package com.zachvlat.instakitty.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        private val INSTANCE_URL = stringPreferencesKey("instance_url")
        private val API_TOKEN = stringPreferencesKey("api_token")
        private val FOLLOWED_USERS = stringPreferencesKey("followed_users")
        private val PROFILE_PICS = stringPreferencesKey("profile_pics")
        private val FAVORITE_QUERIES = stringPreferencesKey("favorite_queries")
        private val SAVED_POSTS = stringPreferencesKey("saved_posts")
    }

    val instanceUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[INSTANCE_URL] ?: ""
    }

    val apiToken: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[API_TOKEN] ?: ""
    }

    val isConfigured: Flow<Boolean> = context.dataStore.data.map { prefs ->
        !prefs[INSTANCE_URL].isNullOrBlank()
    }

    val followedUsers: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[FOLLOWED_USERS] ?: return@map emptySet()
        try {
            Json.decodeFromString<Set<String>>(raw)
        } catch (_: Exception) {
            emptySet()
        }
    }

    val profilePics: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[PROFILE_PICS] ?: return@map emptyMap()
        try {
            Json.decodeFromString<Map<String, String>>(raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    val favoriteQueries: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[FAVORITE_QUERIES] ?: return@map emptyList()
        try {
            Json.decodeFromString<List<String>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    val savedPosts: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[SAVED_POSTS] ?: return@map emptySet()
        try {
            Json.decodeFromString<Set<String>>(raw)
        } catch (_: Exception) {
            emptySet()
        }
    }

    suspend fun getSavedPostsSnapshot(): Set<String> = savedPosts.first()

    suspend fun toggleSavePost(shortcode: String) {
        context.dataStore.edit { prefs ->
            val raw = prefs[SAVED_POSTS] ?: "[]"
            val current = try {
                Json.decodeFromString<MutableSet<String>>(raw)
            } catch (_: Exception) {
                mutableSetOf()
            }
            if (shortcode in current) {
                current.remove(shortcode)
            } else {
                current.add(shortcode)
            }
            prefs[SAVED_POSTS] = Json.encodeToString(current)
        }
    }

    suspend fun getProfilePicsSnapshot(): Map<String, String> = profilePics.first()

    suspend fun saveInstance(url: String, token: String = "") {
        context.dataStore.edit { prefs ->
            prefs[INSTANCE_URL] = url.trimEnd('/')
            prefs[API_TOKEN] = token.trim()
        }
    }

    suspend fun toggleFollow(username: String) {
        context.dataStore.edit { prefs ->
            val raw = prefs[FOLLOWED_USERS] ?: "[]"
            val current = try {
                Json.decodeFromString<MutableSet<String>>(raw)
            } catch (_: Exception) {
                mutableSetOf()
            }
            if (username in current) {
                current.remove(username)
                val picsRaw = prefs[PROFILE_PICS] ?: "{}"
                val pics = try {
                    Json.decodeFromString<MutableMap<String, String>>(picsRaw)
                } catch (_: Exception) {
                    mutableMapOf()
                }
                pics.remove(username)
                prefs[PROFILE_PICS] = Json.encodeToString(pics)
            } else {
                current.add(username)
            }
            prefs[FOLLOWED_USERS] = Json.encodeToString(current)
        }
    }

    suspend fun addFollowedUsers(usernames: Collection<String>) {
        context.dataStore.edit { prefs ->
            val raw = prefs[FOLLOWED_USERS] ?: "[]"
            val current = try {
                Json.decodeFromString<MutableSet<String>>(raw)
            } catch (_: Exception) {
                mutableSetOf()
            }
            current.addAll(usernames)
            prefs[FOLLOWED_USERS] = Json.encodeToString(current)
        }
    }

    suspend fun saveProfilePics(pics: Map<String, String>) {
        context.dataStore.edit { prefs ->
            prefs[PROFILE_PICS] = Json.encodeToString(pics)
        }
    }

    suspend fun updateProfilePic(username: String, url: String) {
        context.dataStore.edit { prefs ->
            val raw = prefs[PROFILE_PICS] ?: "{}"
            val current = try {
                Json.decodeFromString<MutableMap<String, String>>(raw)
            } catch (_: Exception) {
                mutableMapOf()
            }
            current[username] = url
            prefs[PROFILE_PICS] = Json.encodeToString(current)
        }
    }

    suspend fun toggleFavoriteQuery(query: String) {
        context.dataStore.edit { prefs ->
            val raw = prefs[FAVORITE_QUERIES] ?: "[]"
            val current = try {
                Json.decodeFromString<MutableList<String>>(raw)
            } catch (_: Exception) {
                mutableListOf()
            }
            if (query in current) {
                current.remove(query)
            } else {
                current.add(query)
            }
            prefs[FAVORITE_QUERIES] = Json.encodeToString(current)
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
