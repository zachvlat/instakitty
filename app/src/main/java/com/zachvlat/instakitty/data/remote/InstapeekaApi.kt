package com.zachvlat.instakitty.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface InstapeekaApi {

    @GET("api/insta")
    suspend fun getProfile(@Query("username") username: String): Response<InstaProfileResponse>

    @GET("api/posts")
    suspend fun getPosts(@Query("username") username: String): Response<InstaPostsResponse>

    @GET("api/related")
    suspend fun getRelated(@Query("username") username: String): Response<InstaRelatedResponse>

    @GET("api/highlights")
    suspend fun getHighlights(@Query("username") username: String): Response<InstaHighlightsResponse>

    @GET("api/highlight-items")
    suspend fun getHighlightItems(
        @Query("id") id: String,
        @Query("username") username: String
    ): Response<InstaHighlightItemsResponse>
}
