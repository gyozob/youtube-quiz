package com.personal.youtubequiz.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagStatusTest {

    @Test
    fun `tag needs three images and a question`() {
        assertTrue(TagStatus("animals", imageCount = 3, questionCount = 1).isPlayable)
        assertTrue(TagStatus("animals", imageCount = 9, questionCount = 4).isPlayable)
    }

    @Test
    fun `two images is not enough however many questions`() {
        val status = TagStatus("animals", imageCount = 2, questionCount = 5)
        assertFalse(status.isPlayable)
        assertNotNull(status.problem)
    }

    @Test
    fun `images without a question are not playable`() {
        val status = TagStatus("animals", imageCount = 10, questionCount = 0)
        assertFalse(status.isPlayable)
        assertNotNull(status.problem)
    }

    @Test
    fun `a playable tag reports no problem`() {
        assertNull(TagStatus("animals", imageCount = 4, questionCount = 2).problem)
    }
}
