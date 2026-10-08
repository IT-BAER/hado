package com.baer.hado.data.model

import com.google.gson.annotations.SerializedName

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    // HA omits refresh_token in refresh-grant responses
    @SerializedName("refresh_token") val refreshToken: String?,
    @SerializedName("expires_in") val expiresIn: Long,
    @SerializedName("ha_auth_provider") val haAuthProvider: String? = null
)
