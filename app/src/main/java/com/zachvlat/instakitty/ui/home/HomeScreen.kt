package com.zachvlat.instakitty.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.zachvlat.instakitty.data.remote.User

@Composable
fun HomeScreen(
    onNavigateToUser: (String) -> Unit,
    onNavigateToPost: (String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        Text(
            text = "Instagram, but private",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Instance: ${state.instanceUrl}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Search users...",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Enter a username to search",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("username...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            val q = state.searchQuery.trim().removePrefix("https://").removePrefix("http://")
                            if (q.contains("instagram.com/p/") || q.contains("instagram.com/reel/")) {
                                val shortcode = q.split("/p/").lastOrNull()?.split("/")?.firstOrNull()
                                    ?: q.split("/reel/").lastOrNull()?.split("/")?.firstOrNull()
                                if (shortcode != null) onNavigateToPost(shortcode)
                            } else if (state.searchResults.isNotEmpty()) {
                                onNavigateToUser(state.searchResults.first().username ?: q)
                            } else {
                                val username = q.trim('/').split("/").firstOrNull()
                                    ?.removePrefix("@")
                                if (!username.isNullOrBlank()) onNavigateToUser(username)
                            }
                        }
                    ),
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = viewModel::clearSearch) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        val searchError = state.searchError

        when {
            state.isSearching -> {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
            searchError != null -> {
                Text(
                    text = searchError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            state.searchResults.isNotEmpty() -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        state.searchResults.forEachIndexed { index, user ->
                            SearchResultItem(
                                user = user,
                                onClick = { user.username?.let(onNavigateToUser) }
                            )
                            if (index < state.searchResults.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }

        if (state.searchQuery.length < 2 && state.searchResults.isEmpty() && !state.isSearching) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Type at least 4 characters to search",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SearchResultItem(
    user: User,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val pp = user.profilePicture ?: user.profilePicUrl
        if (pp != null) {
            AsyncImage(
                model = pp,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = user.username?.firstOrNull()?.uppercase() ?: "?",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = user.username ?: "unknown",
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium
            )
            if (!user.displayName.isNullOrBlank()) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
