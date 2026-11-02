package com.personal.youtubequiz.ui

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personal.youtubequiz.data.ImageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The image library: import from device storage and tag. */
@Composable
fun ImagesScreen(state: AppState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var importing by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ImageItem?>(null) }

    // The photo picker needs no storage permission and returns a readable URI,
    // which is copied into internal storage on import.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK),
    ) { uris ->
        Log.i("YTQuiz", "photo picker returned ${uris.size} uri(s)")
        if (uris.isNotEmpty()) pendingUris = uris
    }

    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                enabled = !importing,
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            ) { Text(if (importing) "Importing..." else "Add images") }

            Text(
                "${state.images.size} in library",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (state.images.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Import at least three images under one tag to make that tag usable.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.images, key = { it.id }) { image ->
                    Column {
                        Box {
                            Thumbnail(
                                path = image.thumbPath,
                                contentDescription = image.tag,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            IconButton(
                                onClick = { pendingDelete = image },
                                modifier = Modifier.align(Alignment.TopEnd),
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete image")
                            }
                        }
                        Text(image.tag, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (pendingUris.isNotEmpty()) {
        val uris = pendingUris
        TagPromptDialog(
            existingTags = state.tags,
            onDismiss = { pendingUris = emptyList() },
            onConfirm = { tag ->
                pendingUris = emptyList()
                importing = true
                scope.launch {
                    Log.i("YTQuiz", "importing ${uris.size} uri(s) under tag '$tag'")
                    // Decoding and re-encoding several photos is far too slow
                    // for the main thread.
                    val count = withContext(Dispatchers.IO) { state.importImages(uris, tag) }
                    Log.i("YTQuiz", "import finished: $count of ${uris.size} succeeded")
                    state.refresh()
                    importing = false
                    Toast.makeText(
                        context,
                        if (count == uris.size) "Imported $count images"
                        else "Imported $count of ${uris.size} images",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
    }

    pendingDelete?.let { image ->
        val dependents = state.questions.count { it.correctImageId == image.id }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this image?") },
            text = {
                Text(
                    if (dependents > 0) {
                        "$dependents question(s) use it as the answer and will be " +
                            "deleted too."
                    } else {
                        "This cannot be undone."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    state.deleteImage(image.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

private const val MAX_PICK = 30
