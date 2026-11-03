package com.rough.snoremonitor.dispatch

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TelegramDispatcherTest {

    @Test
    fun testRejectsBlankCredentials() {
        runBlocking {
            val dispatcher = TelegramDispatcher()
            val dummyFile = File.createTempFile("test_audio", ".m4a")

            val result = dispatcher.sendVoiceSnippet("", "", dummyFile)
            assertTrue("Should fail with blank credentials", result.isFailure)

            dummyFile.delete()
        }
    }

    @Test
    fun testRejectsNonExistentFile() {
        runBlocking {
            val dispatcher = TelegramDispatcher()
            val missingFile = File("non_existent_file.m4a")

            val result = dispatcher.sendVoiceSnippet("dummy_token", "12345", missingFile)
            assertTrue("Should fail with missing file", result.isFailure)
        }
    }
}
