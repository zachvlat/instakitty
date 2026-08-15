package com.zachvlat.instakitty.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zachvlat.instakitty.data.local.CachedUserPosts
import com.zachvlat.instakitty.data.local.SettingsDataStore
import com.zachvlat.instakitty.data.remote.ApiResult
import com.zachvlat.instakitty.data.remote.KittygramRepository
import com.zachvlat.instakitty.data.remote.Post
import com.zachvlat.instakitty.data.remote.User
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

data class HomePostItem(
    val post: Post,
    val username: String
)

data class HomeUiState(
    val searchQuery: String = "",
    val searchResults: List<User> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val todayPosts: List<HomePostItem> = emptyList(),
    val isLoadingToday: Boolean = false,
    val todayError: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = SettingsDataStore(application)
    private val repository = KittygramRepository(dataStore)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        refreshToday()
    }

    fun refreshToday() {
        viewModelScope.launch { loadTodayPosts() }
    }

    private suspend fun loadTodayPosts() {
        _state.value = _state.value.copy(isLoadingToday = true, todayError = null)
        val followed = dataStore.followedUsers.first().distinctBy { it.lowercase() }.toList()
        if (followed.isEmpty()) {
            _state.value = _state.value.copy(todayPosts = emptyList(), isLoadingToday = false)
            return
        }
        val cache = dataStore.getUserPostsCacheSnapshot()
        val now = System.currentTimeMillis()
        val cutoff = now - RECENT_DAYS * DAY_MILLIS
        val semaphore = Semaphore(8)

        val freshKeys = followed.filter { username ->
            cache[username.lowercase()]?.let { now - it.fetchedAt < CACHE_TTL_MILLIS } == true
        }
        val staleKeys = followed.filter { it !in freshKeys }

        _state.update { current ->
            val cached = freshKeys.flatMap { username ->
                cache[username.lowercase()]?.posts.orEmpty()
                    .map { HomePostItem(post = it, username = username) }
            }
            current.copy(
                todayPosts = mergePosts(cached),
                isLoadingToday = staleKeys.isNotEmpty()
            )
        }

        if (staleKeys.isEmpty()) {
            _state.value = _state.value.copy(isLoadingToday = false, todayError = null)
            return
        }

        val successes = AtomicInteger(0)
        val fetched = try {
            coroutineScope {
                staleKeys.map { username ->
                    async {
                        semaphore.withPermit {
                            val posts = fetchPostsForUser(username)
                            if (posts != null) {
                                successes.incrementAndGet()
                                val result = pickPostsForUser(posts, cutoff)
                                if (result.isNotEmpty()) {
                                    _state.update { current ->
                                        current.copy(
                                            todayPosts = mergePosts(current.todayPosts + result)
                                        )
                                    }
                                }
                                username.lowercase() to CachedUserPosts(now, posts.map { it.post })
                            } else {
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoadingToday = false,
                todayError = e.message ?: "Failed to load recent posts"
            )
            return
        }
        dataStore.saveUserPostsCache(cache + fetched.toMap())
        _state.value = _state.value.copy(
            isLoadingToday = false,
            todayError = if (successes.get() == 0 && freshKeys.isEmpty()) {
                "Couldn't reach the instance. Check that it's online and on the same network."
            } else {
                null
            }
        )
    }

    private suspend fun fetchPostsForUser(username: String): List<HomePostItem>? {
        val resp = repository.getUser(username)
        if (resp !is ApiResult.Success) return null
        return resp.data.posts.orEmpty()
            .map { HomePostItem(post = it, username = username) }
    }

    private fun pickPostsForUser(posts: List<HomePostItem>, cutoff: Long): List<HomePostItem> {
        val recent = posts.filter { it.post.timestamp != null && isRecent(it.post.timestamp, cutoff) }
        val older = posts.filterNot { it in recent }
        return (recent + older)
            .distinctBy { postKey(it.post) }
            .take(USER_FILL_TARGET)
    }

    private fun mergePosts(items: List<HomePostItem>): List<HomePostItem> =
        items.distinctBy { postKey(it.post) }.sortedByDescending { it.post.timestamp }

    private fun postKey(post: Post): String =
        post.shortcode
            ?: post.id
            ?: post.imageUrl
            ?: post.images?.firstOrNull()?.imageUrl
            ?: post.videoUrl
            ?: post.videoThumbnail
            ?: "post_${post.hashCode()}"

    private fun isRecent(timestamp: Long, cutoff: Long): Boolean {
        val millis = if (timestamp > 10_000_000_000L) timestamp else timestamp * 1000L
        return millis >= cutoff
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
        private const val RECENT_DAYS = 7
        private const val USER_FILL_TARGET = 3
        private const val CACHE_TTL_MILLIS = 15L * 60 * 1000
    }

    fun onSearchQueryChange(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        searchJob?.cancel()
        if (query.length >= 3) {
            searchJob = viewModelScope.launch {
                delay(300)
                performSearch(query)
            }
        } else {
            _state.value = _state.value.copy(
                searchResults = emptyList(),
                isSearching = false,
                searchError = null
            )
        }
    }

    private suspend fun performSearch(query: String) {
        _state.value = _state.value.copy(isSearching = true, searchError = null)
        when (val result = repository.searchUsers(query)) {
            is ApiResult.Success -> {
                _state.value = _state.value.copy(
                    searchResults = result.data,
                    isSearching = false,
                    searchError = if (result.data.isEmpty()) "No users found" else null
                )
            }
            is ApiResult.Error -> {
                _state.value = _state.value.copy(
                    searchResults = emptyList(),
                    isSearching = false,
                    searchError = result.message
                )
            }
            is ApiResult.NetworkError -> {
                _state.value = _state.value.copy(
                    searchResults = emptyList(),
                    isSearching = false,
                    searchError = result.message
                )
            }
        }
    }

    fun clearSearch() {
        _state.value = _state.value.copy(
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false,
            searchError = null
        )
        searchJob?.cancel()
    }
}
