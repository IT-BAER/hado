package com.baer.hado.widget

import android.content.Context
import android.util.Log
import com.baer.hado.data.local.TokenManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shared OkHttp client for widget code that handles token refresh on 401.
 */
class WidgetHttpClient(context: Context) {

    private val tokenManager = TokenManager(context)
    private val client = OkHttpClient()
    private val gson = Gson()

    val serverUrl: String? get() = tokenManager.serverUrl
    val accessToken: String? get() = tokenManager.accessToken
    val isLoggedIn: Boolean get() = tokenManager.isLoggedIn

    fun get(path: String): Response? {
        val url = buildUrl(path) ?: return null
        val request = buildRequest(url).get().build()
        return executeWithRefresh(request)
    }

    fun post(path: String, jsonBody: String): Response? {
        val url = buildUrl(path) ?: return null
        val body = jsonBody.toRequestBody("application/json".toMediaType())
        val request = buildRequest(url).post(body).build()
        return executeWithRefresh(request)
    }

    private fun buildUrl(path: String): String? {
        val base = tokenManager.serverUrl ?: return null
        return "${base.trimEnd('/')}/$path"
    }

    private fun buildRequest(url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${tokenManager.accessToken}")
    }

    /**
     * Proactively refresh token if it expires within the next 60 seconds,
     * avoiding 401 responses that trigger HA "invalid authentication" warnings.
     */
    private fun ensureFreshToken() {
        val expiresIn = tokenManager.tokenExpiry - System.currentTimeMillis()
        if (expiresIn < 60_000 && tokenManager.refreshToken != null) {
            Log.d("HAdo", "Token expires in ${expiresIn / 1000}s, proactively refreshing")
            refreshAccessToken()
        }
    }

    private fun executeWithRefresh(request: Request): Response? {
        return try {
            ensureFreshToken()
            // Rebuild request with (possibly refreshed) token
            val currentRequest = request.newBuilder()
                .removeHeader("Authorization")
                .addHeader("Authorization", "Bearer ${tokenManager.accessToken}")
                .build()
            val response = client.newCall(currentRequest).execute()
            if (response.code == 401 && tokenManager.refreshToken != null) {
                Log.w("HAdo", "Got 401 for ${request.url}, attempting token refresh")
                response.close()
                if (refreshAccessToken()) {
                    Log.d("HAdo", "Token refresh succeeded, retrying request")
                    // Retry with new token
                    val retryRequest = request.newBuilder()
                        .removeHeader("Authorization")
                        .addHeader("Authorization", "Bearer ${tokenManager.accessToken}")
                        .build()
                    client.newCall(retryRequest).execute()
                } else {
                    Log.w("HAdo", "Token refresh failed, returning null")
                    null
                }
            } else {
                response
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun refreshAccessToken(): Boolean {
        val serverUrl = tokenManager.serverUrl ?: return false
        val refreshToken = tokenManager.refreshToken ?: return false

        return try {
            val formBody = okhttp3.FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", "https://home-assistant.io/android")
                .build()

            val request = Request.Builder()
                .url("${serverUrl.trimEnd('/')}/auth/token")
                .post(formBody)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.w("HAdo", "Token refresh failed: ${response.code} — $errorBody")
                response.close()
                return false
            }

            val body = response.body?.string() ?: return false
            response.close()

            val tokenResponse = gson.fromJson(body, RefreshTokenResponse::class.java)
            tokenManager.accessToken = tokenResponse.accessToken
            if (tokenResponse.refreshToken != null) {
                tokenManager.refreshToken = tokenResponse.refreshToken
            }
            tokenManager.tokenExpiry =
                System.currentTimeMillis() + (tokenResponse.expiresIn * 1000)
            true
        } catch (_: Exception) {
            false
        }
    }

    private data class RefreshTokenResponse(
        @SerializedName("access_token") val accessToken: String,
        @SerializedName("refresh_token") val refreshToken: String?,
        @SerializedName("expires_in") val expiresIn: Long
    )

    /**
     * Move a todo item via HA WebSocket API (todo/item/move).
     * Blocks the calling thread until complete or timeout.
     * @param entityId The todo list entity (e.g. "todo.shopping_list")
     * @param uid The UID of the item to move
     * @param previousUid The UID of the item to place after, or null for first position
     * @return true if the move succeeded
     */
    fun moveTodoItem(entityId: String, uid: String, previousUid: String?): Boolean {
        val base = tokenManager.serverUrl ?: return false
        val token = tokenManager.accessToken ?: return false
        val wsUrl = base.trimEnd('/')
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") + "/api/websocket"

        val msgId = AtomicInteger(1)
        val success = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        val request = Request.Builder().url(wsUrl).build()
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = gson.fromJson(text, Map::class.java)
                    when (msg["type"]) {
                        "auth_required" -> {
                            val authMsg = gson.toJson(mapOf(
                                "type" to "auth",
                                "access_token" to token
                            ))
                            webSocket.send(authMsg)
                        }
                        "auth_ok" -> {
                            val id = msgId.getAndIncrement()
                            val moveMsg = mutableMapOf<String, Any>(
                                "id" to id,
                                "type" to "todo/item/move",
                                "entity_id" to entityId,
                                "uid" to uid
                            )
                            if (previousUid != null) {
                                moveMsg["previous_uid"] = previousUid
                            }
                            webSocket.send(gson.toJson(moveMsg))
                        }
                        "auth_invalid" -> {
                            Log.e("HAdo", "WebSocket auth failed: ${msg["message"]}")
                            webSocket.close(1000, null)
                            latch.countDown()
                        }
                        "result" -> {
                            val ok = msg["success"] as? Boolean ?: false
                            success.set(ok)
                            if (!ok) {
                                Log.e("HAdo", "moveTodoItem failed: $text")
                            }
                            webSocket.close(1000, null)
                            latch.countDown()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("HAdo", "WebSocket message error", e)
                    webSocket.close(1000, null)
                    latch.countDown()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("HAdo", "WebSocket connection failed", t)
                latch.countDown()
            }
        })

        latch.await(10, TimeUnit.SECONDS)
        return success.get()
    }
}
