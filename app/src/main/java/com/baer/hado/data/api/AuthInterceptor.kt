package com.baer.hado.data.api

import android.util.Log
import com.baer.hado.data.local.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends each request to the active account's server with that account's token.
 * The account is read once per request so URL, token and refresh always belong together.
 */
@Singleton
class TokenRefreshInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val account = tokenManager.activeAccount
        val baseUrl = account.serverUrl?.trimEnd('/')?.plus("/")?.toHttpUrlOrNull()
            ?: return chain.proceed(chain.request())

        // Proactively refresh token if it expires within 60 seconds
        val expiresIn = account.tokenExpiry - System.currentTimeMillis()
        if (expiresIn < 60_000 && account.refreshToken != null) {
            Log.d("HAdo", "Token expires in ${expiresIn / 1000}s, proactively refreshing")
            runBlocking { refreshAccessToken(account) }
        }

        val response = chain.proceed(authorized(chain.request(), baseUrl, account))

        if (response.code == 401 && account.refreshToken != null) {
            response.close()

            val refreshed = runBlocking { refreshAccessToken(account) }
            if (refreshed) {
                return chain.proceed(authorized(chain.request(), baseUrl, account))
            }
        }

        return response
    }

    private fun authorized(request: Request, baseUrl: HttpUrl, account: TokenManager.Account): Request {
        // Retrofit uses a placeholder base URL; swap in the account's server, keeping any path prefix.
        val url = baseUrl.newBuilder()
            .addEncodedPathSegments(request.url.encodedPath.removePrefix("/"))
            .encodedQuery(request.url.encodedQuery)
            .build()
        return request.newBuilder()
            .url(url)
            .removeHeader("Authorization")
            .addHeader("Authorization", "Bearer ${account.accessToken}")
            .build()
    }

    private suspend fun refreshAccessToken(account: TokenManager.Account): Boolean {
        val serverUrl = account.serverUrl ?: return false
        val refreshToken = account.refreshToken ?: return false

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

            account.setTokens(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken ?: refreshToken,
                expiresAtMillis = System.currentTimeMillis() + (response.expiresIn * 1000)
            )
            Log.d("HAdo", "Token refreshed successfully, expires in ${response.expiresIn}s")
            true
        } catch (e: retrofit2.HttpException) {
            Log.e("HAdo", "Token refresh failed HTTP ${e.code()}: ${e.response()?.errorBody()?.string()}")
            false
        } catch (e: Exception) {
            Log.e("HAdo", "Token refresh failed: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    companion object {
        const val AUTH_CLIENT_ID = "https://home-assistant.io/android"
        const val AUTH_REDIRECT_URI = "homeassistant://auth-callback"
        /** Retrofit needs a base URL at build time; every request is rewritten to the active server. */
        const val PLACEHOLDER_BASE_URL = "http://localhost/"
    }
}
