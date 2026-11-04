package com.rough.snoremonitor.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramCommandParserTest {
    private val authorizedChatId = "12345678"
    private val parser = TelegramCommandParser(authorizedChatId)

    @Test
    fun testParseAuthorizedListenCommand() {
        val json = """
            {
              "ok": true,
              "result": [
                {
                  "update_id": 1001,
                  "message": {
                    "chat": {"id": 12345678},
                    "text": "/listen"
                  }
                }
              ]
            }
        """.trimIndent()

        val commands = parser.parseUpdates(json)
        assertEquals(1, commands.size)
        assertEquals(1001L, commands[0].updateId)
        assertEquals(BotCommand.LISTEN, commands[0].command)
    }

    @Test
    fun testParseAuthorizedRecordCommand() {
        val json = """
            {
              "ok": true,
              "result": [
                {
                  "update_id": 1002,
                  "message": {
                    "chat": {"id": 12345678},
                    "text": "/record"
                  }
                }
              ]
            }
        """.trimIndent()

        val commands = parser.parseUpdates(json)
        assertEquals(1, commands.size)
        assertEquals(1002L, commands[0].updateId)
        assertEquals(BotCommand.LISTEN, commands[0].command)
    }

    @Test
    fun testIgnoreUnauthorizedChatId() {
        val json = """
            {
              "ok": true,
              "result": [
                {
                  "update_id": 1003,
                  "message": {
                    "chat": {"id": 99999999},
                    "text": "/listen"
                  }
                }
              ]
            }
        """.trimIndent()

        val commands = parser.parseUpdates(json)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun testParseStatusCommand() {
        val json = """
            {
              "ok": true,
              "result": [
                {
                  "update_id": 1004,
                  "message": {
                    "chat": {"id": 12345678},
                    "text": "/status"
                  }
                }
              ]
            }
        """.trimIndent()

        val commands = parser.parseUpdates(json)
        assertEquals(1, commands.size)
        assertEquals(BotCommand.STATUS, commands[0].command)
    }

    @Test
    fun testParseHelpAndStartCommand() {
        val json = """
            {
              "ok": true,
              "result": [
                {
                  "update_id": 1005,
                  "message": {
                    "chat": {"id": 12345678},
                    "text": "/help"
                  }
                },
                {
                  "update_id": 1006,
                  "message": {
                    "chat": {"id": 12345678},
                    "text": "/start"
                  }
                }
              ]
            }
        """.trimIndent()

        val commands = parser.parseUpdates(json)
        assertEquals(2, commands.size)
        assertEquals(BotCommand.HELP, commands[0].command)
        assertEquals(BotCommand.HELP, commands[1].command)
    }
}
