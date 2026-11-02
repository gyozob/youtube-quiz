package com.personal.youtubequiz.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.personal.youtubequiz.data.PauseStrategy

/**
 * Stops and restarts whatever is playing in the foreground -- in practice
 * YouTube.
 *
 * Two independent levers are available and neither is guaranteed on its own:
 *
 *  - Transient audio focus. Well-behaved players pause and resume around it,
 *    but a player may duck instead of pausing, and auto-resume is a courtesy
 *    rather than a contract.
 *  - Media key events. [AudioManager.dispatchMediaKeyEvent] needs no
 *    permission and reaches whichever media session is active, but it does
 *    nothing if the app has not published a session.
 *
 * [PauseStrategy.BOTH] uses focus first and falls back to the key only if
 * playback is still running, so the two cannot fight each other.
 */
class MediaControl(context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var focusRequest: AudioFocusRequest? = null

    val isMediaPlaying: Boolean get() = audioManager.isMusicActive

    fun pause(strategy: PauseStrategy) {
        // Drop any pending verification from a previous call: a question
        // answered inside the verify window would otherwise be followed by a
        // stale pause or play key.
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "pause via $strategy (music active: ${audioManager.isMusicActive})")
        if (strategy.usesAudioFocus) requestFocus()

        if (strategy == PauseStrategy.MEDIA_KEY) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        } else if (strategy.usesMediaKey) {
            handler.postDelayed({
                if (audioManager.isMusicActive) {
                    Log.d(TAG, "focus did not stop playback; sending pause key")
                    sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
                }
            }, VERIFY_DELAY_MS)
        }
    }

    fun resume(strategy: PauseStrategy) {
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "resume via $strategy")
        if (strategy.usesAudioFocus) abandonFocus()

        if (strategy == PauseStrategy.MEDIA_KEY) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        } else if (strategy.usesMediaKey) {
            handler.postDelayed({
                if (!audioManager.isMusicActive) {
                    Log.d(TAG, "playback did not come back on its own; sending play key")
                    sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                }
            }, VERIFY_DELAY_MS)
        }
    }

    /**
     * Gives up audio focus without asking anything to start playing. Used when
     * the user leaves YouTube mid-quiz -- resuming would be wrong there.
     */
    fun release() {
        handler.removeCallbacksAndMessages(null)
        abandonFocus()
    }

    private fun requestFocus() {
        if (focusRequest != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            // Ask to be paused rather than ducked, so the question is audible.
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener { change ->
                Log.d(TAG, "our own audio focus changed: $change")
            }
            .build()
        focusRequest = request
        val result = audioManager.requestAudioFocus(request)
        Log.d(TAG, "requestAudioFocus -> $result")
    }

    private fun abandonFocus() {
        focusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
            Log.d(TAG, "abandoned audio focus")
        }
        focusRequest = null
    }

    /** A media key is only honoured as a matched down/up pair. */
    private fun sendMediaKey(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    companion object {
        private const val TAG = "YTQuiz"

        /** Long enough for a player to react to the focus change before we second-guess it. */
        private const val VERIFY_DELAY_MS = 400L
    }
}
