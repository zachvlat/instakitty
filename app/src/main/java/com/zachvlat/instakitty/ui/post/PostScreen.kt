package com.zachvlat.instakitty.ui.post

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.zachvlat.instakitty.data.local.MediaDownloader
import com.zachvlat.instakitty.data.remote.Comment
import com.zachvlat.instakitty.data.remote.MediaItem
import com.zachvlat.instakitty.ui.components.FullscreenImageViewer
import com.zachvlat.instakitty.ui.components.ImagePage
import com.zachvlat.instakitty.ui.components.MediaFullscreenButton
import com.zachvlat.instakitty.ui.components.RichText
import com.zachvlat.instakitty.ui.components.VideoPlayer
import kotlinx.coroutines.launch

@Composable
fun PostScreen(
    shortcode: String,
    onBack: () -> Unit,
    onUserClick: (String) -> Unit,
    onHashtagClick: (String) -> Unit,
    siblings: List<String> = emptyList(),
    viewModel: PostViewModel = viewModel()
) {
    // Posts opened from a profile carry that profile's shortcodes so we can swipe between them.
    val postList = remember(siblings, shortcode) {
        val base = siblings.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (shortcode in base) base else base + shortcode
    }
    var currentIndex by rememberSaveable(shortcode) {
        mutableIntStateOf(postList.indexOf(shortcode).coerceAtLeast(0))
    }
    val displayedShortcode = postList.getOrNull(currentIndex) ?: shortcode

    LaunchedEffect(displayedShortcode) {
        viewModel.loadPost(displayedShortcode)
    }

    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The currently visible media item, reported by PostContent (carousel page or single item).
    var currentMedia by remember(displayedShortcode) { mutableStateOf<MediaItem?>(null) }
    var isDownloading by remember(displayedShortcode) { mutableStateOf(false) }

    var viewerPages by remember { mutableStateOf<List<ImagePage>?>(null) }
    var viewerInitialPage by remember { mutableIntStateOf(0) }

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
                    Button(onClick = { viewModel.loadPost(displayedShortcode) }) {
                        Text("Retry")
                    }
                }
            }
            state.post != null -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onBack) { Text("←") }
                        Spacer(Modifier.weight(1f))
                        state.post?.shortcode?.let { postCode ->
                            IconButton(onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "https://www.instagram.com/p/$postCode"
                                    )
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, "Share post")
                                )
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "Share",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        val downloadUrl = currentMedia?.videoUrl
                            ?: currentMedia?.imageUrl
                            ?: currentMedia?.videoThumbnail
                        IconButton(
                            onClick = {
                                if (downloadUrl != null && !isDownloading) {
                                    isDownloading = true
                                    val baseName = listOfNotNull(
                                        state.post?.user?.username,
                                        state.post?.shortcode
                                    ).joinToString("_").ifBlank { "instakitty" } +
                                        "_" + System.currentTimeMillis()
                                    scope.launch {
                                        val result = MediaDownloader.download(
                                            context = context.applicationContext,
                                            url = downloadUrl,
                                            baseName = baseName,
                                            fallbackIsVideo = currentMedia?.videoUrl != null
                                        )
                                        isDownloading = false
                                        val message = result.fold(
                                            onSuccess = { "Saved to $it" },
                                            onFailure = {
                                                "Download failed: ${it.message ?: "unknown error"}"
                                            }
                                        )
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = downloadUrl != null && !isDownloading
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = "Download",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = viewModel::toggleSave) {
                            Icon(
                                imageVector = if (state.isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = if (state.isSaved) "Unsave" else "Save",
                                tint = if (state.isSaved)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Reset carousel page and scroll position whenever the post changes.
                        key(displayedShortcode) {
                            PostContent(
                                post = state.post!!,
                                comments = state.comments,
                                isLoadingComments = state.isLoadingComments,
                                onUserClick = onUserClick,
                                onHashtagClick = onHashtagClick,
                                onMediaSelected = { currentMedia = it },
                                onSwipeToNext = {
                                    if (currentIndex < postList.lastIndex) currentIndex += 1
                                },
                                onSwipeToPrevious = {
                                    if (currentIndex > 0) currentIndex -= 1
                                },
                                onOpenViewer = { pages, index ->
                                    viewerPages = pages
                                    viewerInitialPage = index
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    viewerPages?.let { pages ->
        FullscreenImageViewer(
            pages = pages,
            initialPage = viewerInitialPage,
            onDismiss = { viewerPages = null }
        )
    }
}

@Composable
private fun PostContent(
    post: com.zachvlat.instakitty.data.remote.Post,
    comments: List<Comment>,
    isLoadingComments: Boolean,
    onUserClick: (String) -> Unit,
    onHashtagClick: (String) -> Unit,
    onMediaSelected: (MediaItem?) -> Unit,
    onSwipeToNext: () -> Unit,
    onSwipeToPrevious: () -> Unit,
    onOpenViewer: (List<ImagePage>, Int) -> Unit
) {
    val currentOnSwipeToNext by rememberUpdatedState(onSwipeToNext)
    val currentOnSwipeToPrevious by rememberUpdatedState(onSwipeToPrevious)
    val currentOnMediaSelected by rememberUpdatedState(onMediaSelected)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var totalDrag = 0f
                val threshold = 72.dp.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onDragEnd = {
                        when {
                            totalDrag <= -threshold -> currentOnSwipeToNext()
                            totalDrag >= threshold -> currentOnSwipeToPrevious()
                        }
                        totalDrag = 0f
                    },
                    onDragCancel = { totalDrag = 0f }
                ) { change, dragAmount ->
                    // A carousel owns horizontal drags inside its pager, so this only sees
                    // gestures that the media area did not consume.
                    change.consume()
                    totalDrag += dragAmount
                }
            }
            .verticalScroll(rememberScrollState())
    ) {
        val mediaUrl = post.imageUrl
            ?: post.videoThumbnail

        if (!post.images.isNullOrEmpty()) {
            val pagerState = rememberPagerState(pageCount = { post.images.size })
            val pages = remember(post.images) {
                post.images.map { ImagePage(it.imageUrl, it.videoUrl, it.altText) }
            }

            LaunchedEffect(pagerState.currentPage, post.images) {
                currentOnMediaSelected(post.images.getOrNull(pagerState.currentPage))
            }

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

                MediaFullscreenButton(
                    icon = Icons.Filled.Fullscreen,
                    contentDescription = "Fullscreen",
                    onClick = { onOpenViewer(pages, pagerState.currentPage) },
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        } else if (post.videoUrl != null) {
            LaunchedEffect(post.videoUrl) {
                currentOnMediaSelected(MediaItem(videoUrl = post.videoUrl))
            }
            VideoPlayer(
                videoUrl = post.videoUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(450.dp)
            )
        } else if (mediaUrl != null) {
            LaunchedEffect(mediaUrl) {
                currentOnMediaSelected(MediaItem(imageUrl = mediaUrl))
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = mediaUrl,
                    contentDescription = post.altText ?: "Post media",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp),
                    contentScale = ContentScale.Fit
                )
                MediaFullscreenButton(
                    icon = Icons.Filled.Fullscreen,
                    contentDescription = "Fullscreen",
                    onClick = { onOpenViewer(listOf(ImagePage(mediaUrl, null, post.altText)), 0) },
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        } else {
            LaunchedEffect(Unit) {
                currentOnMediaSelected(null)
            }
        }

        PostDetails(post, onUserClick, onHashtagClick)

        CommentsSection(comments, isLoadingComments, onUserClick, onHashtagClick)
    }
}

@Composable
private fun PostDetails(
    post: com.zachvlat.instakitty.data.remote.Post,
    onUserClick: (String) -> Unit,
    onHashtagClick: (String) -> Unit
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
            RichText(
                text = post.caption,
                style = MaterialTheme.typography.bodyMedium,
                onHashtagClick = onHashtagClick,
                onUserClick = onUserClick
            )
        }

    }
}

@Composable
private fun CommentsSection(
    comments: List<Comment>,
    isLoading: Boolean,
    onUserClick: (String) -> Unit,
    onHashtagClick: (String) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Text(
            text = "Comments",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }
            comments.isEmpty() -> {
                Text(
                    text = "No comments",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                comments.forEach { comment ->
                    CommentItem(comment, onUserClick, onHashtagClick)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun CommentItem(
    comment: Comment,
    onUserClick: (String) -> Unit,
    onHashtagClick: (String) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        val pp = comment.user?.profilePicture ?: comment.user?.profilePicUrl
        if (pp != null) {
            AsyncImage(
                model = pp,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(MaterialTheme.shapes.extraLarge),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { comment.user?.username?.let(onUserClick) },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text(
                        text = comment.user?.username ?: "unknown",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (comment.user?.isVerified == true) {
                    Text(
                        text = " ✓",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (!comment.text.isNullOrBlank()) {
                RichText(
                    text = comment.text,
                    style = MaterialTheme.typography.bodyMedium,
                    onHashtagClick = onHashtagClick,
                    onUserClick = onUserClick
                )
            }
            if (comment.likes != null && comment.likes > 0) {
                Text(
                    text = "${comment.likes} like${if (comment.likes > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
