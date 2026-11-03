package com.rough.snoremonitor.dispatch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class TelegramDispatcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    /**
     * Sends an audio snippet file to a Telegram chat via Telegram Bot API's `sendVoice` endpoint.
     * This presents a native voice message player in the user's Telegram app with audio waveform.
     */
    suspend fun sendVoiceSnippet(
        botToken: String,
        chatId: String,
        audioFile: File,
        caption: String = "Snoring detected downstairs. Audio snippet attached."
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (botToken.isBlank() || chatId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Bot token and Chat ID must not be blank"))
        }

        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("Audio file does not exist or is empty"))
        }

        val url = "https://api.telegram.org/bot$botToken/sendVoice"

        val audioBody = audioFile.asRequestBody("audio/mp4".toMediaTypeOrNull())

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("caption", caption)
            .addFormDataPart("voice", audioFile.name, audioBody)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    Result.failure(IOException("Telegram API error ${response.code}: $errorBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends a quick test text message to verify bot token and chat ID.
     */
    suspend fun sendTestMessage(
        botToken: String,
        chatId: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (botToken.isBlank() || chatId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Bot token and Chat ID must not be blank"))
        }

        val url = "https://api.telegram.org/bot$botToken/sendMessage"

        val formBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("text", "✅ Snore Monitor connection test successful! Ready to receive audio snippets.")
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    Result.failure(IOException("Telegram API error ${response.code}: $errorBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
