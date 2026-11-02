package com.personal.youtubequiz.service

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.personal.youtubequiz.data.Prefs
import com.personal.youtubequiz.data.QuizRepository
import com.personal.youtubequiz.data.QuizRound

/**
 * Detects when YouTube is in the foreground and runs the interval timer.
 *
 * The timer deliberately lives here rather than in a separate foreground
 * service: an accessibility service is already kept alive by the system, so
 * this avoids a permanent notification and the foreground-service-type rules
 * that apply from Android 14 onwards.
 *
 * Only the package name carried on window-state changes is read. No screen
 * content is retrieved -- see res/xml/accessibility_service_config.xml.
 */
class YouTubeWatchService : AccessibilityService() {

    private lateinit var repository: QuizRepository
    private lateinit var prefs: Prefs
    private lateinit var overlay: OverlayController
    private lateinit var mediaControl: MediaControl
    private val audioPlayer = AudioPlayer()

    private val handler = Handler(Looper.getMainLooper())
    private val intervalElapsed = Runnable { onIntervalElapsed() }

    /** Watch time is accumulated across visits, so leaving briefly does not reset progress. */
    private var inYouTube = false
    private var segmentStartMs = 0L
    private var watchedMs = 0L

    private var round: QuizRound? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = QuizRepository.get(this)
        prefs = Prefs(this)
        overlay = OverlayController(this)
        mediaControl = MediaControl(this)
        instance = this
        Log.i(TAG, "watcher connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Our own overlay and settings screen raise window changes too. Treating
        // those as "the user left YouTube" would cancel the quiz the instant it
        // appeared, so they are ignored entirely.
        if (pkg == packageName) return

        if (pkg == YOUTUBE_PACKAGE) enterYouTube() else leaveYouTube()
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay.onConfigurationChanged()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.i(TAG, "watcher disconnected")
        handler.removeCallbacksAndMessages(null)
        audioPlayer.stop()
        overlay.hide()
        mediaControl.release()
        instance = null
        return super.onUnbind(intent)
    }

    // ---- foreground tracking ----------------------------------------------

    private fun enterYouTube() {
        if (inYouTube) return
        inYouTube = true
        segmentStartMs = SystemClock.elapsedRealtime()
        Log.d(TAG, "YouTube in foreground (watched so far: ${watchedMs / 1000}s)")
        armTimer()
    }

    private fun leaveYouTube() {
        if (!inYouTube) return
        inYouTube = false
        watchedMs += SystemClock.elapsedRealtime() - segmentStartMs
        handler.removeCallbacks(intervalElapsed)
        Log.d(TAG, "left YouTube (watched so far: ${watchedMs / 1000}s)")

        if (overlay.isShowing) {
            // The user navigated away mid-question. Tear down, but do not send a
            // play command -- whatever they moved to should not start playing.
            audioPlayer.stop()
            overlay.hide()
            mediaControl.release()
            resetInterval(arm = false)
        }
    }

    // ---- interval ----------------------------------------------------------

    private fun armTimer() {
        handler.removeCallbacks(intervalElapsed)
        if (!prefs.enabled || !inYouTube || overlay.isShowing) return

        val remaining = (prefs.intervalSeconds * 1000L - watchedMs)
            .coerceAtLeast(MIN_DELAY_MS)
        Log.d(TAG, "next quiz in ${remaining / 1000}s")
        handler.postDelayed(intervalElapsed, remaining)
    }

    private fun resetInterval(arm: Boolean = true) {
        watchedMs = 0L
        segmentStartMs = SystemClock.elapsedRealtime()
        if (arm) armTimer()
    }

    private fun onIntervalElapsed() {
        if (!prefs.enabled || !inYouTube || overlay.isShowing) {
            resetInterval()
            return
        }

        val next = repository.buildRound()
        if (next == null) {
            // No usable content yet. Wait another interval rather than spinning.
            Log.w(TAG, "interval elapsed but no playable question is available")
            resetInterval()
            return
        }

        round = next
        mediaControl.pause(prefs.pauseStrategy)

        if (!overlay.show(next, overlayListener)) {
            mediaControl.release()
            resetInterval()
            return
        }
        audioPlayer.play(next.question.audioPath)
    }

    // ---- quiz resolution ---------------------------------------------------

    private val overlayListener = object : OverlayController.Listener {

        override fun onCorrect() {
            val current = round ?: return
            Log.i(TAG, "correct answer for question ${current.question.id}")
            overlay.setAcceptingTaps(false)
            // Requirement order: feedback sound, then dismiss, then resume.
            audioPlayer.play(prefs.correctSoundPath) { finishRound(resumePlayback = true) }
        }

        override fun onWrong() {
            val current = round ?: return
            Log.i(TAG, "wrong answer for question ${current.question.id}")
            overlay.setAcceptingTaps(false)
            audioPlayer.play(prefs.wrongSoundPath) {
                audioPlayer.play(current.question.audioPath) {
                    overlay.setAcceptingTaps(true)
                }
            }
        }

        override fun onReplay() {
            val current = round ?: return
            overlay.setAcceptingTaps(false)
            audioPlayer.play(current.question.audioPath) { overlay.setAcceptingTaps(true) }
        }

        override fun onForceDismiss() {
            finishRound(resumePlayback = true)
        }
    }

    private fun finishRound(resumePlayback: Boolean) {
        audioPlayer.stop()
        overlay.hide()
        round = null
        if (resumePlayback) mediaControl.resume(prefs.pauseStrategy) else mediaControl.release()
        resetInterval()
    }

    // ---- diagnostics hooks -------------------------------------------------
    // Used by the Diagnostics section of the settings UI to exercise pause,
    // resume and the overlay without waiting out an interval. Same process,
    // so a plain reference is enough.

    fun diagnosticPause() = mediaControl.pause(prefs.pauseStrategy)

    fun diagnosticResume() = mediaControl.resume(prefs.pauseStrategy)

    fun diagnosticIsMediaPlaying() = mediaControl.isMediaPlaying

    /** Shows a real round immediately. Returns false if there is nothing to show. */
    fun diagnosticShowOverlay(): Boolean {
        val next = repository.buildRound() ?: return false
        round = next
        if (!overlay.show(next, overlayListener)) return false
        audioPlayer.play(next.question.audioPath)
        return true
    }

    companion object {
        private const val TAG = "YTQuiz"
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"

        /** Guards against a zero or negative delay when the interval is shortened. */
        private const val MIN_DELAY_MS = 1_000L

        @Volatile
        var instance: YouTubeWatchService? = null
            private set

        val isRunning: Boolean get() = instance != null
    }
}
