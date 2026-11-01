package com.zachvlat.instakitty.ui.following

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    var usernameToRemove by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isExporting by viewModel.isExporting.collectAsState()

    var pendingExport by remember { mutableStateOf<String?>(null) }

    val createExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val payload = pendingExport
        pendingExport = null
        if (uri != null && payload != null) {
            scope.launch {
                val saved = viewModel.writeExport(uri, payload)
                Toast.makeText(
                    context,
                    if (saved) "Export saved" else "Could not save export",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val openImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFromUri(uri) { ok, msg ->
                importResult = msg
                if (ok) {
                    importText = ""
                    showImportDialog = false
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Following",
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    if (isExporting) return@OutlinedButton
                    scope.launch {
                        val payload = viewModel.buildExportJson()
                        if (payload == null) {
                            Toast.makeText(context, "Export failed", Toast.LENGTH_LONG).show()
                        } else {
                            pendingExport = payload
                            val stamp = SimpleDateFormat(
                                "yyyyMMdd-HHmmss",
                                Locale.US
                            ).format(Date())
                            createExportLauncher.launch("instakitty-export-$stamp.json")
                        }
                    }
                },
                enabled = !isExporting,
                modifier = Modifier.weight(1f)
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
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
                                    modifier = Modifier
                                        .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val avatar = state.avatars[username]
                                    if (avatar != null) {
                                        AsyncImage(
                                            model = java.io.File(avatar),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
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
                                    }

                                    Spacer(Modifier.width(12.dp))

                                    Text(
                                        text = username,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )

                                    IconButton(
                                        onClick = { usernameToRemove = username },
                                        enabled = username !in removingUsers
                                    ) {
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
            onPickFile = {
                openImportLauncher.launch(
                    arrayOf("application/json", "text/plain", "application/octet-stream")
                )
            },
            onImport = {
                viewModel.importJson(importText) { ok, msg ->
                    importResult = msg
                    if (ok) {
                        importText = ""
                        showImportDialog = false
                    }
                }
            }
        )
    }

    val usernameToRemoveLocal = usernameToRemove
    if (usernameToRemoveLocal != null) {
        val isRemoving = usernameToRemoveLocal in removingUsers
        RemoveFollowDialog(
            username = usernameToRemoveLocal,
            isRemoving = isRemoving,
            onConfirm = {
                viewModel.removeUser(usernameToRemoveLocal)
                usernameToRemove = null
            },
            onDismiss = { usernameToRemove = null }
        )
    }
}

@Composable
private fun ImportDialog(
    text: String,
    result: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onPickFile: () -> Unit,
    onImport: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Follows") },
        text = {
            Column {
                Text(
                    text = "Choose an InstaKitty export file, or paste JSON below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onPickFile,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Load from file")
                }
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

@Composable
private fun RemoveFollowDialog(
    username: String,
    isRemoving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val noOp: () -> Unit = { }
    val dismissRequest = if (isRemoving) noOp else onDismiss
    AlertDialog(
        onDismissRequest = dismissRequest,
        title = { Text("Remove following") },
        text = {
            if (isRemoving) {
                Column(Modifier.padding(top = 8.dp, bottom = 8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Removing @$username…",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text("Are you sure you want to stop following @$username?")
            }
        },
        confirmButton = {
            if (!isRemoving) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Remove") }
            }
        },
        dismissButton = {
            if (!isRemoving) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}