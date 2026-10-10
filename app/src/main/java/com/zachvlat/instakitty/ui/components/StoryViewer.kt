package com.zachvlat.instakitty.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.zachvlat.instakitty.data.remote.InstaStory
import kotlinx.coroutines.delay

private const val STORY_DURATION_MILLIS = 5000L

@Composable
fun StoryViewer(
    stories: List<InstaStory>,
    title: String?,
    onDismiss: () -> Unit
) {
    if (stories.isEmpty()) return

    var index by remember(stories) { mutableIntStateOf(0) }
    var progress by remember(stories) { mutableFloatStateOf(0f) }
    val current = stories.getOrNull(index)

    fun next() {
        if (index < stories.lastIndex) {
            index++
        } else {
            onDismiss()
        }
    }

    fun prev() {
        if (index > 0) index-- else progress = 0f
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (current != null) {
                val isVideo = current.mediaType == "video" && current.videoUrl != null
                key(current.id ?: index) {
                    if (isVideo) {
                        StoryVideo(
                            videoUrl = current.videoUrl!!,
                            onEnded = { next() },
                            onProgress = { progress = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (current.imageUrl != null) {
                        AsyncImage(
                            model = current.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        LaunchedEffect(current.id ?: index) {
                            progress = 0f
                            animate(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = STORY_DURATION_MILLIS.toInt(),
                                    easing = LinearEasing
                                )
                            ) { value, _ -> progress = value }
                            next()
                        }
                    } else {
                        LaunchedEffect(current.id ?: index) { next() }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { prev() }
                )
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { next() }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    stories.indices.forEach { i ->
                        val fraction = when {
                            i < index -> 1f
                            i == index -> progress
                            else -> 0f
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(Color.White.copy(alpha = 0.35f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                                    .background(Color.White)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!title.isNullOrBlank()) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryVideo(
    videoUrl: String,
    onEnded: () -> Unit,
    onProgress: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val player = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onEnded()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            val duration = player.duration
            if (duration > 0) {
                onProgress((player.currentPosition.toFloat() / duration).coerceIn(0f, 1f))
            }
            delay(50)
        }
    }

    AndroidView(
        factory = {
            PlayerView(it).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                this.player = player
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
