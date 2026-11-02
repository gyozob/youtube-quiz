package com.personal.youtubequiz.data

import android.content.Context
import androidx.core.content.edit

/**
 * How playback is stopped and restarted.
 *
 * YouTube's reaction to a transient audio-focus request is not contractual --
 * some builds pause, some duck -- and whether it auto-resumes when focus is
 * abandoned varies too. Rather than guess, all three approaches ship and the
 * working one is chosen from the Diagnostics section.
 */
enum class PauseStrategy(val label: String, val description: String) {
    BOTH(
        "Audio focus + media key",
        "Request transient audio focus, then send a media key if playback did not stop. Try this first.",
    ),
    AUDIO_FOCUS(
        "Audio focus only",
        "Rely on YouTube pausing when it loses audio focus, and resuming when it is returned.",
    ),
    MEDIA_KEY(
        "Media key only",
        "Send pause/play key events to the active media session, like a headset button.",
    );

    val usesAudioFocus: Boolean get() = this == BOTH || this == AUDIO_FOCUS
    val usesMediaKey: Boolean get() = this == BOTH || this == MEDIA_KEY
}

/** User configuration. Small and flat, so SharedPreferences is the right size of tool. */
class Prefs(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("quiz_prefs", Context.MODE_PRIVATE)

    /** Master on/off switch for the whole interruption behaviour. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    /** How much YouTube watch time passes between quizzes. Always a multiple of 10. */
    var intervalSeconds: Int
        get() = prefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL_SECONDS)
        set(value) {
            val snapped = snapInterval(value)
            prefs.edit { putInt(KEY_INTERVAL, snapped) }
        }

    var correctSoundPath: String?
        get() = prefs.getString(KEY_CORRECT_SOUND, null)
        set(value) = prefs.edit { putString(KEY_CORRECT_SOUND, value) }

    var wrongSoundPath: String?
        get() = prefs.getString(KEY_WRONG_SOUND, null)
        set(value) = prefs.edit { putString(KEY_WRONG_SOUND, value) }

    var pauseStrategy: PauseStrategy
        get() = runCatching {
            PauseStrategy.valueOf(prefs.getString(KEY_STRATEGY, null) ?: PauseStrategy.BOTH.name)
        }.getOrDefault(PauseStrategy.BOTH)
        set(value) = prefs.edit { putString(KEY_STRATEGY, value.name) }

    companion object {
        const val MIN_INTERVAL_SECONDS = 10
        const val MAX_INTERVAL_SECONDS = 600
        const val INTERVAL_STEP_SECONDS = 10
        const val DEFAULT_INTERVAL_SECONDS = 120

        /** Rounds to the nearest 10 seconds and clamps, per the frequency requirement. */
        fun snapInterval(seconds: Int): Int {
            val stepped =
                (Math.round(seconds.toFloat() / INTERVAL_STEP_SECONDS)) * INTERVAL_STEP_SECONDS
            return stepped.coerceIn(MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS)
        }

        private const val KEY_ENABLED = "enabled"
        private const val KEY_INTERVAL = "interval_seconds"
        private const val KEY_CORRECT_SOUND = "correct_sound"
        private const val KEY_WRONG_SOUND = "wrong_sound"
        private const val KEY_STRATEGY = "pause_strategy"
    }
}
