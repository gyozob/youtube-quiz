package com.personal.youtubequiz.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import com.personal.youtubequiz.R
import com.personal.youtubequiz.data.QuizRound
import java.io.File

/**
 * Owns the overlay window: adding it to the [WindowManager], laying out the
 * three answer cards, and reporting taps back to the service.
 */
class OverlayController(private val context: Context) {

    interface Listener {
        fun onCorrect()
        fun onWrong()

        /** Practice mode only: the visible exit button in the top-left corner. */
        fun onExit()

        /** Long-press escape hatch -- see [OverlayRootView]. */
        fun onForceDismiss()
    }

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val themedContext = ContextThemeWrapper(context, R.style.Theme_YouTubeQuiz_Overlay)
    private val handler = Handler(Looper.getMainLooper())

    private var root: OverlayRootView? = null
    private var choices: LinearLayout? = null
    private var round: QuizRound? = null
    private var listener: Listener? = null
    private var acceptingTaps = true

    val isShowing: Boolean get() = root != null

    /**
     * Adds the window and draws [round]. [showExit] reveals the practice-mode
     * exit button; without it the only ways out are a correct answer and the
     * long-press hatch.
     */
    fun show(round: QuizRound, listener: Listener, showExit: Boolean = false): Boolean {
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "cannot show overlay: Display over other apps is not granted")
            return false
        }
        if (isShowing) hide()

        this.round = round
        this.listener = listener
        this.acceptingTaps = true

        // A null root is correct here: this view goes straight to the
        // WindowManager, so there is no parent to resolve layout params against.
        @SuppressLint("InflateParams")
        val view = LayoutInflater.from(themedContext)
            .inflate(R.layout.overlay_quiz, null) as OverlayRootView

        val exit = view.findViewById<View>(R.id.overlay_exit)
        exit.visibility = if (showExit) View.VISIBLE else View.GONE
        exit.setOnClickListener { listener.onExit() }

        attachEscapeHatch(view.findViewById(R.id.overlay_escape), listener)

        choices = view.findViewById(R.id.overlay_choices)
        root = view

        return try {
            windowManager.addView(view, buildLayoutParams())
            renderChoices()
            Log.i(TAG, "overlay shown for question " + round.question.id + " tag=" + round.question.tag)
            true
        } catch (e: Exception) {
            Log.e(TAG, "failed to add overlay window", e)
            root = null
            choices = null
            false
        }
    }

    /**
     * Swaps in the next round without touching the window. Practice mode runs
     * question after question, and removing the window between them would
     * flash whatever is behind the overlay on every answer.
     */
    fun showNext(round: QuizRound) {
        if (!isShowing) return
        this.round = round
        this.acceptingTaps = true
        renderChoices()
        Log.i(
            TAG,
            "overlay advanced to question " + round.question.id + " tag=" + round.question.tag,
        )
    }

    fun hide() {
        handler.removeCallbacksAndMessages(null)
        root?.let { view ->
            releaseImages()
            runCatching { windowManager.removeView(view) }
                .onFailure { Log.w(TAG, "overlay was already detached", it) }
        }
        root = null
        choices = null
        round = null
        listener = null
    }

    /**
     * Stops the cards responding while a feedback sound plays, so repeated taps
     * cannot stack several clips on top of each other.
     */
    fun setAcceptingTaps(accepting: Boolean) {
        acceptingTaps = accepting
    }

    /**
     * Returns the board to its untouched state. Kept separate from
     * [setAcceptingTaps] because the two happen at different moments after a
     * wrong guess: the highlight goes as soon as the question starts repeating,
     * while taps stay blocked until the clip has finished.
     */
    fun clearSelection() {
        val container = choices ?: return
        for (i in 0 until container.childCount) {
            val card = container.getChildAt(i)
            card.setBackgroundResource(R.drawable.card_background)
            card.alpha = 1f
        }
    }

    /** The window survives rotation, but the card row has to become a column. */
    fun onConfigurationChanged() {
        if (isShowing) renderChoices()
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        // Not setting FLAG_NOT_FOCUSABLE: the window must take key input so the
        // Back button cannot be used to skip the question.
        val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Fullscreen video runs edge to edge; so should the overlay.
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun renderChoices() {
        val container = choices ?: return
        val current = round ?: return
        val currentListener = listener ?: return

        releaseImages()
        container.removeAllViews()

        val landscape =
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        container.orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL

        current.choices.forEach { choice ->
            val card = ImageView(themedContext).apply {
                layoutParams = LinearLayout.LayoutParams(
                    if (landscape) 0 else LinearLayout.LayoutParams.MATCH_PARENT,
                    if (landscape) LinearLayout.LayoutParams.MATCH_PARENT else 0,
                    1f,
                ).apply {
                    val margin = dp(CARD_MARGIN_DP)
                    setMargins(margin, margin, margin, margin)
                }
                setBackgroundResource(R.drawable.card_background)
                val padding = dp(CARD_PADDING_DP)
                setPadding(padding, padding, padding, padding)
                scaleType = ImageView.ScaleType.FIT_CENTER
                isClickable = true
                isFocusable = true
                contentDescription = choice.tag
                setImageBitmap(decode(choice.imagePath))
                setOnClickListener { tapped ->
                    if (!acceptingTaps) return@setOnClickListener
                    markSelected(tapped)
                    if (choice.id == current.correctImageId) {
                        currentListener.onCorrect()
                    } else {
                        currentListener.onWrong()
                    }
                }
            }
            container.addView(card)
        }
    }

    /**
     * Rings the tapped card and fades the others. The answer is reported back
     * by sound alone, which says nothing about *which* picture was hit, so the
     * selection has to be obvious on its own.
     */
    private fun markSelected(selected: View) {
        val container = choices ?: return
        for (i in 0 until container.childCount) {
            val card = container.getChildAt(i)
            if (card === selected) {
                card.setBackgroundResource(R.drawable.card_background_selected)
                card.alpha = 1f
            } else {
                card.alpha = UNSELECTED_ALPHA
            }
        }
    }

    private fun attachEscapeHatch(target: View, listener: Listener) {
        // A plain long-press is far too easy to trigger by accident on a view
        // covering a screen corner, so require a deliberate hold.
        val fire = Runnable {
            Log.w(TAG, "overlay force-dismissed via escape hatch")
            listener.onForceDismiss()
        }
        target.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> handler.postDelayed(fire, ESCAPE_HOLD_MS)
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(fire)
                    view.performClick()
                }
                MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(fire)
            }
            true
        }
    }

    private fun decode(path: String) =
        if (File(path).exists()) BitmapFactory.decodeFile(path) else null

    private fun releaseImages() {
        val container = choices ?: return
        for (i in 0 until container.childCount) {
            (container.getChildAt(i) as? ImageView)?.setImageDrawable(null)
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "YTQuiz"
        private const val CARD_MARGIN_DP = 8
        private const val CARD_PADDING_DP = 6
        private const val ESCAPE_HOLD_MS = 3_000L
        private const val UNSELECTED_ALPHA = 0.3f
    }
}
