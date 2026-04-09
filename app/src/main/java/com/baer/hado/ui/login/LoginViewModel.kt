package com.baer.hado.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baer.hado.data.local.TokenManager
import com.baer.hado.data.repository.AuthRepository
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
    private val tokenManager: TokenManager
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
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isAuthenticated = true
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Authentication failed"
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
        _uiState.value = _uiState.value.copy(isAuthenticated = true)
    }
}
