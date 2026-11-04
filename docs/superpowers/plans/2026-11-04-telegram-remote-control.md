# Telegram Remote Control Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable the upstairs user to remotely command the downstairs phone via Telegram bot commands (`/listen`, `/status`, `/help`) to immediately record and receive a 15-second ambient audio clip without having to physically access the downstairs phone.

**Architecture:** A dedicated foreground service (`TelegramRemoteService`) runs a long-polling coroutine using Telegram's `getUpdates` API. When an authorized message is received from the configured `chat_id`, the service routes the command: `/listen` captures a 15-second AAC snippet using `SnippetRecorder` and sends it back via `TelegramDispatcher.sendVoiceSnippet`; `/status` queries system battery and monitoring states and replies with a formatted message; unauthorized messages are dropped.

**Tech Stack:** Kotlin, Android Jetpack Compose, OkHttp, Coroutines, MediaRecorder, Android Service.

**Spec:** [`docs/superpowers/specs/2026-11-04-telegram-remote-control-design.md`](file:///Users/singh/project/Rough/docs/superpowers/specs/2026-11-04-telegram-remote-control-design.md)

## Global Constraints

- Android API levels: `minSdk = 26`, `targetSdk = 33`, `compileSdk = 35`.
- Privacy & Security: Only messages where `from.id == prefs.chatId` or `chat.id == prefs.chatId` may be processed. Unauthorized requests must be dropped.
- Direct DNS resolution: Re-use `TelegramDns` to bypass carrier DNS poisoning / IPv6 hangs on `api.telegram.org`.
- No microphone hogging: The microphone must only be acquired during the 15-second `/listen` recording window, not held continuously.

## Review Focus

1. Unauthorized command sender: A message from an unexpected `chat_id` must be completely ignored and not trigger audio recording.
2. Rapid repeated `/listen` commands: If `/listen` is sent multiple times in rapid succession, a mutex must prevent overlapping `MediaRecorder` initializations.
3. Network connection timeout during long poll: A network glitch or timeout must not crash the service; it should back off and resume polling.
4. Clean file cleanup: Recorded `.m4a` cache files must be deleted immediately after Telegram upload completes.
5. Service auto-start: If the remote control toggle was enabled, restarting the app or device should restore the service.

---

### Task 1: Command Parser & Security Filter (`TelegramCommandParser`)

**Files:**
- Create: `app/src/main/java/com/rough/snoremonitor/remote/TelegramCommandParser.kt`
- Test: `app/src/test/java/com/rough/snoremonitor/remote/TelegramCommandParserTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  enum class BotCommand { LISTEN, STATUS, HELP, UNKNOWN }
  data class ValidatedCommand(val updateId: Long, val chatId: String, val command: BotCommand)
  class TelegramCommandParser(private val authorizedChatId: String) {
      fun parseUpdates(jsonResponse: String): List<ValidatedCommand>
  }
  ```

- [ ] **Step 1: Write failing unit tests for command parsing and security filtering**

```kotlin
// app/src/test/java/com/rough/snoremonitor/remote/TelegramCommandParserTest.kt
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
    fun testIgnoreUnauthorizedChatId() {
        val json = """
            {
              "ok": true,
              "result": [
                {
                  "update_id": 1002,
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
                  "update_id": 1003,
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests com.rough.snoremonitor.remote.TelegramCommandParserTest`  
Expected: FAIL (class does not exist).

- [ ] **Step 3: Implement `TelegramCommandParser` in `app/src/main/java/com/rough/snoremonitor/remote/TelegramCommandParser.kt`**

Implement JSON parsing using `org.json.JSONObject`. Verify incoming `chat.id` matches `authorizedChatId`. Route commands: `/listen`, `/record` -> `LISTEN`; `/status` -> `STATUS`; `/help`, `/start` -> `HELP`; others -> `UNKNOWN`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests com.rough.snoremonitor.remote.TelegramCommandParserTest`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/rough/snoremonitor/remote/TelegramCommandParser.kt app/src/test/java/com/rough/snoremonitor/remote/TelegramCommandParserTest.kt
git commit -m "feat(remote): add TelegramCommandParser with security chat filtering"
```

---

### Task 2: Telegram Polling & Messaging in `TelegramDispatcher`

**Files:**
- Modify: `app/src/main/java/com/rough/snoremonitor/dispatch/TelegramDispatcher.kt`

**Interfaces:**
- Produces:
  ```kotlin
  suspend fun getUpdates(botToken: String, offset: Long, timeoutSeconds: Int = 20): Result<String>
  suspend fun sendTextMessage(botToken: String, chatId: String, text: String): Result<Boolean>
  ```

- [ ] **Step 1: Add `getUpdates` and `sendTextMessage` methods to `TelegramDispatcher.kt`**

- `getUpdates(botToken, offset, timeoutSeconds)`: executes an HTTP GET to `https://api.telegram.org/bot$token/getUpdates?offset=$offset&timeout=$timeoutSeconds`.
- `sendTextMessage(botToken, chatId, text)`: executes an HTTP POST to `https://api.telegram.org/bot$token/sendMessage` sending `chat_id` and `text`.
- Reuses `TelegramDns()` and OkHttpClient timeouts configured appropriately for long-polling (read timeout >= 35s).

- [ ] **Step 2: Verify compilation and tests**

Run: `./gradlew testDebugUnitTest`  
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rough/snoremonitor/dispatch/TelegramDispatcher.kt
git commit -m "feat(dispatch): add getUpdates and sendTextMessage to TelegramDispatcher"
```

---

### Task 3: Device Status Provider (`DeviceStatusHelper`)

**Files:**
- Create: `app/src/main/java/com/rough/snoremonitor/remote/DeviceStatusHelper.kt`

**Interfaces:**
- Produces:
  ```kotlin
  object DeviceStatusHelper {
      fun getStatusReport(context: Context): String
  }
  ```

- [ ] **Step 1: Implement `DeviceStatusHelper.kt`**

Queries `BatteryManager` via `context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))`:
- Battery level percentage
- Charging status (Charging via AC/USB vs On Battery)
- Snore detection state (`SnoreDetectionService.isServiceRunning`)
- Formats response cleanly with emojis (e.g., `📱 Downstairs Monitor Online\n🔋 Battery: 88% (Charging)\n🌙 Snore Detection: Inactive\n⏰ Scheduled: 11:00 PM IST`).

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/rough/snoremonitor/remote/DeviceStatusHelper.kt
git commit -m "feat(remote): add DeviceStatusHelper for remote battery and app diagnostics"
```

