package com.personal.youtubequiz.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Log
import kotlin.random.Random

/**
 * Single access point to quiz content, shared by the settings UI and the
 * accessibility service (which run in the same process).
 */
class QuizRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val db by lazy { DbHelper(appContext).writableDatabase }

    val files = MediaFiles(appContext)

    // ---- images -----------------------------------------------------------

    fun addImage(tag: String, imagePath: String, thumbPath: String): Long {
        val values = ContentValues().apply {
            put("tag", tag.normalisedTag())
            put("image_path", imagePath)
            put("thumb_path", thumbPath)
        }
        return db.insert("images", null, values)
    }

    fun images(): List<ImageItem> =
        db.query("images", null, null, null, null, null, "tag ASC, id DESC")
            .mapAll { it.toImageItem() }

    fun imagesByTag(tag: String): List<ImageItem> =
        db.query(
            "images", null, "tag = ?", arrayOf(tag.normalisedTag()),
            null, null, "id DESC",
        ).mapAll { it.toImageItem() }

    /**
     * Removes the image, every question that depends on it, and all their
     * files. The foreign key cascades the rows; the files need doing by hand.
     */
    fun deleteImage(id: Long) {
        val image = image(id) ?: return
        questionsForImage(id).forEach { files.delete(it.audioPath) }
        db.delete("images", "id = ?", arrayOf(id.toString()))
        files.delete(image.imagePath)
        files.delete(image.thumbPath)
    }

    fun image(id: Long): ImageItem? =
        db.query("images", null, "id = ?", arrayOf(id.toString()), null, null, null)
            .mapAll { it.toImageItem() }
            .firstOrNull()

    // ---- questions --------------------------------------------------------

    fun addQuestion(tag: String, audioPath: String, correctImageId: Long): Long {
        val values = ContentValues().apply {
            put("tag", tag.normalisedTag())
            put("audio_path", audioPath)
            put("correct_image_id", correctImageId)
        }
        return db.insert("questions", null, values)
    }

    fun questions(): List<Question> =
        db.query("questions", null, null, null, null, null, "tag ASC, id DESC")
            .mapAll { it.toQuestion() }

    fun deleteQuestion(id: Long) {
        val question = questions().firstOrNull { it.id == id } ?: return
        db.delete("questions", "id = ?", arrayOf(id.toString()))
        files.delete(question.audioPath)
    }

    private fun questionsForImage(imageId: Long): List<Question> =
        db.query(
            "questions", null, "correct_image_id = ?", arrayOf(imageId.toString()),
            null, null, null,
        ).mapAll { it.toQuestion() }

    // ---- tags -------------------------------------------------------------

    fun tagStatuses(): List<TagStatus> {
        val images = images().groupingBy { it.tag }.eachCount()
        val questions = questions().groupingBy { it.tag }.eachCount()
        return (images.keys + questions.keys).sorted().map { tag ->
            TagStatus(tag, images[tag] ?: 0, questions[tag] ?: 0)
        }
    }

    // ---- round construction -----------------------------------------------

    /**
     * Picks a random question whose tag has enough images, then two distractors
     * from the same tag. Returns null when nothing is playable, which the
     * caller treats as "skip this interval" rather than an error.
     *
     * [avoidQuestionId] is a preference, not a filter: practice mode runs
     * rounds back to back and asking the same thing twice in a row feels
     * broken, but a library with a single question must still work.
     */
    fun buildRound(random: Random = Random.Default, avoidQuestionId: Long? = null): QuizRound? {
        val playableTags = tagStatuses().filter { it.isPlayable }.map { it.tag }.toSet()
        if (playableTags.isEmpty()) {
            Log.w(TAG, "no tag has a question and at least ${TagStatus.CHOICES_PER_ROUND} images")
            return null
        }

        val playable = questions().filter { it.tag in playableTags }
        val question = playable
            .filter { it.id != avoidQuestionId }
            .randomOrNull(random)
            ?: playable.randomOrNull(random)
            ?: return null

        return QuizRound.build(question, imagesByTag(question.tag), random)
            ?: run {
                Log.w(TAG, "question ${question.id} has no usable image set")
                null
            }
    }

    // ---- cursor plumbing --------------------------------------------------

    private fun <T> Cursor.mapAll(transform: (Cursor) -> T): List<T> = use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(transform(cursor))
        }
    }

    private fun Cursor.toImageItem() = ImageItem(
        id = getLong(getColumnIndexOrThrow("id")),
        tag = getString(getColumnIndexOrThrow("tag")),
        imagePath = getString(getColumnIndexOrThrow("image_path")),
        thumbPath = getString(getColumnIndexOrThrow("thumb_path")),
    )

    private fun Cursor.toQuestion() = Question(
        id = getLong(getColumnIndexOrThrow("id")),
        tag = getString(getColumnIndexOrThrow("tag")),
        audioPath = getString(getColumnIndexOrThrow("audio_path")),
        correctImageId = getLong(getColumnIndexOrThrow("correct_image_id")),
    )

    companion object {
        private const val TAG = "YTQuiz"

        /** Tags are matched exactly, so normalise once on the way in. */
        fun String.normalisedTag(): String = trim().lowercase()

        @Volatile
        private var instance: QuizRepository? = null

        fun get(context: Context): QuizRepository =
            instance ?: synchronized(this) {
                instance ?: QuizRepository(context).also { instance = it }
            }
    }
}
