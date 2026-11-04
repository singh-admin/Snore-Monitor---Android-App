package com.rough.snoremonitor.dispatch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class TelegramDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        // Direct resolution for api.telegram.org bypasses ISP/carrier DNS blocking and IPv6 hangs in India
        if (hostname.equals("api.telegram.org", ignoreCase = true)) {
            return listOf(
                InetAddress.getByAddress("api.telegram.org", byteArrayOf(149.toByte(), 154.toByte(), 166.toByte(), 110.toByte())),
                InetAddress.getByAddress("api.telegram.org", byteArrayOf(149.toByte(), 154.toByte(), 167.toByte(), 220.toByte()))
            )
        }
        return Dns.SYSTEM.lookup(hostname)
    }
}

class TelegramDispatcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .dns(TelegramDns())
        .build()
) {
    private fun cleanToken(rawToken: String): String {
        return rawToken.trim().removePrefix("bot").trim()
    }

    private fun cleanChatId(rawChatId: String): String {
        return rawChatId.trim()
    }

    suspend fun getUpdates(
        botToken: String,
        offset: Long,
        timeoutSeconds: Int = 20
    ): Result<String> = withContext(Dispatchers.IO) {
        val token = cleanToken(botToken)
        if (token.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Bot token must not be blank"))
        }

        val url = "https://api.telegram.org/bot$token/getUpdates?offset=$offset&timeout=$timeoutSeconds"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "SnoreMonitorApp/1.0")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Result.success(bodyString)
                } else {
                    Result.failure(IOException("Telegram API error ${response.code}: $bodyString"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendTextMessage(
        botToken: String,
        chatId: String,
        text: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val token = cleanToken(botToken)
        val chat = cleanChatId(chatId)

        if (token.isBlank() || chat.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Bot token and Chat ID must not be blank"))
        }

        val url = "https://api.telegram.org/bot$token/sendMessage"

        val formBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chat)
            .addFormDataPart("text", text)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "SnoreMonitorApp/1.0")
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

    suspend fun sendVoiceSnippet(
        botToken: String,
        chatId: String,
        audioFile: File,
        caption: String = "Snoring detected downstairs. Audio snippet attached."
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val token = cleanToken(botToken)
        val chat = cleanChatId(chatId)

        if (token.isBlank() || chat.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Bot token and Chat ID must not be blank"))
        }

        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("Audio file does not exist or is empty"))
        }

        val url = "https://api.telegram.org/bot$token/sendVoice"
        val audioBody = audioFile.asRequestBody("audio/mp4".toMediaTypeOrNull())

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chat)
            .addFormDataPart("caption", caption)
            .addFormDataPart("voice", audioFile.name, audioBody)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "SnoreMonitorApp/1.0")
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

    suspend fun sendTestMessage(
        botToken: String,
        chatId: String
    ): Result<Boolean> = sendTextMessage(
        botToken = botToken,
        chatId = chatId,
        text = "✅ Snore Monitor connection test successful! Ready to receive audio snippets."
    )
}
