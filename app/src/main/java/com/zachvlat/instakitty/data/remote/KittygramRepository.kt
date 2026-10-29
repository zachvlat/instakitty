package com.zachvlat.instakitty.data.remote

import com.zachvlat.instakitty.data.local.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

class KittygramRepository(private val dataStore: SettingsDataStore) {

    private var cachedClient: KittygramApi? = null
    private var cachedUrl: String = ""
    private var cachedToken: String = ""

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

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

    suspend fun searchUsers(query: String): ApiResult<List<User>> {
        val client = getClient() ?: return ApiResult.NetworkError("No instance configured")
        return try {
            val res = client.search(query)
            if (res.isSuccessful) {
                val body = res.body()
                if (body != null) {
                    if (body.hasErrors == true) {
                        ApiResult.Error(
                            body.errorType ?: "unknown",
                            body.errorInfo?.message ?: "Unknown error"
                        )
                    } else {
                        val users = body.users.map { su ->
                            User(
                                username = su.username,
                                profilePicture = su.profilePicture,
                                profilePicUrl = su.profilePicture,
                                isVerified = su.isVerified,
                                id = su.id
                            )
                        }.take(5)
                        ApiResult.Success(users)
                    }
                } else {
                    ApiResult.NetworkError("Empty response")
                }
            } else {
                ApiResult.Error("http_${res.code()}", res.message())
            }
        } catch (e: Exception) {
            ApiResult.NetworkError(e.message ?: "Search failed")
        }
    }

    suspend fun testConnection(url: String, token: String = ""): ConnectionResult {
        return try {
            val client = RetrofitClient.create(url, token.ifBlank { null })
            val status = client.getStatus()
            if (!status.isSuccessful) {
                val code = status.code()
                val msg = when (code) {
                    503 -> "Instance is rate-limited (503)"
                    404 -> "Instance not found (404)"
                    418 -> "Instance blocked this request (418)"
                    else -> "HTTP $code"
                }
                return ConnectionResult(false, msg)
            }

            val info = client.getInfo()
            if (!info.isSuccessful && info.code() == 401) {
                return ConnectionResult(
                    false, if (token.isBlank())
                        "Instance requires an API token (401)"
                    else
                        "API token is invalid (401)"
                )
            }

            val s = status.body()
            if (s?.redis == true) {
                ConnectionResult(true, "Connected!")
            } else {
                ConnectionResult(true, "Connected (no cache)")
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

    suspend fun getComments(shortcode: String): ApiResult<CommentsResponse> {
        val client = getClient() ?: return ApiResult.NetworkError("No instance configured")
        return try {
            val res = client.getPostWithComments(shortcode)
            if (res.isSuccessful) {
                val body = res.body()
                if (body != null) {
                    try {
                        val comments = json.decodeFromJsonElement(
                            CommentsResponse.serializer(),
                            body
                        )
                        ApiResult.Success(comments)
                    } catch (e: Exception) {
                        ApiResult.Success(CommentsResponse())
                    }
                } else {
                    ApiResult.Success(CommentsResponse())
                }
            } else {
                ApiResult.Success(CommentsResponse())
            }
        } catch (e: Exception) {
            ApiResult.Success(CommentsResponse())
        }
    }

    suspend fun getPopular(topic: String, cursor: String? = null): ApiResult<ExploreResponse> {
        val client = getClient() ?: return ApiResult.NetworkError("No instance configured")
        return try {
            val res = client.getPopular(topic, cursor)
            if (res.isSuccessful) {
                val body = res.body()
                if (body != null) {
                    if (body.status == "ok") {
                        ApiResult.Success(body)
                    } else {
                        ApiResult.Error("api_error", "API returned status: ${body.status}")
                    }
                } else {
                    ApiResult.NetworkError("Empty response")
                }
            } else {
                ApiResult.Error("http_${res.code()}", res.message())
            }
        } catch (e: Exception) {
            ApiResult.NetworkError(e.message ?: "Explore failed")
        }
    }

    suspend fun getUser(
        username: String,
        cursor: String? = null
    ): ApiResult<UserProfileGridResponse> {
        val client = getClient() ?: return ApiResult.NetworkError("No instance configured")
        return try {
            val res = client.getUser(username, cursor)
            val body = res.body()
            when {
                !res.isSuccessful -> ApiResult.Error("http_${res.code()}", res.message())
                body == null -> ApiResult.NetworkError("Empty response")
                body.hasErrors == true -> ApiResult.Error(
                    body.errorType ?: "unknown",
                    body.errorInfo?.message ?: "Unknown error"
                )
                else -> ApiResult.Success(body)
            }
        } catch (e: Exception) {
            ApiResult.NetworkError(e.message ?: "Network error")
        }
    }

    suspend fun getUserTimeline(username: String, cursor: String? = null): ApiResult<UserTimelineResponse> {
        val timeline = kittygramTimeline(username, cursor)
        if (timeline is ApiResult.Success || cursor != null) return timeline
        // Kittygram's timeline uses its own GraphQL query (PolarisProfilePostsQuery) which
        // Instagram rate-limits separately from the profile grid. When it fails, rebuild the
        // latest posts from the grid, which is what profile pages use and usually still works.
        return gridAsTimeline(username)?.let { ApiResult.Success(it) } ?: timeline
    }

    // Grid items carry no timestamp, so the newest few are resolved through /api/post
    // (a separate, cached query) to get their dates and full media.
    private suspend fun gridAsTimeline(username: String): UserTimelineResponse? {
        val grid = getUser(username) as? ApiResult.Success ?: return null
        val client = getClient() ?: return null
        val posts = grid.data.grid?.items.orEmpty()
            .take(GRID_FALLBACK_POSTS)
            .mapNotNull { item ->
                val shortcode = item.shortcode ?: return@mapNotNull null
                try {
                    val res = client.getPost(shortcode)
                    res.body()?.takeIf { res.isSuccessful && it.hasErrors != true }
                } catch (_: Exception) {
                    null
                }
            }
        if (posts.isEmpty()) return null
        return UserTimelineResponse(info = grid.data.info, timeline = GridData(items = posts))
    }

    private suspend fun kittygramTimeline(
        username: String,
        cursor: String?
    ): ApiResult<UserTimelineResponse> {
        val client = getClient() ?: return ApiResult.NetworkError("No instance configured")
        return try {
            val res = client.getUserTimeline(username, cursor)
            val body = res.body()
            when {
                !res.isSuccessful -> ApiResult.Error("http_${res.code()}", res.message())
                body == null -> ApiResult.NetworkError("Empty response")
                body.hasErrors == true -> ApiResult.Error(
                    body.errorType ?: "unknown",
                    body.errorInfo?.message ?: "Unknown error"
                )
                else -> ApiResult.Success(body)
            }
        } catch (e: Exception) {
            ApiResult.NetworkError(e.message ?: "Network error")
        }
    }

    companion object {
        // Covers up to 3 pinned posts (which sit at the top of the grid) plus recent ones.
        private const val GRID_FALLBACK_POSTS = 6
    }
}
