package com.zachvlat.instakitty.ui.saved

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zachvlat.instakitty.ui.components.PostCard

@Composable
fun SavedScreen(
    onOpenPost: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    viewModel: SavedViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val removing by viewModel.removing.collectAsState()
    val visiblePosts = state.posts.filter { it.shortcode !in removing }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Saved",
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                visiblePosts.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No saved posts yet.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Tap the bookmark on a post to save it here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(visiblePosts, key = { it.shortcode ?: it.id ?: it.hashCode() }) { post ->
                            PostCard(
                                post = post,
                                onPostClick = onOpenPost,
                                onUserClick = onOpenUser,
                                onDelete = { post.shortcode?.let(viewModel::removeSaved) }
                            )
                        }
                    }
                }
            }
        }
    }
}