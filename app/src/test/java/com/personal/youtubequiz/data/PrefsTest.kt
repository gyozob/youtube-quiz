package com.personal.youtubequiz.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefsTest {

    @Test
    fun `interval snaps to the nearest ten seconds`() {
        assertEquals(30, Prefs.snapInterval(32))
        assertEquals(30, Prefs.snapInterval(27))
        assertEquals(120, Prefs.snapInterval(123))
        assertEquals(130, Prefs.snapInterval(125))
    }

    @Test
    fun `interval is clamped to the supported range`() {
        assertEquals(Prefs.MIN_INTERVAL_SECONDS, Prefs.snapInterval(0))
        assertEquals(Prefs.MIN_INTERVAL_SECONDS, Prefs.snapInterval(-50))
        assertEquals(Prefs.MAX_INTERVAL_SECONDS, Prefs.snapInterval(99_999))
    }

    @Test
    fun `every snapped value is a whole number of steps`() {
        (Prefs.MIN_INTERVAL_SECONDS..Prefs.MAX_INTERVAL_SECONDS).forEach { seconds ->
            val snapped = Prefs.snapInterval(seconds)
            assertEquals(
                "snapped value $snapped is not a multiple of the step",
                0,
                snapped % Prefs.INTERVAL_STEP_SECONDS,
            )
            assertTrue(snapped in Prefs.MIN_INTERVAL_SECONDS..Prefs.MAX_INTERVAL_SECONDS)
        }
    }

    @Test
    fun `pause strategies cover the two levers`() {
        assertTrue(PauseStrategy.BOTH.usesAudioFocus && PauseStrategy.BOTH.usesMediaKey)
        assertTrue(PauseStrategy.AUDIO_FOCUS.usesAudioFocus)
        assertTrue(!PauseStrategy.AUDIO_FOCUS.usesMediaKey)
        assertTrue(PauseStrategy.MEDIA_KEY.usesMediaKey)
        assertTrue(!PauseStrategy.MEDIA_KEY.usesAudioFocus)
    }
}
