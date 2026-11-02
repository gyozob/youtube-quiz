package com.personal.youtubequiz.data

/** An image in the library, always tagged so it can be matched to questions. */
data class ImageItem(
    val id: Long,
    val tag: String,
    val imagePath: String,
    val thumbPath: String,
)

/** A voice-recorded question and the image that answers it. */
data class Question(
    val id: Long,
    val tag: String,
    val audioPath: String,
    val correctImageId: Long,
)

/**
 * Content readiness for one tag. A round needs three same-tag images (one
 * correct, two distractors), so a tag with fewer is unusable no matter how
 * many questions it has.
 */
data class TagStatus(
    val tag: String,
    val imageCount: Int,
    val questionCount: Int,
) {
    val isPlayable: Boolean get() = imageCount >= CHOICES_PER_ROUND && questionCount > 0

    val problem: String?
        get() = when {
            imageCount < CHOICES_PER_ROUND ->
                "needs $CHOICES_PER_ROUND images, has $imageCount"
            questionCount == 0 -> "no questions recorded"
            else -> null
        }

    companion object {
        const val CHOICES_PER_ROUND = 3
    }
}

/** One quiz presentation: a question plus the three shuffled image choices. */
data class QuizRound(
    val question: Question,
    val choices: List<ImageItem>,
) {
    val correctImageId: Long get() = question.correctImageId

    companion object {
        /**
         * Assembles a round from [question] and every image sharing its tag.
         *
         * Kept free of database and Android types so the selection rules --
         * exactly three choices, the correct one always present, no repeats --
         * can be tested directly. Returns null when [pool] cannot satisfy them.
         */
        fun build(
            question: Question,
            pool: List<ImageItem>,
            random: kotlin.random.Random = kotlin.random.Random.Default,
        ): QuizRound? {
            val correct = pool.firstOrNull { it.id == question.correctImageId } ?: return null

            val distractors = pool
                .filter { it.id != correct.id }
                .shuffled(random)
                .take(TagStatus.CHOICES_PER_ROUND - 1)
            if (distractors.size < TagStatus.CHOICES_PER_ROUND - 1) return null

            return QuizRound(question, (distractors + correct).shuffled(random))
        }
    }
}
