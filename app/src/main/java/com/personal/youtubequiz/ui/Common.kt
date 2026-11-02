package com.personal.youtubequiz.ui

import android.Manifest
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personal.youtubequiz.service.AudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Decodes an image file off the main thread, recomposing when it is ready. */
@Composable
fun rememberImageBitmap(path: String?): ImageBitmap? {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            path?.takeIf { File(it).exists() }
                ?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
        }
    }
    return bitmap
}

@Composable
fun Thumbnail(
    path: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val bitmap = rememberImageBitmap(path)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Asks for the tag to file freshly picked images under. */
@Composable
fun TagPromptDialog(
    existingTags: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var tag by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tag these images") },
        text = {
            Column {
                Text(
                    "Questions are matched to images by tag, and a round needs " +
                        "three images sharing one tag.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text("Tag") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
                if (existingTags.isNotEmpty()) {
                    Text(
                        "Existing: " + existingTags.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = tag.isNotBlank(),
                onClick = { onConfirm(tag.trim()) },
            ) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Simple dropdown over the known tags, with an option to type a new one. */
@Composable
fun TagSelector(
    tags: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected ?: "Choose a tag")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            tags.forEach { tag ->
                DropdownMenuItem(
                    text = { Text(tag) },
                    onClick = {
                        onSelect(tag)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Record / stop / play controls over a single audio clip.
 *
 * [onRecorded] receives the path of a newly captured file. The caller owns the
 * file from that point, including deleting any clip it replaces.
 */
@Composable
fun AudioClipControls(
    clipPath: String?,
    newFile: () -> File,
    onRecorded: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val recorder = remember { AudioRecorder(context) }
    val player = remember { AudioPlayer() }
    var recording by remember { mutableStateOf(false) }
    var micGranted by remember { mutableStateOf(Permissions.hasMicrophone(context)) }

    DisposableEffect(Unit) {
        onDispose {
            if (recorder.isRecording) recorder.stop()
            player.stop()
        }
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> micGranted = granted }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = {
                if (!micGranted) {
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    return@OutlinedButton
                }
                if (recording) {
                    val file = recorder.stop()
                    recording = false
                    if (file != null) onRecorded(file.absolutePath)
                } else {
                    player.stop()
                    recording = recorder.start(newFile())
                }
            },
        ) {
            Icon(
                imageVector = if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = null,
            )
            Text(
                text = when {
                    recording -> "Stop"
                    clipPath != null -> "Re-record"
                    else -> "Record"
                },
                modifier = Modifier.padding(start = 6.dp),
            )
        }

        if (clipPath != null && !recording) {
            OutlinedButton(onClick = { player.play(clipPath) }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text("Play", modifier = Modifier.padding(start = 6.dp))
            }
            if (onDelete != null) {
                OutlinedButton(onClick = {
                    player.stop()
                    onDelete()
                }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete recording")
                }
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Box(Modifier.padding(top = 10.dp)) { content() }
        }
    }
}
