package com.example.autopin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.autopin.state.MainUiState
import com.example.autopin.ui.components.AutoPinTopAppBar
import com.example.autopin.ui.components.BottomControls
import com.example.autopin.ui.components.PinterestAccountCard
import com.example.autopin.ui.components.UploadQueueSection
import com.example.autopin.ui.components.WatchedFolderCard
import com.example.autopin.ui.theme.AutoPinTheme
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenFolderPicker: () -> Unit,
    onOpenQueueDetails: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    HomeScreenContent(
        uiState = uiState,
        onPinterestConnectClick = { viewModel.connectPinterest(context) },
        onPinterestDisconnectClick = viewModel::disconnectPinterest,
        onSelectFolderClick = onOpenFolderPicker,
        onQueueClick = onOpenQueueDetails,
        onToggleAutoUpload = viewModel::onToggleAutoUpload,
        onErrorMessageShown = viewModel::onErrorMessageShown,
        onRetryInitialization = viewModel::initializeViewModel,
        modifier = modifier,
    )
}

@Composable
fun HomeScreenContent(
    uiState: MainUiState,
    onPinterestConnectClick: () -> Unit,
    onPinterestDisconnectClick: () -> Unit,
    onSelectFolderClick: () -> Unit,
    onQueueClick: () -> Unit,
    onToggleAutoUpload: () -> Unit,
    onErrorMessageShown: () -> Unit,
    onRetryInitialization: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showSettingsDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onErrorMessageShown()
        }
    }

    Scaffold(
        topBar = {
            AutoPinTopAppBar(
                onSettingsClick = {
                    showSettingsDialog = true
                }
            )
        },
        bottomBar = {
            BottomControls(
                isEnabled = uiState.isStartUploadEnabled,
                isActive = uiState.isAutoUploadActive,
                onToggleAutoUpload = {
                    val nextState = !uiState.isAutoUploadActive
                    onToggleAutoUpload()
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            if (nextState) "Auto upload started" else "Auto upload stopped"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            if (uiState.hasInitializationError) {
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Initialization Error",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.initializationErrorMessage ?: "Something went wrong while loading AutoPin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onRetryInitialization) {
                            Text("Retry")
                        }
                    }
                }
            }

            // 1. Pinterest Account Section
            PinterestAccountCard(
                account = uiState.pinterestAccount,
                onConnectClick = onPinterestConnectClick,
                onDisconnectClick = onPinterestDisconnectClick,
            )

            // 2. Watched Folder Section
            WatchedFolderCard(
                selectedFolder = uiState.selectedFolderDisplayPath,
                onSelectFolderClick = onSelectFolderClick,
            )

            // 3. Upload Queue Section
            UploadQueueSection(
                filesWaiting = uiState.filesWaiting,
                filesUploaded = uiState.filesUploaded,
                filesFailed = uiState.filesFailed,
                onQueueClick = onQueueClick,
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Settings") },
            text = { Text("AutoPin settings and configuration options will appear here.") },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    AutoPinTheme {
        HomeScreenContent(
            uiState = MainUiState(
                selectedFolderDisplayPath = null,
            ),
            onPinterestConnectClick = {},
            onPinterestDisconnectClick = {},
            onSelectFolderClick = {},
            onQueueClick = {},
            onToggleAutoUpload = {},
            onErrorMessageShown = {},
        )
    }
}
