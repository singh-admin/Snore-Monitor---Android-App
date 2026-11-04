# Design Document: On-Demand Remote Control via Telegram

**Date:** 2026-11-04  
**Status:** Under Review (Builder Check)  
**Target Platform:** Android (Android 10 - Android 14, API 29-34)  
**Author:** Antigravity Pair Programming Agent  

---

## 1. Intent and Why

The user stays upstairs while their brother sleeps downstairs. Walking downstairs to physically tap "Start Listening" on the brother's phone is impractical and disrupts sleep. 

The user wants to control the snore monitor remotely from their own phone via Telegram. By sending simple bot commands from upstairs (such as `/listen` or `/status`), the downstairs phone should instantly capture an audio snippet of the downstairs room and transmit it directly to the user's Telegram chat, enabling immediate acoustic verification without requiring physical access to the downstairs device.

---

## 2. Goals, Non-Goals, and Anti-Goals

### Goals
- **On-Demand Remote Audio Capture (`/listen` or `/record`):** When the user sends `/listen` or `/record` from their Telegram app upstairs, the downstairs phone immediately records a 15-second ambient audio clip and transmits it as a Telegram voice message.
- **Remote Device Status (`/status`):** When requested, the downstairs phone replies with its current status: battery level, charging state, network connectivity, and current monitoring state.
- **Strict Authorization & Privacy:** The bot must strictly verify that the incoming update originates from the configured `chat_id`. Any messages from unauthorized users are silently ignored.
- **Reliable Long Polling (`getUpdates`):** Implements standard Telegram Bot API long polling (`getUpdates?offset=X&timeout=30`) running in a persistent background/foreground service so commands arrive within 1–2 seconds.
- **Coexistence with Local Features:** Seamlessly works alongside the existing 11:00 PM nightly schedule and manual listening features.
- **UI Control:** Add a simple toggle switch on `MainScreen` to enable or disable the Remote Control Listener, with status indicators (e.g., "Remote Control Active - Polling for commands").

### Non-Goals
- **Telegram Webhook Architecture:** Webhooks require a public static IP, domain name, and HTTPS certificate. Long polling (`getUpdates`) is zero-configuration and works reliably behind carrier NAT and residential Wi-Fi.
- **Continuous Microphone Streaming:** Does not keep an open two-way VoIP call; it captures and delivers clean 15-second audio snippets on demand to conserve battery and avoid continuous audio streaming.
- **Remote Settings Reconfiguration:** Bot will not accept raw preference reconfiguration over chat in phase 1, keeping the command set lean and bulletproof.

### Anti-Goals
- **Unauthorized Eavesdropping:** The app must never respond to commands from any Telegram account other than the authorized user's configured `chat_id`.

---

## 3. Platform Constraints & Permissions

1. **Background Network & Execution:**
   - To keep long polling responsive while the downstairs phone is locked with screen off, a `ForegroundService` with `PARTIAL_WAKE_LOCK` or a low-power maintenance loop is required.
   - Reuses existing permissions: `INTERNET`, `RECORD_AUDIO`, `WAKE_LOCK`, `FOREGROUND_SERVICE`.
2. **Audio Concurrency:**
   - If the phone is currently running continuous snore detection when a `/listen` command is received, the service must temporarily release the `AudioRecord` instance, allow `SnippetRecorder` to record the 15-second snippet, and then resume listening.
3. **Telegram Network Reliability in India:**
   - Reuses `TelegramDns` to bypass local ISP DNS blocking and IPv6 hangs on `api.telegram.org`.

---

## 4. Approaches Considered & Tradeoffs

| Approach | Description | Pros | Cons / Selected? |
|---|---|---|---|
| **Approach 1: On-Demand Remote Commands via Telegram (`/listen`, `/status`)** | Persistent long polling for incoming bot messages; captures 15s audio upon `/listen`. | Instant room audio on demand from upstairs; zero physical contact with downstairs phone. | **Selected by user.** Requires persistent background service for polling. |
| **Approach 2: Purely Hands-Free Nightly Schedule** | Relies solely on 11 PM alarm trigger. | Fully automated, zero interaction. | Rejected: Cannot check sound before 11 PM or outside the schedule window. |
| **Approach 3: Hybrid (Nightly Schedule + Remote Commands)** | Automatically arms at 11 PM AND listens for remote commands. | Maximum flexibility. | Can be added as a subsequent layer; Option 1 addresses the immediate need. |

