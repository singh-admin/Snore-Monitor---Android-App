package com.rough.snoremonitor.remote

import org.json.JSONObject

enum class BotCommand {
    LISTEN,
    STATUS,
    HELP,
    UNKNOWN
}

data class ValidatedCommand(
    val updateId: Long,
    val chatId: String,
    val command: BotCommand,
    val rawText: String
)

class TelegramCommandParser(private val authorizedChatId: String) {

    private val cleanedAuthorizedChatId = authorizedChatId.trim()

    fun parseUpdates(jsonResponse: String): List<ValidatedCommand> {
        val commands = mutableListOf<ValidatedCommand>()
        if (cleanedAuthorizedChatId.isBlank() || jsonResponse.isBlank()) {
            return commands
        }

        try {
            val root = JSONObject(jsonResponse)
            if (!root.optBoolean("ok", false)) {
                return commands
            }

            val resultArray = root.optJSONArray("result") ?: return commands
            for (i in 0 until resultArray.length()) {
                val updateObj = resultArray.getJSONObject(i)
                val updateId = updateObj.getLong("update_id")

                val messageObj = updateObj.optJSONObject("message")
                    ?: updateObj.optJSONObject("channel_post")
                    ?: continue

                val chatObj = messageObj.optJSONObject("chat") ?: continue
                val incomingChatId = chatObj.optLong("id", Long.MIN_VALUE).toString()

                // Security filter: strictly ignore any chat_id that does not match authorizedChatId
                if (incomingChatId != cleanedAuthorizedChatId) {
                    continue
                }

                val text = messageObj.optString("text", "").trim()
                if (text.isBlank()) {
                    continue
                }

                val command = parseCommand(text)
                commands.add(ValidatedCommand(updateId, incomingChatId, command, text))
            }
        } catch (_: Exception) {
            // Malformed JSON is safely handled
        }

        return commands
    }

    private fun parseCommand(text: String): BotCommand {
        val lower = text.lowercase()
        val firstToken = lower.split("\\s+".toRegex()).firstOrNull() ?: ""

        return when {
            firstToken == "/listen" || firstToken.startsWith("/listen@") -> BotCommand.LISTEN
            firstToken == "/record" || firstToken.startsWith("/record@") -> BotCommand.LISTEN
            firstToken == "/status" || firstToken.startsWith("/status@") -> BotCommand.STATUS
            firstToken == "/help" || firstToken.startsWith("/help@") -> BotCommand.HELP
            firstToken == "/start" || firstToken.startsWith("/start@") -> BotCommand.HELP
            else -> BotCommand.UNKNOWN
        }
    }
}
