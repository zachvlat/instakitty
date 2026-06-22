package com.zachvlat.instakitty.ui.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.zachvlat.instakitty.data.remote.Post

@Composable
fun UserScreen(
    username: String,
    onBack: () -> Unit,
    onPostClick: (String) -> Unit,
    viewModel: UserViewModel = viewModel()
) {
    LaunchedEffect(username) {
        viewModel.loadUser(username)
    }

    val state by viewModel.state.collectAsState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index) {
        val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        if (lastVisible >= state.posts.size - 6 && state.endCursor != null && !state.isLoadingMore) {
            viewModel.loadMore()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("←") }
            Spacer(Modifier.weight(1f))
            Text(
                text = state.user?.username ?: username,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(64.dp))
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.error ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadUser(username) }) {
                            Text("Retry")
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        contentPadding = PaddingValues(0.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        item(span = { GridItemSpan(3) }) {
                            state.user?.let { user ->
                                UserHeader(
                                    user = user,
                                    isFollowing = state.isFollowing,
                                    onToggleFollow = { viewModel.toggleFollow() }
                                )
                            }
                        }
                        item(span = { GridItemSpan(3) }) {
                            if (state.posts.isNotEmpty()) {
                                HorizontalDivider()
                            }
                        }
                        items(state.posts, key = { it.shortcode ?: it.id ?: "" }) { post ->
                            ProfilePostThumbnail(
                                post = post,
                                onClick = { post.shortcode?.let(onPostClick) }
                            )
                        }
                        if (state.isLoadingMore) {
                            item(span = { GridItemSpan(3) }) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserHeader(
    user: com.zachvlat.instakitty.data.remote.User,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val pp = user.profilePicture ?: user.profilePicUrl
            if (pp != null) {
                AsyncImage(
                    model = pp,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Posts", user.mediaCount ?: 0)
                StatItem("Followers", user.followerCount ?: 0)
                StatItem("Following", user.followingCount ?: 0)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = user.displayName ?: user.username ?: "",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        if (user.username != null) {
            Text(
                text = user.biography ?: "",
                fontSize = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onToggleFollow,
            modifier = Modifier.fillMaxWidth(),
            colors = if (isFollowing) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ButtonDefaults.buttonColors()
            }
        ) {
            Text(if (isFollowing) "Following" else "Follow")
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun StatItem(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatUserCount(count),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProfilePostThumbnail(
    post: Post,
    onClick: () -> Unit
) {
    val mediaUrl = post.imageUrl ?: post.images?.firstOrNull()?.imageUrl
        ?: post.videoThumbnail

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
    ) {
        if (mediaUrl != null) {
            AsyncImage(
                model = mediaUrl,
                contentDescription = post.altText ?: "Post thumbnail",
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

private fun formatUserCount(n: Int): String {
    return when {
        n >= 1_000_000 -> "${n / 1_000_000}M"
        n >= 1_000 -> "${n / 1_000}k"
        else -> n.toString()
    }
}
