package com.example.autopin.data.pinterest

data class PinterestAccount(
    val accountId: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val profileImageUrl: String? = null,
    val status: PinterestAuthStatus = PinterestAuthStatus.DISCONNECTED,
    val errorMessage: String? = null,
)
