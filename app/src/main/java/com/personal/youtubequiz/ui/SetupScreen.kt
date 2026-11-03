package com.personal.youtubequiz.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.personal.youtubequiz.data.PauseStrategy
import com.personal.youtubequiz.data.Prefs
import com.personal.youtubequiz.service.YouTubeWatchService

/**
 * Landing screen: permission status, the master controls, content readiness
 * and the diagnostics used to work out how YouTube reacts to pausing.
 */
@Composable
fun SetupScreen(state: AppState, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Every permission below is granted in system Settings, outside this app,
    // so the checks are re-run whenever the user returns -- and on demand via
    // the Re-check button. Bumping this counter is what forces that.
    var refreshToken by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshToken++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val overlayGranted = remember(refreshToken) { Permissions.canDrawOverlays(context) }
    val accessibilityGranted =
        remember(refreshToken) { Permissions.isAccessibilityServiceEnabled(context) }
    val accessibilityRunning = remember(refreshToken) { YouTubeWatchService.isRunning }
    val micGranted = remember(refreshToken) { Permissions.hasMicrophone(context) }
    val batteryUnrestricted = remember(refreshToken) { Permissions.isBatteryUnrestricted(context) }
    val youTubeInstalled = remember(refreshToken) { Permissions.isYouTubeInstalled(context) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SectionCard("Permissions") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PermissionRow(
                        label = "Display over other apps",
                        detail = "Required to draw the quiz on top of YouTube.",
                        granted = overlayGranted,
                        onFix = { context.openSettings(Permissions.overlaySettings(context)) },
                    )
                    PermissionRow(
                        label = "Accessibility service",
                        detail = if (accessibilityRunning) {
                            "Enabled and running."
                        } else {
                            "Detects when YouTube is in the foreground."
                        },
                        granted = accessibilityGranted,
                        onFix = { context.openSettings(Permissions.accessibilitySettings()) },
                    )
                    PermissionRow(
                        label = "Microphone",
                        detail = "Needed to record questions and feedback sounds.",
                        granted = micGranted,
                        onFix = { context.openSettings(Permissions.appDetails(context)) },
                    )
                    PermissionRow(
                        label = "Unrestricted battery",
                        detail = "Stops the system freezing the timer in the background.",
                        granted = batteryUnrestricted,
                        onFix = { context.openSettings(Permissions.batterySettings()) },
                    )
                    PermissionRow(
                        label = "YouTube installed",
                        detail = "The app watches for com.google.android.youtube.",
                        granted = youTubeInstalled,
                        onFix = null,
                    )

                    Text(
                        "Sideloaded on Android 13+? If the accessibility toggle is greyed " +
                            "out, open the app info page, tap the three-dot menu and choose " +
                            "\"Allow restricted settings\".",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = { context.openSettings(Permissions.appDetails(context)) },
                    ) { Text("Open app info") }
                    OutlinedButton(onClick = { refreshToken++ }) { Text("Re-check") }
                }
            }
        }

        item {
            SectionCard("Interruptions") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Quiz while watching YouTube")
                            Text(
                                if (state.enabled) "On" else "Off",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = state.enabled,
                            onCheckedChange = { state.updateEnabled(it) },
                        )
                    }

                    Text("Ask a question every ${formatInterval(state.intervalSeconds)}")
                    Slider(
                        value = state.intervalSeconds.toFloat(),
                        onValueChange = { state.updateIntervalSeconds(it.toInt()) },
                        valueRange = Prefs.MIN_INTERVAL_SECONDS.toFloat()..
                            Prefs.MAX_INTERVAL_SECONDS.toFloat(),
                        // One stop per 10 seconds, per the frequency requirement.
                        steps = (Prefs.MAX_INTERVAL_SECONDS - Prefs.MIN_INTERVAL_SECONDS) /
                            Prefs.INTERVAL_STEP_SECONDS - 1,
                    )
                    Text(
                        "Counts YouTube watch time only, and carries over if you " +
                            "briefly switch away.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item {
            SectionCard("Content") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state.tagStatuses.isEmpty()) {
                        Text("No images or questions yet.")
                    } else {
                        state.tagStatuses.forEach { status ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusIcon(status.isPlayable)
                                Column(Modifier.padding(start = 8.dp)) {
                                    Text(status.tag)
                                    Text(
                                        status.problem
                                            ?: "${status.imageCount} images, " +
                                            "${status.questionCount} questions",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                    if (state.correctSoundPath == null || state.wrongSoundPath == null) {
                        Text(
                            "Feedback sounds are not recorded yet. The quiz still works; " +
                                "it just stays silent on an answer.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        item {
            SectionCard("Diagnostics") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "YouTube does not promise to pause for an audio-focus request, " +
                            "so try each strategy against a playing video and keep the " +
                            "one that works.",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    PauseStrategy.entries.forEach { strategy ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = state.pauseStrategy == strategy,
                                    onClick = { state.updatePauseStrategy(strategy) },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.pauseStrategy == strategy,
                                onClick = { state.updatePauseStrategy(strategy) },
                            )
                            Column(Modifier.padding(start = 4.dp)) {
                                Text(strategy.label)
                                Text(
                                    strategy.description,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val service = YouTubeWatchService.instance
                            if (service == null) {
                                context.toast("Accessibility service is not running")
                            } else {
                                val before = service.diagnosticIsMediaPlaying()
                                service.diagnosticPause()
                                context.toast(
                                    if (before) "Pause sent (media was playing)"
                                    else "Pause sent, but nothing was playing"
                                )
                            }
                        }) { Text("Pause") }

                        OutlinedButton(onClick = {
                            val service = YouTubeWatchService.instance
                            if (service == null) {
                                context.toast("Accessibility service is not running")
                            } else {
                                service.diagnosticResume()
                                context.toast("Resume sent")
                            }
                        }) { Text("Resume") }
                    }

                    Button(onClick = {
                        val service = YouTubeWatchService.instance
                        when {
                            service == null ->
                                context.toast("Accessibility service is not running")
                            !Permissions.canDrawOverlays(context) ->
                                context.toast("Grant \"Display over other apps\" first")
                            !service.diagnosticShowOverlay() ->
                                context.toast("No tag has a question and 3 images yet")
                            else -> Unit
                        }
                    }) { Text("Show a quiz now") }

                    Button(onClick = {
                        val service = YouTubeWatchService.instance
                        when {
                            service == null ->
                                context.toast("Accessibility service is not running")
                            !Permissions.canDrawOverlays(context) ->
                                context.toast("Grant \"Display over other apps\" first")
                            !service.diagnosticStartPractice() ->
                                context.toast("No tag has a question and 3 images yet")
                            else -> Unit
                        }
                    }) { Text("Practice mode") }

                    Text(
                        "Practice keeps asking new questions after each correct " +
                            "answer. Tap the X in the top-left corner to stop.",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Text(
                        "Logs: adb logcat -s YTQuiz:V",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    detail: String,
    granted: Boolean,
    onFix: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusIcon(granted)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 8.dp),
        ) {
            Text(label)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        if (!granted && onFix != null) {
            OutlinedButton(onClick = onFix) { Text("Grant") }
        }
    }
}

@Composable
private fun StatusIcon(ok: Boolean) {
    Icon(
        imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
        contentDescription = if (ok) "ready" else "action needed",
        tint = if (ok) Color(0xFF2E7D32) else Color(0xFFE65100),
    )
}

private fun formatInterval(seconds: Int): String = when {
    seconds < 60 -> "$seconds seconds"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60} min ${seconds % 60} s"
}

private fun Context.openSettings(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { toast("Could not open that settings page") }
}

private fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
