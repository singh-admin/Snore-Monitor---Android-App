package com.rough.snoremonitor.recorder

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SnippetRecorder(private val context: Context) {

    /**
     * Records a clean AAC audio snippet of [durationMs] into an .m4a file.
     * Uses MediaRecorder configured for voice/speech recording.
     */
    suspend fun recordSnippet(durationMs: Long = 15_000L): Result<File> = withContext(Dispatchers.IO) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val snippetFile = File(context.cacheDir, "snore_snippet_$timeStamp.m4a")

        var recorder: MediaRecorder? = null

        try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(32000)
                setOutputFile(snippetFile.absolutePath)
                prepare()
                start()
            }

            // Record for the specified duration
            delay(durationMs)

            try {
                recorder.stop()
            } catch (e: Exception) {
                // In case stop is called too early
            }
            recorder.release()
            recorder = null

            if (snippetFile.exists() && snippetFile.length() > 0) {
                Result.success(snippetFile)
            } else {
                Result.failure(IllegalStateException("Recorded snippet file is empty or missing"))
            }
        } catch (e: Exception) {
            try {
                recorder?.release()
            } catch (_: Exception) {}
            Result.failure(e)
        }
    }
}
