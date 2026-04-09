package com.baer.hado.data.repository

import android.net.Uri
import com.baer.hado.data.api.HaAuthService
import com.baer.hado.data.api.TokenRefreshInterceptor.Companion.AUTH_CLIENT_ID
import com.baer.hado.data.api.TokenRefreshInterceptor.Companion.AUTH_REDIRECT_URI
import com.baer.hado.data.local.TokenManager
import com.baer.hado.data.model.TokenResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val tokenManager: TokenManager
) {
    fun buildAuthorizeUrl(serverUrl: String): String {
        val url = Uri.parse(serverUrl.trimEnd('/') + "/auth/authorize")
            .buildUpon()
            .appendQueryParameter("client_id", AUTH_CLIENT_ID)
            .appendQueryParameter("redirect_uri", AUTH_REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .build()
            .toString()
        return url
    }

    suspend fun exchangeCodeForTokens(serverUrl: String, code: String): Result<TokenResponse> {
        return try {
            val baseUrl = serverUrl.trimEnd('/') + "/"
            val authService = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(HaAuthService::class.java)

            val response = authService.exchangeCode(
                code = code,
                clientId = AUTH_CLIENT_ID
            )

            tokenManager.serverUrl = serverUrl.trimEnd('/')
            tokenManager.accessToken = response.accessToken
            tokenManager.refreshToken = response.refreshToken
            tokenManager.tokenExpiry =
                System.currentTimeMillis() + (response.expiresIn * 1000)

            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        tokenManager.clearAll()
    }

    val isLoggedIn: Boolean get() = tokenManager.isLoggedIn
    val serverUrl: String? get() = tokenManager.serverUrl
}
