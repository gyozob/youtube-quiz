package com.personal.youtubequiz.ui

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Records a single clip to an .m4a file. Used for question prompts and for the
 * two feedback sounds.
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var target: File? = null

    val isRecording: Boolean get() = recorder != null

    fun start(file: File): Boolean {
        stop()
        return try {
            val created = newRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(BIT_RATE)
                setAudioSamplingRate(SAMPLE_RATE)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = created
            target = file
            true
        } catch (e: Exception) {
            Log.e(TAG, "failed to start recording", e)
            releaseQuietly()
            file.delete()
            false
        }
    }

    /** Stops recording and returns the finished file, or null if it failed. */
    fun stop(): File? {
        val active = recorder ?: return null
        val file = target
        recorder = null
        target = null

        return try {
            active.stop()
            active.release()
            file?.takeIf { it.exists() && it.length() > 0 }
        } catch (e: Exception) {
            // stop() throws if it is called before any audio was captured --
            // a very short tap on the record button does this.
            Log.w(TAG, "recording was too short to save", e)
            runCatching { active.release() }
            file?.delete()
            null
        }
    }

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    private fun releaseQuietly() {
        runCatching { recorder?.release() }
        recorder = null
        target = null
    }

    companion object {
        private const val TAG = "YTQuiz"
        private const val BIT_RATE = 96_000
        private const val SAMPLE_RATE = 44_100
    }
}