---

### Task 4: Preferences & Service Notification for Remote Service

**Files:**
- Modify: `app/src/main/java/com/rough/snoremonitor/data/AppPreferences.kt`
- Modify: `app/src/main/java/com/rough/snoremonitor/service/ServiceNotificationManager.kt`

**Interfaces:**
- Produces:
  ```kotlin
  // In AppPreferences
  var isRemoteControlEnabled: Boolean
  var lastTelegramUpdateId: Long

  // In ServiceNotificationManager
  const val REMOTE_NOTIFICATION_ID = 1002
  const val REMOTE_CHANNEL_ID = "snore_remote_control_channel"
  fun buildRemoteNotification(context: Context, statusText: String): Notification
  ```

- [ ] **Step 1: Update `AppPreferences.kt`** with `isRemoteControlEnabled` (default `false`) and `lastTelegramUpdateId` (default `0L`).
- [ ] **Step 2: Update `ServiceNotificationManager.kt`** with channel and notification builder for the remote control service.
- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rough/snoremonitor/data/AppPreferences.kt app/src/main/java/com/rough/snoremonitor/service/ServiceNotificationManager.kt
git commit -m "feat(prefs): add remote control persistence and notification channel"
```

---

### Task 5: Background Telegram Remote Service (`TelegramRemoteService`)

**Files:**
- Create: `app/src/main/java/com/rough/snoremonitor/service/TelegramRemoteService.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/rough/snoremonitor/receiver/BootReceiver.kt`

**Interfaces:**
- Produces:
  ```kotlin
  class TelegramRemoteService : Service() {
      companion object {
          const val ACTION_START = "com.rough.snoremonitor.START_REMOTE"
          const val ACTION_STOP = "com.rough.snoremonitor.STOP_REMOTE"
          var isServiceRunning = false
              private set
      }
  }
  ```

- [ ] **Step 1: Create `TelegramRemoteService.kt`**
  - Runs in a coroutine scope on `Dispatchers.IO`.
  - Promotes to foreground using `ServiceNotificationManager.buildRemoteNotification`.
  - Maintains `wakeLock` with safety timeout.
  - Loops `isActive`:
    - Calls `dispatcher.getUpdates(prefs.botToken, prefs.lastTelegramUpdateId + 1)`.
    - Parses commands using `TelegramCommandParser(prefs.chatId)`.
    - For each validated command:
      - Updates `prefs.lastTelegramUpdateId = max(prefs.lastTelegramUpdateId, cmd.updateId)`.
      - If `LISTEN`:
        - Sends immediate acknowledgement: `dispatcher.sendTextMessage(..., "🎙️ Recording 15-second snippet...")`
        - Records snippet via `SnippetRecorder(this).recordSnippet(15000L)`
        - Sends voice message via `dispatcher.sendVoiceSnippet(..., audioFile, "🎙️ On-demand room audio requested from upstairs.")`
        - Cleans up snippet file.
      - If `STATUS`:
        - Sends `DeviceStatusHelper.getStatusReport(this)` via `sendTextMessage`.
      - If `HELP` or `UNKNOWN`:
        - Sends help text explaining `/listen` and `/status`.
    - Catches network exceptions and adds a 3-second delay before retry.

- [ ] **Step 2: Register in `AndroidManifest.xml`**
  - Add `<service android:name=".service.TelegramRemoteService" android:enabled="true" android:exported="false" android:foregroundServiceType="microphone" />`.

- [ ] **Step 3: Update `BootReceiver.kt`**
  - If `prefs.isRemoteControlEnabled`, start `TelegramRemoteService` on device boot.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/rough/snoremonitor/service/TelegramRemoteService.kt app/src/main/AndroidManifest.xml app/src/main/java/com/rough/snoremonitor/receiver/BootReceiver.kt
git commit -m "feat(service): implement TelegramRemoteService with long-polling command router"
```

