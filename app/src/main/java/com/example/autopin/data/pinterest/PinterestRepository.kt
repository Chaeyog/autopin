package com.example.autopin.data.pinterest

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

interface PinterestRepository {
    val accountState: StateFlow<PinterestAccount>

    fun connect(context: Context)
    fun handleOAuthRedirect(uri: Uri)
    fun disconnect()
    fun getAccessToken(): String?
}