---

## 5. System Architecture & Remote Command Flow

```
+-----------------------------------------------------------+
|                      Upstairs Phone                       |
|                                                           |
|  User opens Telegram -> Sends: "/listen"                  |
+-----------------------------------------------------------+
                             |
                             | (HTTPS via Telegram API)
                             v
+-----------------------------------------------------------+
|                      Telegram Cloud                       |
|  api.telegram.org/bot<TOKEN>/getUpdates                   |
+-----------------------------------------------------------+
                             |
                             | (Long-poll response: 200 OK)
                             v
+-----------------------------------------------------------+
|                      Downstairs Phone                     |
|                                                           |
|  [TelegramRemoteListener] (Background Service Coroutine)  |
|         |                                                 |
|         v                                                 |
|  Security Check: from.id == prefs.chatId?                 |
|         |                                                 |
|   (Authorized)                                            |
|         v                                                 |
|  Command Router:                                          |
|  - "/status" -> Send text: Battery %, Charging, State     |
|  - "/listen" or "/record" ->                              |
|         |                                                 |
|         v                                                 |
|  [SnippetRecorder.recordSnippet(15000L)]                  |
|         |                                                 |
|         v                                                 |
|  [TelegramDispatcher.sendVoiceSnippet()]                  |
|         |                                                 |
|         v                                                 |
|  Caption: "🎙️ Room sound requested from upstairs"         |
+-----------------------------------------------------------+
                             |
                             | (HTTPS POST /sendVoice)
                             v
+-----------------------------------------------------------+
|                      Upstairs Phone                       |
|                                                           |
|  User receives voice note with audio waveform.            |
|  Taps Play -> Verifies room sound immediately!            |
+-----------------------------------------------------------+
```

---

## 6. Detailed Component Design

### 6.1 `TelegramRemoteListener`
- Coroutine-based polling loop running on `Dispatchers.IO`.
- Calls `https://api.telegram.org/bot<token>/getUpdates?offset=<lastUpdateId + 1>&timeout=25`.
- Parses incoming JSON using lightweight Kotlin/Android `org.json.JSONObject`.
- State tracking:
  - `lastUpdateId`: stored in memory (and persisted in preferences to avoid replaying old commands on restart).
- Command definitions:
  - `/listen` or `/record`: replies with "🎙️ Recording 15-second snippet...", triggers `SnippetRecorder`, sends voice message.
  - `/status`: replies with battery level, charging status, whether snore monitor is active.
  - `/help` or `/start`: sends list of commands.
  - Any unknown command: sends brief help text.

### 6.2 Security Filter
- Every incoming message object contains `message.chat.id`.
- Filter logic:
  ```kotlin
  val incomingChatId = message.getJSONObject("chat").getLong("id").toString()
  if (incomingChatId != prefs.chatId.trim()) {
      // Ignore unauthorized messages completely
      return
  }
  ```

### 6.3 Concurrency & Resource Management
- A Mutex or `AtomicBoolean` ensures only one recording occurs at a time if multiple `/listen` commands arrive in rapid succession.
- If continuous snore detection is running, it pauses mic reading during snippet recording to prevent `AudioRecord` / `MediaRecorder` conflicts.

### 6.4 User Interface Updates (`MainScreen.kt`)
- New card: **Remote Control (Telegram Commands)**
  - Toggle switch: "Enable Telegram Remote Control"
  - Indicator: 🟢 Active (listening for `/listen`, `/status`) or ⚪ Inactive
  - Quick test button: "Poll Now" or status feedback.

---

## 7. Verification & Testing Strategy

1. **Unit / Component Tests:**
   - Mock Telegram JSON payload parsing for `/listen`, `/status`, and unauthorized `chat_id`.
   - Verify `lastUpdateId` tracking and offset incrementing.
2. **End-to-End Test Plan:**
   - Send `/status` from upstairs phone -> Verify Telegram receives instant battery and state reply.
   - Send `/listen` from upstairs phone -> Verify downstairs phone records 15s and sends `.m4a` voice clip.
   - Verify unauthorized Telegram account sending `/listen` receives no response and triggers no audio recording.
