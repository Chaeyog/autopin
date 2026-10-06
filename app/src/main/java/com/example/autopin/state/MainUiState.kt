package com.example.autopin.state

import android.net.Uri
import com.example.autopin.data.pinterest.PinterestAccount
import com.example.autopin.data.pinterest.PinterestAuthStatus

data class MainUiState(
    val pinterestAccount: PinterestAccount = PinterestAccount(),
    val selectedFolderUri: Uri? = null,
    val selectedFolderDisplayPath: String? = null,
    val errorMessage: String? = null,
    val hasInitializationError: Boolean = false,
    val initializationErrorMessage: String? = null,
    val filesWaiting: Int = 0,
    val filesUploaded: Int = 0,
    val filesFailed: Int = 0,
    val isAutoUploadActive: Boolean = false,
) {
    val isStartUploadEnabled: Boolean
        get() = (pinterestAccount.status == PinterestAuthStatus.CONNECTED) &&
                (selectedFolderUri != null) &&
                (errorMessage == null) &&
                !hasInitializationError
}
