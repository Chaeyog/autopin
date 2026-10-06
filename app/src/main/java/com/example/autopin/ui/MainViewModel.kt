package com.example.autopin.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.autopin.data.FolderRepository
import com.example.autopin.data.FolderValidationResult
import com.example.autopin.data.UploadQueueRepository
import com.example.autopin.data.db.UploadQueueItemEntity
import com.example.autopin.data.pinterest.PinterestRepositoryProvider
import com.example.autopin.state.MainUiState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val folderRepository = FolderRepository()
    private val uploadQueueRepository by lazy { UploadQueueRepository(application) }
    private val pinterestRepository by lazy { PinterestRepositoryProvider.getInstance(application) }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Unhandled coroutine exception caught safely", throwable)
        _uiState.update { currentState ->
            currentState.copy(
                hasInitializationError = true,
                initializationErrorMessage = "Something went wrong while loading AutoPin."
            )
        }
    }

    val queueItems: StateFlow<List<UploadQueueItemEntity>> by lazy {
        uploadQueueRepository.allQueueItemsFlow
            .catch { e ->
                Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Error in queueItems flow", e)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    init {
        initializeViewModel()
    }

    fun initializeViewModel() {
        Log.i("AutoPinTrace", "[AUTOPIN_TRACE] MainViewModel initializeViewModel, repoDbHash=${uploadQueueRepository.dbHash}")
        _uiState.update { it.copy(hasInitializationError = false, initializationErrorMessage = null) }
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            try {
                uploadQueueRepository.cleanupDuplicates()
            } catch (e: Exception) {
                Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Error cleaning up duplicates on startup", e)
            }
        }
        loadSavedFolder()
        observeQueueCounts()
        observePinterestAccount()
    }

    private fun observePinterestAccount() {
        viewModelScope.launch(exceptionHandler) {
            pinterestRepository.accountState.collect { account ->
                _uiState.update { it.copy(pinterestAccount = account) }
            }
        }
    }

    private fun observeQueueCounts() {
        viewModelScope.launch(exceptionHandler) {
            launch {
                uploadQueueRepository.waitingCountFlow
                    .catch { e -> Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Error collecting waiting count", e) }
                    .collect { count ->
                        Log.i("AutoPinTrace", "[AUTOPIN_TRACE] ROOM_FLOW_EMISSION waitingCount = $count")
                        Log.i("AutoPinTrace", "[AUTOPIN_TRACE] VIEWMODEL_STATE filesWaiting = $count")
                        _uiState.update { it.copy(filesWaiting = count) }
                    }
            }
            launch {
                uploadQueueRepository.uploadedCountFlow
                    .catch { e -> Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Error collecting uploaded count", e) }
                    .collect { count ->
                        _uiState.update { it.copy(filesUploaded = count) }
                    }
            }
            launch {
                uploadQueueRepository.failedCountFlow
                    .catch { e -> Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Error collecting failed count", e) }
                    .collect { count ->
                        _uiState.update { it.copy(filesFailed = count) }
                    }
            }
        }
    }

    fun connectPinterest(context: Context) {
        pinterestRepository.connect(context)
    }

    fun disconnectPinterest() {
        pinterestRepository.disconnect()
    }

    fun loadSavedFolder() {
        try {
            when (val result = folderRepository.getSavedFolder(getApplication())) {
                is FolderValidationResult.Valid -> {
                    _uiState.update { currentState ->
                        currentState.copy(
                            selectedFolderUri = result.uri,
                            selectedFolderDisplayPath = result.displayPath,
                            errorMessage = null,
                        )
                    }
                    scanCurrentFolder()
                }
                is FolderValidationResult.Invalid -> {
                    _uiState.update { currentState ->
                        currentState.copy(
                            selectedFolderUri = null,
                            selectedFolderDisplayPath = null,
                            isAutoUploadActive = false,
                            errorMessage = result.message
                        )
                    }
                }
                FolderValidationResult.None -> {
                    _uiState.update { currentState ->
                        currentState.copy(
                            selectedFolderUri = null,
                            selectedFolderDisplayPath = null
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Error in loadSavedFolder", e)
            _uiState.update { currentState ->
                currentState.copy(
                    selectedFolderUri = null,
                    selectedFolderDisplayPath = null,
                    errorMessage = "Folder access is no longer available. Please select the folder again."
                )
            }
        }
    }

    fun onFolderSelected(uri: Uri) {
        try {
            when (val result = folderRepository.saveFolderUri(getApplication(), uri)) {
                is FolderValidationResult.Valid -> {
                    _uiState.update { currentState ->
                        currentState.copy(
                            selectedFolderUri = result.uri,
                            selectedFolderDisplayPath = result.displayPath,
                            errorMessage = null,
                        )
                    }
                    scanFolder(result.uri)
                }
                is FolderValidationResult.Invalid -> {
                    _uiState.update { currentState ->
                        currentState.copy(
                            selectedFolderUri = null,
                            selectedFolderDisplayPath = null,
                            isAutoUploadActive = false,
                            errorMessage = result.message
                        )
                    }
                }
                FolderValidationResult.None -> {}
            }
        } catch (e: Exception) {
            Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Error in onFolderSelected", e)
            _uiState.update {
                it.copy(
                    selectedFolderUri = null,
                    selectedFolderDisplayPath = null,
                    errorMessage = "Failed to select folder. Please try again."
                )
            }
        }
    }

    fun scanCurrentFolder() {
        val folderUri = _uiState.value.selectedFolderUri ?: return
        scanFolder(folderUri)
    }

    fun scanFolder(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            _isScanning.value = true
            try {
                uploadQueueRepository.scanFolder(getApplication(), uri)
            } catch (e: Exception) {
                Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Error scanning folder: $uri", e)
                _uiState.update { it.copy(errorMessage = "Failed to scan folder: ${e.localizedMessage}") }
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun retryFailed() {
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            uploadQueueRepository.retryFailed()
        }
    }

    fun clearUploaded() {
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            uploadQueueRepository.clearUploaded()
        }
    }

    fun deleteQueueItem(id: Long) {
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            uploadQueueRepository.deleteItem(id)
        }
    }

    fun onClearFolder() {
        try {
            folderRepository.clearFolder(getApplication())
            _uiState.update { currentState ->
                currentState.copy(
                    selectedFolderUri = null,
                    selectedFolderDisplayPath = null,
                    isAutoUploadActive = false
                )
            }
        } catch (e: Exception) {
            Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Error in onClearFolder", e)
        }
    }

    fun onToggleAutoUpload() {
        _uiState.update { currentState ->
            if (currentState.isStartUploadEnabled) {
                currentState.copy(isAutoUploadActive = !currentState.isAutoUploadActive)
            } else {
                currentState
            }
        }
    }

    fun onErrorMessageShown() {
        _uiState.update { currentState ->
            currentState.copy(errorMessage = null)
        }
    }
}
