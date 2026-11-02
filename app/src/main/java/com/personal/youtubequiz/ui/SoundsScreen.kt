package com.personal.youtubequiz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The two feedback clips played when an answer is tapped. */
@Composable
fun SoundsScreen(state: AppState, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SectionCard("Good answer") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Played when the correct image is tapped, just before the " +
                            "overlay closes and the video resumes.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    AudioClipControls(
                        clipPath = state.correctSoundPath,
                        newFile = { state.repository.files.newAudioFile("correct") },
                        onRecorded = { state.setCorrectSound(it) },
                        onDelete = { state.setCorrectSound(null) },
                    )
                }
            }
        }

        item {
            SectionCard("Wrong answer") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Played when a wrong image is tapped. The question is then " +
                            "repeated and the overlay stays open.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    AudioClipControls(
                        clipPath = state.wrongSoundPath,
                        newFile = { state.repository.files.newAudioFile("wrong") },
                        onRecorded = { state.setWrongSound(it) },
                        onDelete = { state.setWrongSound(null) },
                    )
                }
            }
        }

        item {
            Text(
                "Both are optional. Without them the quiz still works, it just " +
                    "gives no audible feedback.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
