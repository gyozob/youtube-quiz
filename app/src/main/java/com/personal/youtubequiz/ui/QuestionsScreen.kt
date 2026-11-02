package com.personal.youtubequiz.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.personal.youtubequiz.data.Question
import com.personal.youtubequiz.data.TagStatus
import com.personal.youtubequiz.service.AudioPlayer

/** Record questions and bind each to the image that answers it. */
@Composable
fun QuestionsScreen(state: AppState, modifier: Modifier = Modifier) {
    val player = remember { AudioPlayer() }
    var composing by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Question?>(null) }

    DisposableEffect(Unit) { onDispose { player.stop() } }

    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                enabled = state.images.isNotEmpty(),
                onClick = { composing = true },
            ) { Text("New question") }

            Text(
                if (state.images.isEmpty()) "Import images first"
                else "${state.questions.size} recorded",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.questions, key = { it.id }) { question ->
                val answer = state.imageById(question.correctImageId)
                Card {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Thumbnail(
                            path = answer?.thumbPath,
                            contentDescription = "Correct answer",
                            modifier = Modifier.size(64.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(question.tag, style = MaterialTheme.typography.titleSmall)
                            Text(
                                answer?.let { "Answer set" } ?: "Answer image is missing",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(onClick = { player.play(question.audioPath) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play question")
                        }
                        IconButton(onClick = { pendingDelete = question }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete question")
                        }
                    }
                }
            }
        }
    }

    if (composing) {
        NewQuestionDialog(
            state = state,
            onDismiss = { composing = false },
            onSaved = { composing = false },
        )
    }

    pendingDelete?.let { question ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this question?") },
            text = { Text("The recording will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    state.deleteQuestion(question.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun NewQuestionDialog(
    state: AppState,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    var tag by remember { mutableStateOf(state.tags.firstOrNull()) }
    var audioPath by remember { mutableStateOf<String?>(null) }
    var correctImageId by remember { mutableStateOf<Long?>(null) }

    val candidates = tag?.let { state.imagesForTag(it) }.orEmpty()
    val canSave = tag != null && audioPath != null && correctImageId != null

    AlertDialog(
        onDismissRequest = {
            // Discard a recording the user is abandoning, so it does not
            // linger on disk unreferenced.
            audioPath?.let { state.repository.files.delete(it) }
            onDismiss()
        },
        title = { Text("New question") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("1. Choose a tag", style = MaterialTheme.typography.titleSmall)
                TagSelector(
                    tags = state.tags,
                    selected = tag,
                    onSelect = {
                        tag = it
                        correctImageId = null
                    },
                )
                if (tag != null && candidates.size < TagStatus.CHOICES_PER_ROUND) {
                    Text(
                        "This tag has ${candidates.size} images. A round needs " +
                            "${TagStatus.CHOICES_PER_ROUND}, so it will be skipped until " +
                            "you add more.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE65100),
                    )
                }

                Text("2. Record the question", style = MaterialTheme.typography.titleSmall)
                AudioClipControls(
                    clipPath = audioPath,
                    newFile = { state.repository.files.newAudioFile("question") },
                    onRecorded = { path ->
                        audioPath?.let { state.repository.files.delete(it) }
                        audioPath = path
                    },
                    onDelete = {
                        audioPath?.let { state.repository.files.delete(it) }
                        audioPath = null
                    },
                )

                Text("3. Tap the correct answer", style = MaterialTheme.typography.titleSmall)
                if (candidates.isEmpty()) {
                    Text("No images with this tag yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(72.dp),
                        modifier = Modifier.heightIn(max = 220.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(candidates, key = { it.id }) { image ->
                            val selected = correctImageId == image.id
                            Box(
                                Modifier
                                    .clickable { correctImageId = image.id }
                                    .border(
                                        width = if (selected) 3.dp else 0.dp,
                                        color = if (selected) Color(0xFF1E88E5) else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp),
                                    )
                            ) {
                                Thumbnail(
                                    path = image.thumbPath,
                                    contentDescription = image.tag,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    state.addQuestion(tag!!, audioPath!!, correctImageId!!)
                    onSaved()
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = {
                audioPath?.let { state.repository.files.delete(it) }
                onDismiss()
            }) { Text("Cancel") }
        },
    )
}
