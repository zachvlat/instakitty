package com.zachvlat.instakitty.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

data class ImagePage(
    val imageUrl: String?,
    val videoUrl: String? = null,
    val altText: String? = null
)

@Composable
fun FullscreenImageViewer(
    pages: List<ImagePage>,
    initialPage: Int = 0,
    onDismiss: () -> Unit
) {
    if (pages.isEmpty()) return

    val page = pages[initialPage.coerceIn(0, pages.lastIndex)]

    Dialog(
        onDismissRequest = onDismiss,
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (page.videoUrl != null) {
                VideoPlayer(
                    videoUrl = page.videoUrl,
                    showFullscreenButton = false,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                ZoomableImage(
                    model = page.imageUrl,
                    contentDescription = page.altText,
                    modifier = Modifier.fillMaxSize(),
                    onTap = onDismiss
                )
            }

            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(onClick = onDismiss)
                    .padding(8.dp)
            )
        }
    }
}