---

### Task 6: UI Dashboard Updates (`MainScreen.kt`)

**Files:**
- Modify: `app/src/main/java/com/rough/snoremonitor/ui/MainScreen.kt`

- [ ] **Step 1: Add Remote Control Card in `MainScreen.kt`**
  - Display switch or button: "Enable Remote Telegram Control" (bound to `prefs.isRemoteControlEnabled`).
  - When enabled: starts `TelegramRemoteService`, displays badge: "🟢 Ready: Send /listen from upstairs anytime".
  - When disabled: stops `TelegramRemoteService`, displays badge: "⚪ Disabled".
  - Shows quick instructions:
    - "From Telegram upstairs, send:
       • `/listen` - Capture 15s audio clip right now
       • `/status` - Check phone battery & connection"

- [ ] **Step 2: Build and verify Compose UI compiles cleanly**

Run: `./gradlew compileDebugKotlin`  
Expected: PASS with 0 errors.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rough/snoremonitor/ui/MainScreen.kt
git commit -m "feat(ui): add Telegram remote control card and service triggers"
```

---

### Task 7: Full Verification, Test Execution & APK Packaging

**Files:**
- Output: `snore-monitor.apk` (root directory and `app/build/outputs/apk/debug/app-debug.apk`)

- [ ] **Step 1: Run all unit tests**

Run: `./gradlew testDebugUnitTest`  
Expected: PASS with 100% tests passing.

- [ ] **Step 2: Build release/debug APK**

Run: `./gradlew assembleDebug`  
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Copy updated APK to project root**

Run: `cp app/build/outputs/apk/debug/app-debug.apk snore-monitor.apk`

- [ ] **Step 4: Commit and finalize**

```bash
git add snore-monitor.apk
git commit -m "build: package updated snore-monitor.apk with Telegram remote control"
```
