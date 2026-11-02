package com.personal.youtubequiz.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.personal.youtubequiz.data.ImageItem
import com.personal.youtubequiz.data.PauseStrategy
import com.personal.youtubequiz.data.Prefs
import com.personal.youtubequiz.data.Question
import com.personal.youtubequiz.data.QuizRepository
import com.personal.youtubequiz.data.TagStatus

/**
 * Observable wrapper over the repository and preferences for the settings UI.
 *
 * The data set is a handful of rows, so it is simply re-read after every
 * change rather than observed -- a reactive query layer would be more
 * machinery than this earns.
 */
class AppState(context: Context) {

    private val appContext = context.applicationContext
    val repository = QuizRepository.get(appContext)
    val prefs = Prefs(appContext)

    var images by mutableStateOf<List<ImageItem>>(emptyList())
        private set
    var questions by mutableStateOf<List<Question>>(emptyList())
        private set
    var tagStatuses by mutableStateOf<List<TagStatus>>(emptyList())
        private set

    var enabled by mutableStateOf(prefs.enabled)
        private set
    var intervalSeconds by mutableIntStateOf(prefs.intervalSeconds)
        private set
    var pauseStrategy by mutableStateOf(prefs.pauseStrategy)
        private set
    var correctSoundPath by mutableStateOf(prefs.correctSoundPath)
        private set
    var wrongSoundPath by mutableStateOf(prefs.wrongSoundPath)
        private set

    val tags: List<String> get() = tagStatuses.map { it.tag }

    init {
        refresh()
    }

    fun refresh() {
        images = repository.images()
        questions = repository.questions()
        tagStatuses = repository.tagStatuses()
    }

    // ---- settings ----------------------------------------------------------

    fun updateEnabled(value: Boolean) {
        prefs.enabled = value
        enabled = value
    }

    fun updateIntervalSeconds(value: Int) {
        prefs.intervalSeconds = value
        intervalSeconds = prefs.intervalSeconds
    }

    fun updatePauseStrategy(value: PauseStrategy) {
        prefs.pauseStrategy = value
        pauseStrategy = value
    }

    // ---- content -----------------------------------------------------------

    /** Imports [uris] under one tag. Blocking: callers run it off the main thread. */
    fun importImages(uris: List<Uri>, tag: String): Int {
        var imported = 0
        uris.forEach { uri ->
            val paths = repository.files.importImage(uri) ?: return@forEach
            repository.addImage(tag, paths.first, paths.second)
            imported++
        }
        return imported
    }

    fun deleteImage(id: Long) {
        repository.deleteImage(id)
        refresh()
    }

    fun addQuestion(tag: String, audioPath: String, correctImageId: Long) {
        repository.addQuestion(tag, audioPath, correctImageId)
        refresh()
    }

    fun deleteQuestion(id: Long) {
        repository.deleteQuestion(id)
        refresh()
    }

    fun imagesForTag(tag: String): List<ImageItem> = images.filter { it.tag == tag }

    fun imageById(id: Long): ImageItem? = images.firstOrNull { it.id == id }

    // ---- feedback sounds ---------------------------------------------------

    fun setCorrectSound(path: String?) {
        prefs.correctSoundPath?.takeIf { it != path }?.let { repository.files.delete(it) }
        prefs.correctSoundPath = path
        correctSoundPath = path
    }

    fun setWrongSound(path: String?) {
        prefs.wrongSoundPath?.takeIf { it != path }?.let { repository.files.delete(it) }
        prefs.wrongSoundPath = path
        wrongSoundPath = path
    }

    /** True when at least one tag can actually produce a round. */
    val hasPlayableContent: Boolean get() = tagStatuses.any { it.isPlayable }
}
