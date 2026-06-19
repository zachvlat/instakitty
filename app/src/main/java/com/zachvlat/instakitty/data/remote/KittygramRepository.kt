package com.zachvlat.instakitty.data.remote

import com.zachvlat.instakitty.data.local.SettingsDataStore
import kotlinx.coroutines.flow.first

class KittygramRepository(private val dataStore: SettingsDataStore) {

    private var cachedClient: KittygramApi? = null
    private var cachedUrl: String = ""
    private var cachedToken: String = ""

    private suspend fun getClient(): KittygramApi? {
        val url = dataStore.instanceUrl.first()
        val token = dataStore.apiToken.first()
        if (url.isBlank()) return null
        if (cachedUrl != url || cachedToken != token || cachedClient == null) {
            cachedUrl = url
            cachedToken = token
            cachedClient = RetrofitClient.create(url, token.ifBlank { null })
        }
        return cachedClient
    }

    suspend fun testConnection(url: String, token: String = ""): ConnectionResult {
        return try {
            val client = RetrofitClient.create(url, token.ifBlank { null })
            val status = client.getStatus()
            if (status.isSuccessful) {
                val s = status.body()
                if (s?.redis == true) {
                    ConnectionResult(true, "Connected!")
                } else {
                    ConnectionResult(true, "Connected (no cache)")
                }
            } else {
                val code = status.code()
                val msg = when (code) {
                    401 -> "Instance requires an API token (401)"
                    503 -> "Instance is rate-limited (503)"
                    404 -> "Instance not found (404)"
                    418 -> "Instance blocked this request (418)"
                    else -> "HTTP $code"
                }
                ConnectionResult(false, msg)
            }
        } catch (e: Exception) {
            ConnectionResult(false, e.message ?: "Connection failed")
        }
    }

    suspend fun getPost(shortcode: String): ApiResult<Post> {
        val client = getClient() ?: return ApiResult.NetworkError("No instance configured")
        return try {
            val res = client.getPost(shortcode)
            if (res.isSuccessful) {
                val body = res.body()
                if (body != null) {
                    if (body.hasErrors == true) {
                        ApiResult.Error(
                            body.errorType ?: "unknown",
                            body.errorInfo?.message ?: "Unknown error"
                        )
                    } else {
                        ApiResult.Success(body)
                    }
                } else {
                    ApiResult.NetworkError("Empty response")
                }
            } else {
                ApiResult.Error("http_${res.code()}", res.message())
            }
        } catch (e: Exception) {
            ApiResult.NetworkError(e.message ?: "Network error")
        }
    }

    suspend fun getUser(username: String, cursor: String? = null): ApiResult<UserProfileResponse> {
        val client = getClient() ?: return ApiResult.NetworkError("No instance configured")
        return try {
            val res = client.getUser(username, cursor)
            if (res.isSuccessful) {
                val body = res.body()
                if (body != null) {
                    if (body.hasErrors == true) {
                        ApiResult.Error(
                            body.errorType ?: "unknown",
                            body.errorInfo?.message ?: "Unknown error"
                        )
                    } else {
                        ApiResult.Success(body)
                    }
                } else {
                    ApiResult.NetworkError("Empty response")
                }
            } else {
                ApiResult.Error("http_${res.code()}", res.message())
            }
        } catch (e: Exception) {
            ApiResult.NetworkError(e.message ?: "Network error")
        }
    }
}
