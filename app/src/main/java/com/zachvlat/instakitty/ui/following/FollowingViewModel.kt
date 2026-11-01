package com.zachvlat.instakitty.ui.following

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zachvlat.instakitty.data.local.AvatarCache
import com.zachvlat.instakitty.data.local.ExportBundle
import com.zachvlat.instakitty.data.local.SettingsDataStore
import com.zachvlat.instakitty.data.remote.ApiResult
import com.zachvlat.instakitty.data.remote.KittygramRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

// ------------------------------------------------------------
// UI state that the composables read from
// ------------------------------------------------------------
data class FollowingUiState(
    val usernames: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val avatars: Map<String, String> = emptyMap()
)

// ------------------------------------------------------------
// ViewModel
// ------------------------------------------------------------
class FollowingViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = SettingsDataStore(application)
    private val repository = KittygramRepository(dataStore)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /* ---------- State flows ---------- */
    private val _state = MutableStateFlow(FollowingUiState())
    val state: StateFlow<FollowingUiState> = _state.asStateFlow()

    private val _removingUsers = MutableStateFlow<Set<String>>(emptySet())
    val removingUsers: StateFlow<Set<String>> = _removingUsers.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    /* ---------- Init: collect from DataStore ---------- */
    init {
        // Avatars (only keep local files)
        viewModelScope.launch {
            dataStore.profilePics.collect { pics ->
                _state.value = _state.value.copy(
                    avatars = pics.filterValues { it.startsWith("/") }
                )
            }
        }

        // Followed usernames
        viewModelScope.launch {
            dataStore.followedUsers.collect { users ->
                val sorted = users.toList().sorted()
                // Keep only those that are still in the list
                _removingUsers.value = _removingUsers.value.intersect(sorted.toSet())
                _state.value = _state.value.copy(
                    usernames = sorted,
                    isLoading = false
                )
            }
        }
    }

    /* ---------- Remove a user ---------- */
    fun removeUser(username: String) {
        // Optimistically show the user as “removing”
        _removingUsers.value += username

        viewModelScope.launch {
            try {
                dataStore.toggleFollow(username)   // toggles unfollow
                // Success – remove flag
                _removingUsers.value -= username
            } catch (e: Exception) {
                // Failure – rollback UI flag
                _removingUsers.value -= username
            }
        }
    }

    /* ---------- Export to JSON ---------- */

    /**
     * Builds a self-contained backup. Built off the main thread because it reads avatar files
     * and may fetch metadata for saved posts that were cached before caching was introduced.
     */
    suspend fun buildExportJson(): String? {
        _isExporting.value = true
        return withContext(Dispatchers.IO) {
            try {
                val usernames = dataStore.followedUsers.first().sorted()
                val pics = dataStore.getProfilePicsSnapshot()

                val avatars = usernames.mapNotNull { username ->
                    val path = pics[username] ?: return@mapNotNull null
                    val file = File(path)
                    if (!file.isFile) return@mapNotNull null
                    val bytes = try {
                        file.readBytes()
                    } catch (_: Exception) {
                        return@mapNotNull null
                    }
                    username to Base64.encodeToString(bytes, Base64.NO_WRAP)
                }.toMap()

                // Use cached metadata where possible so exporting does not hammer the API.
                val cached = dataStore.getSavedPostsCacheSnapshot().toMutableMap()
                val shortcodes = dataStore.getSavedPostsSnapshot().sorted()
                val missing = shortcodes.filter { it !in cached }
                if (missing.isNotEmpty()) {
                    for (shortcode in missing) {
                        when (val result = repository.getPost(shortcode)) {
                            is ApiResult.Success -> cached[shortcode] = result.data
                            else -> Unit
                        }
                    }
                    dataStore.saveSavedPostsCache(cached)
                }

                val bundle = ExportBundle(
                    exportedAt = System.currentTimeMillis(),
                    following = usernames,
                    avatars = avatars,
                    savedPosts = shortcodes.mapNotNull { cached[it] },
                    savedShortcodes = shortcodes
                )
                json.encodeToString(bundle)
            } catch (_: Exception) {
                null
            } finally {
                _isExporting.value = false
            }
        }
    }

    /** Writes a previously built export to a user-selected document. */
    suspend fun writeExport(uri: Uri, jsonText: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                    it.write(jsonText.toByteArray(Charsets.UTF_8))
                } != null
            } catch (_: Exception) {
                false
            }
        }

    /* ---------- Import from JSON ---------- */

    fun importFromUri(uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                } catch (_: Exception) {
                    null
                }
            }
            if (text == null) {
                onResult(false, "Could not read the selected file")
            } else {
                importJson(text, onResult)
            }
        }
    }

    fun importJson(jsonText: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val bundle = parseBundle(jsonText)
                if (bundle == null) {
                    onResult(false, "Invalid JSON: expected an InstaKitty export")
                    return@launch
                }

                val shortcodes = (
                    bundle.savedShortcodes +
                        bundle.savedPosts.mapNotNull { it.shortcode }
                    ).distinct()
                val usernames = (bundle.following + bundle.avatars.keys).distinct()
                if (usernames.isEmpty() && shortcodes.isEmpty()) {
                    onResult(false, "Nothing to import")
                    return@launch
                }

                if (usernames.isNotEmpty()) dataStore.addFollowedUsers(usernames)
                if (shortcodes.isNotEmpty()) dataStore.addSavedPosts(shortcodes)

                if (bundle.savedPosts.isNotEmpty()) {
                    val cached = dataStore.getSavedPostsCacheSnapshot().toMutableMap()
                    bundle.savedPosts.forEach { post ->
                        post.shortcode?.let { cached[it] = post }
                    }
                    dataStore.saveSavedPostsCache(cached)
                }

                val restoredAvatars = if (bundle.avatars.isEmpty()) {
                    emptyMap()
                } else {
                    withContext(Dispatchers.IO) {
                        restoreAvatars(bundle.avatars)
                    }
                }
                if (restoredAvatars.isNotEmpty()) {
                    dataStore.saveProfilePics(
                        dataStore.getProfilePicsSnapshot() + restoredAvatars
                    )
                }

                val parts = buildList {
                    if (usernames.isNotEmpty()) add("${usernames.size} profile(s)")
                    if (restoredAvatars.isNotEmpty()) add("${restoredAvatars.size} avatar(s)")
                    if (shortcodes.isNotEmpty()) add("${shortcodes.size} saved post(s)")
                }
                onResult(true, "Imported ${parts.joinToString(", ")}")
            } catch (e: Exception) {
                onResult(false, "Invalid JSON: ${e.message}")
            }
        }
    }

    /** Accepts both the old `["username", ...]` export and the full bundle format. */
    private fun parseBundle(text: String): ExportBundle? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return try {
            if (trimmed.startsWith("[")) {
                ExportBundle(following = json.decodeFromString<List<String>>(trimmed))
            } else {
                json.decodeFromString<ExportBundle>(trimmed)
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun restoreAvatars(avatars: Map<String, String>): Map<String, String> {
        val app = getApplication<Application>()
        val restored = mutableMapOf<String, String>()
        for ((username, encoded) in avatars) {
            try {
                val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                AvatarCache.saveBytes(app, username, bytes)?.let { restored[username] = it }
            } catch (_: IllegalArgumentException) {
                // Skip a corrupted avatar instead of failing the whole import.
            }
        }
        return restored
    }
}
