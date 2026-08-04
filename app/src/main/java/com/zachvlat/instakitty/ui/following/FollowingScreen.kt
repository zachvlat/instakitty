package com.zachvlat.instakitty.ui.following

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun FollowingScreen(
    onUserClick: (String) -> Unit,
    viewModel: FollowingViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val removingUsers by viewModel.removingUsers.collectAsState()
    val visibleUsers = state.usernames.filter { it !in removingUsers }
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importResult by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val json = viewModel.exportJson()
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, json)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Export follows"))
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export")
            }
            OutlinedButton(
                onClick = {
                    importText = ""
                    importResult = ""
                    showImportDialog = true
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Import")
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                visibleUsers.isEmpty() -> {
                    Text(
                        text = "No followed profiles yet.\nFollow a profile to see it here.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(visibleUsers, key = { it }) { username ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onUserClick(username) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.Person,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = username,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    IconButton(onClick = { viewModel.removeUser(username) }) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        ImportDialog(
            text = importText,
            result = importResult,
            onTextChange = { importText = it },
            onDismiss = { showImportDialog = false },
            onImport = {
                viewModel.importJson(importText) { ok, msg ->
                    importResult = msg
                    if (ok) {
                        importText = ""
                    }
                }
            }
        )
    }
}

@Composable
private fun ImportDialog(
    text: String,
    result: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onImport: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Follows") },
        text = {
            Column {
                Text(
                    text = "Paste the JSON from your export",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    placeholder = { Text("[\"username1\", \"username2\", ...]") },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    )
                )
                if (result.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.startsWith("Imported"))
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onImport()
                },
                enabled = text.isNotBlank()
            ) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
