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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomePostItem(
    val post: Post,
    val username: String
)

private sealed interface UserFetch {
    data class Success(val items: List<HomePostItem>) : UserFetch
    object RateLimited : UserFetch
    object Failed : UserFetch
}

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
    private var retryJob: Job? = null
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            // Reload whenever the followed list changes (imports, new follows),
            // and on first collection as the initial load.
            var lastUsers: Set<String>? = null
            dataStore.followedUsers.collect { users ->
                if (users != lastUsers) {
                    lastUsers = users
                    refreshToday()
                }
            }
        }
    }

    fun refreshToday() {
        retryJob?.cancel()
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch { loadTodayPosts() }
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

        val freshKeys = followed.filter { username ->
            cache[username.lowercase()]?.let { now - it.fetchedAt < CACHE_TTL_MILLIS } == true
        }
        val staleKeys = followed.filter { it !in freshKeys }

        _state.update { current ->
            val cached = followed.flatMap { username ->
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

        // Re-order dynamically by recency: each fetch discovers a user's real
        // newest post time, so we re-sort whoever is still pending and pull in
        // the users with the newest content first — regardless of the order of
        // the following list.
        val remaining = staleKeys.toMutableList()
        val newestByUser = mutableMapOf<String, Long>().apply {
            staleKeys.forEach { username ->
                put(
                    username.lowercase(),
                    cache[username.lowercase()]?.posts
                        ?.maxOfOrNull { it.timestamp ?: 0L } ?: 0L
                )
            }
        }
        val fetched = mutableMapOf<String, CachedUserPosts>()
        var rateLimited = false
        var fetchCount = 0

        while (remaining.isNotEmpty() && fetchCount < MAX_USERS_PER_REFRESH) {
            remaining.sortByDescending { newestByUser[it.lowercase()] ?: 0L }
            val username = remaining.removeAt(0)
            delay(STAGGER_MILLIS)
            when (val outcome = fetchPostsForUser(username)) {
                is UserFetch.Success -> {
                    val picked = pickPostsForUser(outcome.items, cutoff)
                    if (picked.isNotEmpty()) {
                        _state.update { current ->
                            current.copy(
                                todayPosts = mergePosts(current.todayPosts + picked)
                            )
                        }
                    }
                    fetched[username.lowercase()] =
                        CachedUserPosts(now, outcome.items.map { it.post })
                    newestByUser[username.lowercase()] =
                        outcome.items.maxOfOrNull { it.post.timestamp ?: 0L } ?: 0L
                    fetchCount++

                    if (feedIsComplete(remaining, newestByUser, cutoff)) break
                }
                UserFetch.RateLimited -> {
                    // Stop hammering the instance; it'll stay blocked as long as
                    // we keep firing requests. Retry later with a single poke.
                    rateLimited = true
                    break
                }
                UserFetch.Failed -> fetchCount++
            }
        }

        dataStore.saveUserPostsCache(cache + fetched)
        _state.value = _state.value.copy(
            isLoadingToday = false,
            todayError = null
        )

        if (rateLimited) {
            retryJob?.cancel()
            retryJob = viewModelScope.launch {
                delay(RETRY_DELAY_MILLIS)
                loadTodayPosts()
            }
        }
    }

    private suspend fun fetchPostsForUser(username: String): UserFetch {
        val resp = repository.getUserTimeline(username)
        if (resp is ApiResult.Success) {
            return UserFetch.Success(
                (resp.data.timeline?.items).orEmpty()
                    .map { HomePostItem(post = it, username = username) }
            )
        }
        if (resp is ApiResult.Error && isRateLimited(resp)) {
            return UserFetch.RateLimited
        }
        return UserFetch.Failed
    }

    private fun isRateLimited(error: ApiResult.Error): Boolean =
        error.type.contains("503", ignoreCase = true) ||
            error.type.contains("ratelimit", ignoreCase = true) ||
            error.message.contains("rate", ignoreCase = true)

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
            ?: post.thumbnail
            ?: "post_${post.hashCode()}"

    private fun feedIsComplete(
        remaining: List<String>,
        newestByUser: Map<String, Long>,
        cutoff: Long
    ): Boolean {
        val recents = _state.value.todayPosts.filter {
            it.post.timestamp != null && isRecent(it.post.timestamp, cutoff)
        }
        if (recents.size < ENOUGH_RECENT) return false
        val threshold = recents.minOf { postMillis(it.post.timestamp ?: 0L) }
        val next = remaining.firstOrNull() ?: return true
        val nextNewest = newestByUser[next.lowercase()] ?: return false
        if (nextNewest <= 0L) return false
        return postMillis(nextNewest) < threshold
    }

    private fun postMillis(timestamp: Long): Long =
        if (timestamp > 10_000_000_000L) timestamp else timestamp * 1000L

    private fun isRecent(timestamp: Long, cutoff: Long): Boolean =
        postMillis(timestamp) >= cutoff

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
        private const val RECENT_DAYS = 7
        private const val USER_FILL_TARGET = 3
        private const val CACHE_TTL_MILLIS = 60L * 60 * 1000
        private const val STAGGER_MILLIS = 400L
        private const val ENOUGH_RECENT = 24
        private const val MAX_USERS_PER_REFRESH = 12
        private const val RETRY_DELAY_MILLIS = 60_000L
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
