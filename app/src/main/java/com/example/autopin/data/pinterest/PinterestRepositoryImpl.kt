package com.example.autopin.data.pinterest

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class PinterestRepositoryImpl(
    context: Context,
    private val tokenStorage: PinterestTokenStorage = PinterestTokenStorage(context),
) : PinterestRepository {

    private val _accountState = MutableStateFlow(
        tokenStorage.getSavedAccount() ?: PinterestAccount(status = PinterestAuthStatus.DISCONNECTED)
    )
    override val accountState: StateFlow<PinterestAccount> = _accountState.asStateFlow()

    private var activeAuthState: String? = null

    override fun connect(context: Context) {
        _accountState.update {
            it.copy(
                status = PinterestAuthStatus.CONNECTING,
                errorMessage = null
            )
        }

        if (PinterestOAuthConfig.CLIENT_ID.isBlank()) {
            _accountState.update {
                it.copy(
                    status = PinterestAuthStatus.ERROR,
                    errorMessage = "Pinterest Developer Portal configuration required (Client ID and Token Exchange Backend)."
                )
            }
            return
        }

        try {
            val csrfState = UUID.randomUUID().toString()
            activeAuthState = csrfState

            val authUri = Uri.parse(PinterestOAuthConfig.AUTH_URL).buildUpon()
                .appendQueryParameter("client_id", PinterestOAuthConfig.CLIENT_ID)
                .appendQueryParameter("redirect_uri", PinterestOAuthConfig.REDIRECT_URI)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", PinterestOAuthConfig.SCOPE)
                .appendQueryParameter("state", csrfState)
                .build()

            val intent = Intent(Intent.ACTION_VIEW, authUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("PinterestRepository", "Error launching authorization URL", e)
            _accountState.update {
                it.copy(
                    status = PinterestAuthStatus.ERROR,
                    errorMessage = "Unable to launch browser for authorization: ${e.localizedMessage}"
                )
            }
        }
    }

    override fun handleOAuthRedirect(uri: Uri) {
        val scheme = uri.scheme
        val host = uri.host
        val path = uri.path

        if (scheme != "autopin" || host != "oauth" || path != "/pinterest") {
            Log.w("PinterestRepository", "Ignored invalid OAuth redirect URI: $uri")
            return
        }

        val error = uri.getQueryParameter("error")
        val errorReason = uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error_reason")

        if (!error.isNullOrBlank()) {
            Log.i("PinterestRepository", "OAuth authorization cancelled or failed: $error - $errorReason")
            _accountState.update {
                it.copy(
                    status = PinterestAuthStatus.ERROR,
                    errorMessage = errorReason ?: "Authorization cancelled or failed ($error)."
                )
            }
            return
        }

        val code = uri.getQueryParameter("code")
        val returnedState = uri.getQueryParameter("state")

        if (code.isNullOrBlank()) {
            _accountState.update {
                it.copy(
                    status = PinterestAuthStatus.ERROR,
                    errorMessage = "Authorization code was not returned by Pinterest."
                )
            }
            return
        }

        if (activeAuthState != null && returnedState != activeAuthState) {
            _accountState.update {
                it.copy(
                    status = PinterestAuthStatus.ERROR,
                    errorMessage = "Security verification failed (state mismatch). Please try again."
                )
            }
            return
        }

        Log.i("PinterestRepository", "Received authorization code successfully")

        if (PinterestOAuthConfig.TOKEN_EXCHANGE_BACKEND_URL.isBlank()) {
            _accountState.update {
                it.copy(
                    status = PinterestAuthStatus.ERROR,
                    errorMessage = "Authorization code received, but token exchange backend URL is not configured."
                )
            }
        }
    }

    override fun disconnect() {
        tokenStorage.clearAll()
        activeAuthState = null
        _accountState.update {
            PinterestAccount(status = PinterestAuthStatus.DISCONNECTED)
        }
    }

    override fun getAccessToken(): String? {
        return tokenStorage.getAccessToken()
    }
}
