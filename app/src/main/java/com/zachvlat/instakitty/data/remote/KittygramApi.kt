package com.zachvlat.instakitty.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface KittygramApi {

    @GET("api/status")
    suspend fun getStatus(): Response<ApiStatus>

    @GET("api/info")
    suspend fun getInfo(): Response<ApiInfo>

    @GET("api/post/{shortcode}")
    suspend fun getPost(@Path("shortcode") shortcode: String): Response<Post>

    @GET("api/user/{username}")
    suspend fun getUser(
        @Path("username") username: String,
        @Query("cursor") cursor: String? = null
    ): Response<UserProfileResponse>
}
