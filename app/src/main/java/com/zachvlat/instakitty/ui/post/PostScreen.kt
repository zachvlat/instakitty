package com.zachvlat.instakitty.ui.post

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.zachvlat.instakitty.data.remote.MediaItem

@Composable
fun PostScreen(
    shortcode: String,
    onBack: () -> Unit,
    onUserClick: (String) -> Unit,
    viewModel: PostViewModel = viewModel()
) {
    LaunchedEffect(shortcode) {
        viewModel.loadPost(shortcode)
    }

    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("←") }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Post",
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
                        Button(onClick = { viewModel.loadPost(shortcode) }) {
                            Text("Retry")
                        }
                    }
                }
                state.post != null -> PostContent(state.post!!, onUserClick)
            }
        }
    }
}

@Composable
private fun PostContent(
    post: com.zachvlat.instakitty.data.remote.Post,
    onUserClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        val mediaUrl = post.imageUrl
            ?: post.videoThumbnail
            ?: post.images?.firstOrNull()?.imageUrl
            ?: post.images?.firstOrNull()?.videoThumbnail

        if (mediaUrl != null) {
            AsyncImage(
                model = mediaUrl,
                contentDescription = post.altText ?: "Post media",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                contentScale = ContentScale.Fit
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pp = post.user?.profilePicture ?: post.user?.profilePicUrl
                if (pp != null) {
                    AsyncImage(
                        model = pp,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.extraLarge),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    TextButton(
                        onClick = { post.user?.username?.let(onUserClick) }
                    ) {
                        Text(
                            text = post.user?.username ?: "unknown",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (post.user?.displayName != null) {
                        Text(
                            text = post.user.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (post.likes != null) {
                    Text("♥ ${post.likes}", fontWeight = FontWeight.Bold)
                }
                if (post.commentCount != null) {
                    Text("💬 ${post.commentCount}")
                }
                if (post.viewCount != null) {
                    Text("▶ ${post.viewCount}")
                }
            }

            if (!post.caption.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = post.caption,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (!post.images.isNullOrEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Carousel (${post.images.size} items)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                post.images.forEach { item ->
                    CarouselMediaItem(item)
                }
            }
        }
    }
}

@Composable
private fun CarouselMediaItem(item: MediaItem) {
    val url = item.imageUrl ?: item.videoThumbnail
    if (url != null) {
        Spacer(Modifier.height(8.dp))
        AsyncImage(
            model = url,
            contentDescription = item.altText ?: "Carousel media",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp),
            contentScale = ContentScale.Fit
        )
    }
}
