package com.personal.youtubequiz.service

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import java.io.File

/**
 * Plays one clip at a time. Serialising matters because the wrong-answer sound
 * is immediately followed by a replay of the question, and overlapping those
 * two makes both unintelligible.
 */
class AudioPlayer {

    private var player: MediaPlayer? = null

    /**
     * Plays [path], then calls [onComplete]. A missing or unrecorded clip
     * completes immediately rather than stalling the caller's sequence -- the
     * feedback sounds are optional until the user records them.
     */
    fun play(path: String?, onComplete: (() -> Unit)? = null) {
        stop()

        if (path.isNullOrBlank() || !File(path).exists()) {
            Log.w(TAG, "nothing to play for path=$path")
            onComplete?.invoke()
            return
        }

        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(path)
                setOnCompletionListener {
                    Log.d(TAG, "finished $path")
                    onComplete?.invoke()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra for $path")
                    onComplete?.invoke()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "failed to play $path", e)
            onComplete?.invoke()
        }
    }

    fun stop() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
    }

    companion object {
        private const val TAG = "YTQuiz"
    }
}
