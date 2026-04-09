package com.baer.hado.data.api

import com.baer.hado.data.model.HaState
import com.baer.hado.data.model.TokenResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

interface HaApiService {

    @GET("api/states")
    suspend fun getStates(): List<HaState>

    @POST("api/services/todo/get_items?return_response")
    suspend fun getTodoItems(@Body body: Map<String, String>): Response<ResponseBody>

    @POST("api/services/todo/add_item")
    suspend fun addTodoItem(@Body body: Map<String, String>): Response<ResponseBody>

    @POST("api/services/todo/update_item")
    suspend fun updateTodoItem(@Body body: Map<String, String>): Response<ResponseBody>

    @POST("api/services/todo/remove_item")
    suspend fun removeTodoItem(@Body body: Map<String, String>): Response<ResponseBody>
}

interface HaAuthService {

    @FormUrlEncoded
    @POST("auth/token")
    suspend fun exchangeCode(
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("client_id") clientId: String
    ): TokenResponse

    @FormUrlEncoded
    @POST("auth/token")
    suspend fun refreshToken(
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String
    ): TokenResponse
}
