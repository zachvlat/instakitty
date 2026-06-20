package com.zachvlat.instakitty.ui.post

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.zachvlat.instakitty.ui.components.VideoPlayer
import com.zachvlat.instakitty.ui.components.ZoomableImage

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
    val isVideo = state.post?.videoUrl != null

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
            state.post != null -> {
                if (isVideo) {
                    VideoPlayer(
                        videoUrl = state.post!!.videoUrl!!,
                        modifier = Modifier.fillMaxSize()
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                            .clickable(onClick = onBack)
                            .padding(8.dp)
                    )
                } else {
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
                            PostContent(state.post!!, onUserClick)
                        }
                    }
                }
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

        if (!post.images.isNullOrEmpty()) {
            val pagerState = rememberPagerState(pageCount = { post.images.size })

            Box(modifier = Modifier
                .fillMaxWidth()
                .height(450.dp)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    CarouselMediaItem(post.images[page])
                }

                if (post.images.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        repeat(post.images.size) { i ->
                            Box(
                                modifier = Modifier
                                    .size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i == pagerState.currentPage)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    )
                            )
                        }
                    }
                }
            }
        } else if (mediaUrl != null) {
            ZoomableImage(
                model = mediaUrl,
                contentDescription = post.altText ?: "Post media",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                contentScale = ContentScale.Fit
            )
        }

        PostDetails(post, onUserClick)
    }
}

@Composable
private fun PostDetails(
    post: com.zachvlat.instakitty.data.remote.Post,
    onUserClick: (String) -> Unit
) {
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

    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarouselMediaItem(item: MediaItem) {
    if (item.videoUrl != null) {
        VideoPlayer(
            videoUrl = item.videoUrl,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        )
    } else {
        val url = item.imageUrl ?: item.videoThumbnail
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = item.altText ?: "Carousel media",
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentScale = ContentScale.Fit
            )
        }
    }
}
