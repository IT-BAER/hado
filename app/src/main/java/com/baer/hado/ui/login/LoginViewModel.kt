package com.baer.hado.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baer.hado.data.local.TokenManager
import com.baer.hado.data.repository.AuthRepository
import com.baer.hado.notifications.OverdueNotificationScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val serverUrl: String = "",
    val token: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAuthenticated: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        if (authRepository.isLoggedIn) {
            _uiState.value = _uiState.value.copy(isAuthenticated = true)
        }
    }

    fun onServerUrlChanged(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url, error = null)
    }

    fun onTokenChanged(token: String) {
        _uiState.value = _uiState.value.copy(token = token, error = null)
    }

    fun getAuthorizeUrl(): String? {
        val url = _uiState.value.serverUrl.trim()
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter your Home Assistant URL")
            return null
        }

        val normalizedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else url

        // Validate that the URL has at least a host component before opening a WebView
        val uri = try { android.net.Uri.parse(normalizedUrl) } catch (_: Exception) { null }
        if (uri?.host.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                error = "Invalid URL — please enter a full address like https://homeassistant.local:8123"
            )
            return null
        }

        _uiState.value = _uiState.value.copy(
            serverUrl = normalizedUrl,
            isLoading = true,
            error = null
        )
        return authRepository.buildAuthorizeUrl(normalizedUrl)
    }

    fun handleAuthCallback(code: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = authRepository.exchangeCodeForTokens(
                serverUrl = _uiState.value.serverUrl,
                code = code
            )
            result.fold(
                onSuccess = {
                    OverdueNotificationScheduler.reschedule(appContext)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isAuthenticated = true
                    )
                },
                onFailure = { e ->
                    // Avoid leaking raw exception messages (e.g. hostnames from
                    // UnknownHostException) which produce confusing toasts like "www. error".
                    val friendlyError = when {
                        e is java.net.UnknownHostException ->
                            "Cannot reach Home Assistant — check the URL and your network connection"
                        e is java.net.SocketTimeoutException ->
                            "Connection timed out — is your Home Assistant reachable?"
                        e is javax.net.ssl.SSLException ->
                            "SSL error — check your server's certificate or try http://"
                        e.message?.contains("401") == true ->
                            "Authentication failed — the authorization code was rejected"
                        else -> "Authentication failed — please check your URL and try again"
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = friendlyError
                    )
                }
            )
        }
    }

    fun saveWithToken() {
        val url = _uiState.value.serverUrl.trim()
        val token = _uiState.value.token.trim()

        if (url.isBlank() || token.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "URL and token are required")
            return
        }

        val normalizedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else url

        tokenManager.serverUrl = normalizedUrl.trimEnd('/')
        tokenManager.accessToken = token
        tokenManager.refreshToken = null
        tokenManager.tokenExpiry = System.currentTimeMillis() + (315360000L * 1000)
        OverdueNotificationScheduler.reschedule(appContext)

        _uiState.value = _uiState.value.copy(isAuthenticated = true)
    }

    fun setError(msg: String) {
        _uiState.value = _uiState.value.copy(error = msg, isLoading = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, isLoading = false)
    }

    fun enterDemoMode() {
        tokenManager.isDemoMode = true
        OverdueNotificationScheduler.reschedule(appContext)
        _uiState.value = _uiState.value.copy(isAuthenticated = true)
    }
}
