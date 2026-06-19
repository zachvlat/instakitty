package com.zachvlat.instakitty.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        private val INSTANCE_URL = stringPreferencesKey("instance_url")
        private val API_TOKEN = stringPreferencesKey("api_token")
        private val FOLLOWED_USERS = stringPreferencesKey("followed_users")
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
            if (username in current) current.remove(username) else current.add(username)
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

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
