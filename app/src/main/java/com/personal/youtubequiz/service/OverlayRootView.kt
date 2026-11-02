package com.personal.youtubequiz.service

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.FrameLayout

/**
 * Root of the overlay window.
 *
 * The window is focusable so it can take key events away from YouTube, which
 * also means Back arrives here. The requirement is that the popup stays until
 * the question is answered, so Back is swallowed rather than dismissing it --
 * the long-press escape hatch is the way out.
 */
class OverlayRootView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return true
        return super.dispatchKeyEvent(event)
    }
}
