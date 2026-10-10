package com.zachvlat.instakitty.data.remote

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val INSTAPEEKA_BASE_URL = "https://instapeeka.com/"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    fun create(baseUrl: String, token: String? = null): KittygramApi {
        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val client = newHttpClient(token, CommentsHtmlInterceptor(url))
        return newRetrofit(url, client).create(KittygramApi::class.java)
    }

    fun createThirdParty(): InstapeekaApi {
        val client = newHttpClient(null, null)
        return newRetrofit(INSTAPEEKA_BASE_URL, client).create(InstapeekaApi::class.java)
    }

    private fun newHttpClient(
        token: String?,
        extraInterceptor: Interceptor?
    ): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
            if (!token.isNullOrBlank()) {
                request.addHeader("Authorization", "Bearer $token")
            }
            chain.proceed(request.build())
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .apply { extraInterceptor?.let { addInterceptor(it) } }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun newRetrofit(baseUrl: String, client: OkHttpClient): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }
}
