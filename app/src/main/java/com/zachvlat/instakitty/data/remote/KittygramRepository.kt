package com.zachvlat.instakitty.data.remote

import com.zachvlat.instakitty.data.local.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

class KittygramRepository(private val dataStore: SettingsDataStore) {

    private var cachedClient: KittygramApi? = null
    private var cachedUrl: String = ""
    private var cachedToken: String = ""

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val thirdPartyApi: InstapeekaApi by lazy { RetrofitClient.createThirdParty() }

    private suspend fun thirdPartyEnabled(): Boolean = dataStore.useThirdPartyApis.first()

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

    // Profile pages always come from Kittygram first so pagination, counts and bio are
    // complete. instapeeka only serves the first page (no cursor), so it is used as a
    // best-effort fallback when the configured instance is missing or fails.
    suspend fun getUser(username: String, cursor: String? = null): ApiResult<UserProfileGridResponse> {
        val result = kittygramUser(username, cursor)
        if (result is ApiResult.Success) return result
        if (cursor == null && thirdPartyEnabled()) {
            getThirdPartyProfile(username)?.let { return ApiResult.Success(it) }
        }
        return result
    }

    private suspend fun kittygramUser(
        username: String,
        cursor: String?
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

    // The heavy per-user "today" feed fetch offloads to instapeeka when enabled, but
    // still works against Kittygram (with or without the third-party API).
    suspend fun getUserTimeline(username: String, cursor: String? = null): ApiResult<UserTimelineResponse> {
        if (thirdPartyEnabled()) {
            getThirdPartyTimeline(username)?.let { return ApiResult.Success(it) }
        }
        return kittygramTimeline(username, cursor)
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

    suspend fun getProfileAvatar(username: String): String? {
        if (!thirdPartyEnabled()) return null
        return try {
            val res = thirdPartyApi.getProfile(username)
            if (res.isSuccessful) res.body()?.profile?.profilePictureUrl else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getRelatedProfiles(username: String): ApiResult<List<User>> {
        if (!thirdPartyEnabled()) return ApiResult.Success(emptyList())
        return try {
            val res = thirdPartyApi.getRelated(username)
            if (res.isSuccessful) {
                ApiResult.Success(res.body()?.profiles.orEmpty().map { it.toUser() })
            } else {
                ApiResult.Error("http_${res.code()}", res.message())
            }
        } catch (e: Exception) {
            ApiResult.NetworkError(e.message ?: "Related profiles failed")
        }
    }

    suspend fun getHighlights(username: String): ApiResult<List<InstaHighlight>> {
        if (!thirdPartyEnabled()) return ApiResult.Success(emptyList())
        return try {
            val res = thirdPartyApi.getHighlights(username)
            if (res.isSuccessful) {
                ApiResult.Success(res.body()?.highlights.orEmpty())
            } else {
                ApiResult.Error("http_${res.code()}", res.message())
            }
        } catch (e: Exception) {
            ApiResult.NetworkError(e.message ?: "Highlights failed")
        }
    }

    suspend fun getStories(username: String): ApiResult<List<InstaStory>> {
        if (!thirdPartyEnabled()) return ApiResult.Success(emptyList())
        return try {
            val res = thirdPartyApi.getProfile(username)
            if (res.isSuccessful) {
                ApiResult.Success(res.body()?.stories.orEmpty())
            } else {
                ApiResult.Error("http_${res.code()}", res.message())
            }
        } catch (e: Exception) {
            ApiResult.NetworkError(e.message ?: "Stories failed")
        }
    }

    suspend fun getHighlightItems(highlightId: String, username: String): ApiResult<List<InstaStory>> {
        if (!thirdPartyEnabled()) return ApiResult.Success(emptyList())
        return try {
            val res = thirdPartyApi.getHighlightItems(highlightId, username)
            if (res.isSuccessful) {
                ApiResult.Success(res.body()?.stories.orEmpty())
            } else {
                ApiResult.Error("http_${res.code()}", res.message())
            }
        } catch (e: Exception) {
            ApiResult.NetworkError(e.message ?: "Highlight items failed")
        }
    }

    // instapeeka's profile has no counts/bio; this is only a fallback for page one.
    private suspend fun getThirdPartyProfile(username: String): UserProfileGridResponse? {
        return try {
            val profileRes = thirdPartyApi.getProfile(username)
            if (!profileRes.isSuccessful) return null
            val profile = profileRes.body()?.profile ?: return null
            val postsRes = thirdPartyApi.getPosts(username)
            if (!postsRes.isSuccessful) return null
            UserProfileGridResponse(
                info = profile.toUser(),
                grid = GridData(items = postsRes.body()?.posts.orEmpty().map { it.toPost() })
            )
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun getThirdPartyTimeline(username: String): UserTimelineResponse? {
        return try {
            val res = thirdPartyApi.getPosts(username)
            if (!res.isSuccessful) return null
            UserTimelineResponse(
                timeline = GridData(items = res.body()?.posts.orEmpty().map { it.toPost() })
            )
        } catch (_: Exception) {
            null
        }
    }
}
