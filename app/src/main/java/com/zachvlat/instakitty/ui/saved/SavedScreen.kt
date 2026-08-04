package com.zachvlat.instakitty.ui.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zachvlat.instakitty.data.remote.Post
import com.zachvlat.instakitty.ui.components.PostCard

@OptIn(ExperimentalMaterial3Api::class)
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
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        post.shortcode?.let(viewModel::removeSaved)
                                        true
                                    } else {
                                        false
                                    }
                                }
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFFE53935), shape = MaterialTheme.shapes.medium)
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Remove",
                                            tint = Color.White
                                        )
                                    }
                                }
                            ) {
                                PostCard(
                                    post = post,
                                    onPostClick = onOpenPost,
                                    onUserClick = onOpenUser
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}