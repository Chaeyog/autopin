package com.example.autopin.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.autopin.data.MediaFolder
import com.example.autopin.data.MediaFolderRepository
import com.example.autopin.data.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FolderPickerUiState(
    val folders: List<MediaFolder> = emptyList(),
    val selectedFolder: MediaFolder? = null,
    val folderItems: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val hasPermission: Boolean = true,
)

class FolderPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val mediaFolderRepository = MediaFolderRepository()

    private val _uiState = MutableStateFlow(FolderPickerUiState())
    val uiState: StateFlow<FolderPickerUiState> = _uiState.asStateFlow()

    fun updatePermissionState(hasPermission: Boolean) {
        _uiState.update { it.copy(hasPermission = hasPermission) }
        if (hasPermission) {
            loadFolders()
        }
    }

    fun loadFolders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val folders = mediaFolderRepository.getMediaFolders(getApplication())
            _uiState.update {
                it.copy(
                    folders = folders,
                    isLoading = false
                )
            }
        }
    }

    fun onFolderClicked(folder: MediaFolder) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedFolder = folder,
                    isLoading = true
                )
            }
            val items = mediaFolderRepository.getMediaItemsInFolder(getApplication(), folder.id)
            _uiState.update {
                it.copy(
                    folderItems = items,
                    isLoading = false
                )
            }
        }
    }

    fun onBackClicked(): Boolean {
        return if (_uiState.value.selectedFolder != null) {
            _uiState.update {
                it.copy(
                    selectedFolder = null,
                    folderItems = emptyList()
                )
            }
            true
        } else {
            false
        }
    }
}
