package com.zachvlat.instakitty.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zachvlat.instakitty.data.remote.Post

@Composable
fun PostCard(
    post: Post,
    onPostClick: (String) -> Unit,
    onUserClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { post.shortcode?.let(onPostClick) }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val profilePic = post.user?.profilePicture
                    ?: post.user?.profilePicUrl
                if (profilePic != null) {
                    AsyncImage(
                        model = profilePic,
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = post.user?.username?.firstOrNull()?.uppercase() ?: "?",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.clickable { post.user?.username?.let(onUserClick) }) {
                    Text(
                        text = post.user?.username ?: "unknown",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (post.user?.displayName != null) {
                        Text(
                            text = post.user.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            val mediaUrl = post.imageUrl ?: post.images?.firstOrNull()?.imageUrl
                ?: post.videoThumbnail
            if (mediaUrl != null) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = mediaUrl,
                        contentDescription = post.altText ?: "Post image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        contentScale = ContentScale.Crop
                    )
                    if (post.videoUrl != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(48.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Play video",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                if (post.likes != null || post.commentCount != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (post.likes != null) {
                            Text(
                                text = "♥ ${formatCount(post.likes)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (post.likes != null && post.commentCount != null) {
                            Spacer(Modifier.width(16.dp))
                        }
                        if (post.commentCount != null) {
                            Text(
                                text = "💬 ${formatCount(post.commentCount)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (!post.caption.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = post.caption,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun formatCount(n: Int): String {
    return when {
        n >= 1_000_000 -> "${n / 1_000_000}.${(n % 1_000_000) / 100_000}M"
        n >= 1_000 -> "${n / 1_000}.${(n % 1_000) / 100}k"
        else -> n.toString()
    }
}
