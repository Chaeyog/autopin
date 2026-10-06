package com.example.autopin.data.pinterest

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class PinterestTokenStorage(context: Context) {

    private companion object {
        private const val PREFS_NAME = "pinterest_secure_token_prefs"
        private const val KEY_ACCESS_TOKEN = "key_access_token"
        private const val KEY_REFRESH_TOKEN = "key_refresh_token"
        private const val KEY_EXPIRES_AT = "key_expires_at"
        private const val KEY_ACCOUNT_ID = "key_account_id"
        private const val KEY_USERNAME = "key_username"
        private const val KEY_DISPLAY_NAME = "key_display_name"
        private const val KEY_PROFILE_IMAGE_URL = "key_profile_image_url"
    }

    @Suppress("DEPRECATION")
    private val sharedPreferences = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.valueOf("AES256_SKEY"),
            EncryptedSharedPreferences.PrefValueEncryptionScheme.valueOf("AES256_GCM")
        )
    } catch (e: Exception) {
        Log.e("PinterestTokenStorage", "Error creating EncryptedSharedPreferences, falling back to private prefs", e)
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveTokens(accessToken: String, refreshToken: String?, expiresInSeconds: Long?) {
        val expiresAt = if (expiresInSeconds != null && expiresInSeconds > 0) {
            System.currentTimeMillis() + (expiresInSeconds * 1000)
        } else {
            0L
        }

        sharedPreferences.edit {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            putLong(KEY_EXPIRES_AT, expiresAt)
        }
    }

    fun getAccessToken(): String? {
        val token = sharedPreferences.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val expiresAt = sharedPreferences.getLong(KEY_EXPIRES_AT, 0L)
        if (expiresAt in 1..System.currentTimeMillis()) {
            return null
        }
        return token
    }

    fun getRefreshToken(): String? {
        return sharedPreferences.getString(KEY_REFRESH_TOKEN, null)
    }

    fun saveAccountMetadata(
        accountId: String?,
        username: String?,
        displayName: String?,
        profileImageUrl: String?
    ) {
        sharedPreferences.edit {
            putString(KEY_ACCOUNT_ID, accountId)
            putString(KEY_USERNAME, username)
            putString(KEY_DISPLAY_NAME, displayName)
            putString(KEY_PROFILE_IMAGE_URL, profileImageUrl)
        }
    }

    fun getSavedAccount(): PinterestAccount? {
        val token = getAccessToken() ?: return null
        if (token.isBlank()) return null

        val accountId = sharedPreferences.getString(KEY_ACCOUNT_ID, null)
        val username = sharedPreferences.getString(KEY_USERNAME, null)
        val displayName = sharedPreferences.getString(KEY_DISPLAY_NAME, null)
        val profileImageUrl = sharedPreferences.getString(KEY_PROFILE_IMAGE_URL, null)

        return PinterestAccount(
            accountId = accountId,
            username = username,
            displayName = displayName ?: username ?: "Pinterest User",
            profileImageUrl = profileImageUrl,
            status = PinterestAuthStatus.CONNECTED,
            errorMessage = null
        )
    }

    fun clearAll() {
        sharedPreferences.edit { clear() }
    }
}
