# Design Document: Android Snore Detection & Audio Snippet Monitor

**Date:** 2026-11-03  
**Status:** Draft (Pending Review & User Approval)  
**Target Platform:** Android (Android 10 - Android 15, API 29-35)  
**Author:** Pair Programming Agent  

---

## 1. Intent and Why

The user sleeps on the 1st floor of a house, while their brother sleeps on the ground floor. The brother snores consistently once he falls asleep. The user wants an automated monitoring app running on an Android device downstairs that automatically wakes up at 11:00 PM IST (Indian Standard Time), monitors room sound for snoring, and upon detecting rhythmic snoring, records a 15-second audio snippet and sends it to the user upstairs.

This allows the user to tap play, verify with their own ears that their brother is asleep, and comfortably go to sleep themselves without having to physically check downstairs or stay awake listening to a continuous audio stream.

---

## 2. Goals, Non-Goals, and Anti-Goals

### Goals
- **Automatic Nightly Trigger:** App schedules itself to activate at 11:00 PM IST (configurable) using Android's `AlarmManager.setExactAndAllowWhileIdle()`.
- **Background Snore Detection:** Runs a compliant Android `ForegroundService` (with `FOREGROUND_SERVICE_TYPE_MICROPHONE` and persistent notification) that continuously analyzes room audio energy without storing persistent raw audio.
- **Rhythmic Sound/Snore Detection:** Analyzes sound amplitude and periodicity (e.g., loud breathing/snoring peaks occurring every 2–5 seconds sustained for at least 30–60 seconds).
- **15-Second Audio Capture:** Automatically records a clean 15-second `.m4a` / `.aac` audio snippet when the detection threshold is met.
- **Direct Delivery to Upstairs User:** Transmits the 15-second audio clip immediately to the upstairs phone with a notification and playable audio preview (using Telegram Bot API by default).
- **Cool-Down / Debounce:** After sending a verified clip, enters a cool-down state (e.g., 30 minutes) to prevent duplicate notifications throughout the night.
- **User Interface:** A simple Jetpack Compose screen showing service status, test mic button, sensitivity slider, schedule time, and delivery settings.

### Non-Goals
- **Medical Apnea Diagnosis:** The app is not a clinical health device; it only detects room sound patterns to identify when someone is asleep.
- **24/7 Raw Live Streaming:** Does not broadcast an open microphone stream over Wi-Fi all night (saves battery and avoids user having to listen live).
- **iOS Support:** Exclusively targeting Android as requested.

### Anti-Goals
- **Covert Spyware:** The app will not hide its presence. It adheres to Android platform requirements, showing a persistent status bar notification and the system microphone privacy indicator when listening.

---

## 3. Platform Constraints & Permissions

1. **Android Microphone Privacy Policy:**
   - Background microphone recording without a foreground service is blocked starting in Android 9/10.
   - On Android 14+ (API 34+), the service must explicitly declare `android:foregroundServiceType="microphone"` in the manifest and specify a runtime foreground service permission.
   - A persistent system notification in the notification shade is required by Android OS while the service is active.
2. **Doze Mode & Exact Alarms:**
   - Scheduled activation at 11:00 PM requires `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM` permission, along with `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` so the device does not freeze background work when idle.
3. **Audio Permissions:**
   - `RECORD_AUDIO`, `POST_NOTIFICATIONS`, `INTERNET`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`.

---

## 4. Approaches Considered & Tradeoffs

| Approach | Description | Pros | Cons / Reason for Selection |
|---|---|---|---|
| **Approach 1: Continuous Live Audio Stream** | Streams live audio over local Wi-Fi (WebRTC/RTSP) continuously. | Real-time hearing. | High battery drain, network load, forces user to stay awake listening. *Rejected.* |
| **Approach 2: Pure Metadata Alert (No Audio)** | Analyzes sound and sends text push notification ("Brother is asleep"). | Lowest data & battery. | User cannot verify with their own ears. *Rejected by user.* |
| **Approach 3: Automated Snore Detection with 15-Second Clip Delivery** *(Selected)* | Listens periodically/in background, detects snoring pattern, records 15s clip, and sends it to upstairs phone. | Automated, battery-efficient, delivers direct audio proof. | Requires reliable clip delivery mechanism. *Chosen approach.* |

---

## 5. System Architecture & Components

```
+-----------------------------------------------------------+
|                      Downstairs Phone                     |
|                                                           |
|  [AlarmManager (11:00 PM IST)]                            |
|                |                                          |
|                v                                          |
|  [SnoreDetectionService (Foreground Service + Mic)]       |
|                |                                          |
|                v                                          |
|  [AudioRecord Buffer -> Snore Pattern Detector]           |
|                |                                          |
|        (Snoring Confirmed)                                |
|                v                                          |
|  [Capture 15s Audio Clip (.m4a)]                          |
|                |                                          |
|                v                                          |
|  [TelegramDispatcher (HTTPS POST to Telegram Bot API)]    |
+-----------------------------------------------------------+
                             |
                             | (Internet / Wi-Fi)
                             v
+-----------------------------------------------------------+
|                      Upstairs Phone                       |
|                                                           |
|  [Telegram App / Push Notification]                       |
|  - Ding! "Snoring detected at 11:28 PM"                   |
|  - Playable 15-second voice message waveform              |
+-----------------------------------------------------------+
```

### Component Details:
1. **Alarm Receiver (`BootAndScheduleReceiver`):**
   - Schedules daily alarm at 23:00 IST using `AlarmManager.setExactAndAllowWhileIdle()`.
   - Re-registers alarms on device reboot (`ACTION_BOOT_COMPLETED`).
2. **Detection Service (`SnoreDetectionService`):**
   - Starts foreground service displaying: *"Monitoring sleep audio..."*.
   - Uses `AudioRecord` (16kHz, 16-bit Mono PCM).
   - Calculates Root Mean Square (RMS) decibel amplitude over 100ms frames.
   - Detects snoring rhythm: an acoustic surge above background baseline followed by a quiet exhalation/pause every 2.5 to 5 seconds.
   - Requires 5 consecutive rhythmic cycles within a 45-second window to trigger detection (filtering out isolated noises like coughs or door creaks).
3. **Audio Snippet Recorder (`SnippetRecorder`):**
   - Once snoring rhythm is identified, records the next 15 seconds into an AAC-encoded `.m4a` file in the app cache directory.
4. **Delivery Dispatcher (`TelegramDispatcher`):**
   - Uses Telegram Bot API `sendVoice` / `sendDocument` endpoint via OkHttp.
   - **Why Telegram:**
     - 100% free, zero cloud server hosting or maintenance needed.
     - Works across any distance (home Wi-Fi or mobile data).
     - Native audio player in Telegram notification and chat: user can press play right from their lock screen or chat upstairs.
     - Bot setup takes 60 seconds via `@BotFather`.
5. **Debounce Logic:**
   - After dispatching a snippet, the service stops listening or enters a 30-minute sleep state to avoid continuous duplicate clips.
   - Automatically shuts down at 6:00 AM IST (configurable).

---

## 6. What's Left to the Builder

- Exact RMS decibel sensitivity threshold parameters (exposed via a user-facing UI slider: Sensitive, Normal, Firm).
- Exact notification channel ID and text.
- Fallback local HTTP endpoint if Telegram is unavailable.
- Jetpack Compose UI layout styling and theme.

---

## 7. Approval & Next Steps

Once this design document is reviewed and approved by the user, we will invoke the `writing-plans` skill to generate a step-by-step implementation plan.
