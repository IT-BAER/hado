package com.baer.hado.data.api

import android.util.Log
import com.baer.hado.data.local.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenManager.accessToken
            ?: return chain.proceed(chain.request())

        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()

        return chain.proceed(request)
    }
}

@Singleton
class TokenRefreshInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        // Proactively refresh token if it expires within 60 seconds
        val expiresIn = tokenManager.tokenExpiry - System.currentTimeMillis()
        if (expiresIn < 60_000 && tokenManager.refreshToken != null) {
            Log.d("HAdo", "Token expires in ${expiresIn / 1000}s, proactively refreshing")
            runBlocking { refreshAccessToken() }
        }

        val request = chain.request().newBuilder()
            .removeHeader("Authorization")
            .addHeader("Authorization", "Bearer ${tokenManager.accessToken}")
            .build()
        val response = chain.proceed(request)

        if (response.code == 401 && tokenManager.refreshToken != null) {
            response.close()

            val refreshed = runBlocking { refreshAccessToken() }
            if (refreshed) {
                val newRequest = chain.request().newBuilder()
                    .removeHeader("Authorization")
                    .addHeader("Authorization", "Bearer ${tokenManager.accessToken}")
                    .build()
                return chain.proceed(newRequest)
            }
        }

        return response
    }

    private suspend fun refreshAccessToken(): Boolean {
        val serverUrl = tokenManager.serverUrl ?: return false
        val refreshToken = tokenManager.refreshToken ?: return false

        return try {
            val retrofit = retrofit2.Retrofit.Builder()
                .baseUrl(serverUrl.trimEnd('/') + "/")
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build()

            val authService = retrofit.create(HaAuthService::class.java)
            val response = authService.refreshToken(
                refreshToken = refreshToken,
                clientId = AUTH_CLIENT_ID
            )

            tokenManager.accessToken = response.accessToken
            tokenManager.refreshToken = response.refreshToken
            tokenManager.tokenExpiry =
                System.currentTimeMillis() + (response.expiresIn * 1000)
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val AUTH_CLIENT_ID = "https://home-assistant.io/android"
        const val AUTH_REDIRECT_URI = "homeassistant://auth-callback"
    }
}
