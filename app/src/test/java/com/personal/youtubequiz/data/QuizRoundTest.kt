package com.personal.youtubequiz.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuizRoundTest {

    private fun image(id: Long, tag: String = "animals") =
        ImageItem(id = id, tag = tag, imagePath = "/img/$id.jpg", thumbPath = "/thumb/$id.jpg")

    private fun question(correctImageId: Long, tag: String = "animals") =
        Question(id = 1, tag = tag, audioPath = "/audio/1.m4a", correctImageId = correctImageId)

    @Test
    fun `round has exactly three distinct choices including the correct one`() {
        val pool = (1L..6L).map { image(it) }
        val round = QuizRound.build(question(correctImageId = 4), pool, Random(0))

        assertNotNull(round)
        assertEquals(TagStatus.CHOICES_PER_ROUND, round!!.choices.size)
        assertEquals(round.choices.size, round.choices.map { it.id }.toSet().size)
        assertTrue(round.choices.any { it.id == 4L })
    }

    @Test
    fun `distractors are never the correct image`() {
        val pool = (1L..5L).map { image(it) }
        repeat(50) { seed ->
            val round = QuizRound.build(question(correctImageId = 2), pool, Random(seed))!!
            assertEquals(1, round.choices.count { it.id == 2L })
        }
    }

    @Test
    fun `exactly three images in the pool still produces a round`() {
        val pool = listOf(image(1), image(2), image(3))
        val round = QuizRound.build(question(correctImageId = 3), pool, Random(7))

        assertNotNull(round)
        assertEquals(setOf(1L, 2L, 3L), round!!.choices.map { it.id }.toSet())
    }

    @Test
    fun `fewer than three images yields no round`() {
        val pool = listOf(image(1), image(2))
        assertNull(QuizRound.build(question(correctImageId = 1), pool, Random(0)))
    }

    @Test
    fun `missing correct image yields no round`() {
        val pool = (1L..5L).map { image(it) }
        assertNull(QuizRound.build(question(correctImageId = 99), pool, Random(0)))
    }

    @Test
    fun `choice order varies across rounds`() {
        val pool = (1L..6L).map { image(it) }
        val orders = (0..30).map { seed ->
            QuizRound.build(question(correctImageId = 1), pool, Random(seed))!!
                .choices.map { it.id }
        }.toSet()

        // The correct answer must not sit in a predictable slot.
        assertTrue("choices were never reordered", orders.size > 1)
    }
}
