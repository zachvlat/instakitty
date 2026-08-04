package com.zachvlat.instakitty.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@Composable
fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isFullscreen by remember { mutableStateOf(false) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        }
    }

    val playerView = remember {
        PlayerView(context).apply {
            this.player = player
            useController = true
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playerView.player = null
            player.release()
        }
    }

    Box(modifier = modifier) {
        if (isFullscreen) {
            val exitFullscreen = {
                (playerView.parent as? ViewGroup)?.removeView(playerView)
                isFullscreen = false
            }
            Dialog(
                onDismissRequest = exitFullscreen,
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                val activity = LocalContext.current.findActivity()
                DisposableEffect(activity) {
                    val controller = activity?.let {
                        WindowInsetsControllerCompat(it.window, it.window.decorView)
                    }
                    controller?.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller?.hide(WindowInsetsCompat.Type.systemBars())
                    onDispose {
                        controller?.show(WindowInsetsCompat.Type.systemBars())
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { playerView },
                        modifier = Modifier.fillMaxSize()
                    )
                    FullscreenButton(
                        icon = Icons.Filled.FullscreenExit,
                        contentDescription = "Exit fullscreen",
                        onClick = exitFullscreen,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
            }
        } else {
            AndroidView(
                factory = { playerView },
                modifier = Modifier.fillMaxSize()
            )
            FullscreenButton(
                icon = Icons.Filled.Fullscreen,
                contentDescription = "Fullscreen",
                onClick = { isFullscreen = true },
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
private fun FullscreenButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(12.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
