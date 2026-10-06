package com.example.autopin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.autopin.ui.FolderPickerScreen
import com.example.autopin.ui.HomeScreen
import com.example.autopin.ui.MainViewModel
import com.example.autopin.ui.UploadQueueDetailsScreen
import com.example.autopin.ui.theme.AutoPinTheme

enum class Screen {
    Main,
    FolderPicker,
    QueueDetails
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoPinTheme {
                AutoPinApp()
            }
        }
    }
}

@Composable
fun AutoPinApp(
    viewModel: MainViewModel = viewModel()
) {
    var currentScreen by remember { mutableStateOf(Screen.Main) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Detect when app returns to foreground and scan folder
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.scanCurrentFolder()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    when (currentScreen) {
        Screen.Main -> {
            HomeScreen(
                viewModel = viewModel,
                onOpenFolderPicker = {
                    currentScreen = Screen.FolderPicker
                },
                onOpenQueueDetails = {
                    currentScreen = Screen.QueueDetails
                }
            )
        }
        Screen.FolderPicker -> {
            FolderPickerScreen(
                onNavigateBack = {
                    currentScreen = Screen.Main
                },
                onFolderSelected = { uri ->
                    viewModel.onFolderSelected(uri)
                    currentScreen = Screen.Main
                }
            )
        }
        Screen.QueueDetails -> {
            UploadQueueDetailsScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    currentScreen = Screen.Main
                }
            )
        }
    }
}
