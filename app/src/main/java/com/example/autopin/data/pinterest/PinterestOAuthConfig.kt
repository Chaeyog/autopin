package com.example.autopin.data.pinterest

object PinterestOAuthConfig {
    const val AUTH_URL = "https://www.pinterest.com/oauth/"
    const val REDIRECT_URI = "autopin://oauth/pinterest"
    const val SCOPE = "boards:read,pins:read,pins:write"

    // Configuration placeholder for Client ID (configured in Pinterest Developer Portal)
    const val CLIENT_ID = ""

    // Token exchange should occur through a secure token-exchange backend
    const val TOKEN_EXCHANGE_BACKEND_URL = ""
}
