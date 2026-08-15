package com.zachvlat.instakitty.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.zachvlat.instakitty.data.remote.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToUser: (String) -> Unit,
    onNavigateToPost: (String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var showSearch by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TodaySection(
                items = state.todayPosts,
                isLoading = state.isLoadingToday,
                error = state.todayError,
                onRefresh = viewModel::refreshToday,
                onPostClick = onNavigateToPost
            )
        }

        FloatingActionButton(
            onClick = { showSearch = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search users"
            )
        }
    }

    if (showSearch) {
        ModalBottomSheet(
            onDismissRequest = { showSearch = false },
            sheetState = sheetState
        ) {
            SearchSheetContent(
                query = state.searchQuery,
                isSearching = state.isSearching,
                error = state.searchError,
                results = state.searchResults,
                onQueryChange = viewModel::onSearchQueryChange,
                onClear = viewModel::clearSearch,
                onSubmit = {
                    val q = state.searchQuery.trim().removePrefix("https://").removePrefix("http://")
                    if (q.contains("instagram.com/p/") || q.contains("instagram.com/reel/")) {
                        val shortcode = q.split("/p/").lastOrNull()?.split("/")?.firstOrNull()
                            ?: q.split("/reel/").lastOrNull()?.split("/")?.firstOrNull()
                        if (shortcode != null) onNavigateToPost(shortcode)
                    } else if (state.searchResults.isNotEmpty()) {
                        onNavigateToUser(state.searchResults.first().username ?: q)
                    } else {
                        val username = q.trim('/').split("/").firstOrNull()
                            ?.removePrefix("@")
                        if (!username.isNullOrBlank()) onNavigateToUser(username)
                    }
                },
                onUserClick = { username ->
                    showSearch = false
                    username?.let(onNavigateToUser)
                }
            )
        }
    }
}

@Composable
private fun SearchSheetContent(
    query: String,
    isSearching: Boolean,
    error: String?,
    results: List<User>,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onUserClick: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Search users",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Type at least 3 characters") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.Close, "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() })
        )

        Spacer(Modifier.height(8.dp))

        when {
            isSearching -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 24.dp)
                )
            }
            error != null -> {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
            query.length >= 3 && results.isEmpty() -> {
                Text(
                    text = "No users found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
            results.isNotEmpty() -> {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(results, key = { it.username ?: it.id ?: it.hashCode() }) { user ->
                        SearchResultItem(
                            user = user,
                            onClick = { user.username?.let(onUserClick) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SearchResultItem(
    user: User,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val pp = user.profilePicture ?: user.profilePicUrl
        if (pp != null) {
            AsyncImage(
                model = pp,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = user.username?.firstOrNull()?.uppercase() ?: "?",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = user.username ?: "unknown",
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium
            )
            if (!user.displayName.isNullOrBlank()) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TodaySection(
    items: List<HomePostItem>,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onPostClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Recents",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
        }
    }

    when {
        isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
        error != null -> {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = onRefresh) { Text("Retry") }
        }
        items.isEmpty() -> {
            Text(
                text = "No recent posts from people you follow",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        else -> {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { postKey(it.post) }) { item ->
                    TodayPostCard(
                        item = item,
                        onPostClick = onPostClick
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayPostCard(
    item: HomePostItem,
    onPostClick: (String) -> Unit
) {
    val post = item.post
    val imageUrl = post.imageUrl
        ?: post.images?.firstOrNull()?.imageUrl
        ?: post.videoThumbnail

    Card(
        modifier = Modifier
            .width(180.dp)
            .clickable { post.shortcode?.let(onPostClick) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = post.caption ?: "Post",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (post.videoUrl != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Video",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private fun postKey(post: com.zachvlat.instakitty.data.remote.Post): String =
    post.shortcode
        ?: post.id
        ?: post.imageUrl
        ?: post.images?.firstOrNull()?.imageUrl
        ?: post.videoUrl
        ?: post.videoThumbnail
        ?: "post_${post.hashCode()}"
