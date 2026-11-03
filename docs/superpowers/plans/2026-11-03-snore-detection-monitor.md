# Android Snore Detection & Audio Snippet Monitor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android application that runs on a downstairs device, automatically wakes up at 11:00 PM IST (or on demand), detects rhythmic snoring patterns, records a 15-second `.m4a` audio snippet, sends it to the user upstairs via Telegram Bot, and cleanly shuts down for the night ("one-and-done").

**Architecture:** A foreground service (`SnoreDetectionService`) captures audio via `AudioRecord`, extracts RMS amplitude envelopes to detect periodic snoring cycles, delegates to `SnippetRecorder` to record 15 seconds of AAC audio once snoring is confirmed, dispatches the clip to the Telegram Bot API via `TelegramDispatcher`, and calls `stopSelf()` while scheduling the next night's 11:00 PM wake-up via `AlarmManager`. A Jetpack Compose UI provides manual controls, sensitivity calibration, and bot credential configuration.

**Tech Stack:** Kotlin 2.0, Android SDK (compileSdk 35, targetSdkVersion 33, minSdk 26), Jetpack Compose + Material3, Coroutines/Flow, OkHttp 4.12, JUnit 4/5.

**Spec:** `docs/superpowers/specs/2026-11-03-snore-detection-monitor-design.md`

## Global Constraints

- **Target SDK:** Must compile with `compileSdk = 35` and set `targetSdkVersion = 33` (Android 13) to allow `AlarmManager` to directly start `SnoreDetectionService` without encountering Android 14+ background microphone launch restrictions.
- **Audio Privacy:** Must run as a foreground service with `android:foregroundServiceType="microphone"` and post an ongoing notification with channel ID `"snore_monitor_channel"`.
- **Audio Focus:** Must register `AudioManager.OnAudioFocusChangeListener` to pause audio analysis during phone calls or media playback.
- **Lifecycle:** Must implement "One-and-Done" shutdown: immediately after sending the 15-second audio snippet, `SnoreDetectionService` releases microphone resources, cancels notification, and stops itself.

## Review Focus

1. **Short Spurious Noise:** A single loud noise (cough, dropped item, door slam) must NOT trigger clip recording. Expectation: requires at least 4-5 rhythmic peaks within a 45-second sliding window.
2. **Phone Call During Monitoring:** Incoming call on brother's phone while monitoring must immediately yield microphone. Expectation: audio focus loss pauses capture cleanly and resumes when idle.
3. **Network Offline During Dispatch:** If Wi-Fi/mobile network is temporarily disconnected when dispatching clip, app must not crash. Expectation: catches `IOException`, logs error, stores clip locally, and safely shuts down or retries once.
4. **Alarm After Reboot:** Device restarted during the day must restore the 11:00 PM alarm. Expectation: `BootReceiver` listens to `ACTION_BOOT_COMPLETED` and calls `AlarmScheduler.scheduleNightlyAlarm()`.
5. **Exact 15-Second Clip Duration:** Audio clip recorded must not exceed 15 seconds or hang the recorder. Expectation: timer terminates recording after exactly 15,000 ms.

---

### Task 1: Android Project Scaffolding & Manifest Configuration

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `local.properties`

**Interfaces:**
- Consumes: Standard Android Gradle Plugin (AGP 8.5+) and Kotlin plugins.
- Produces: Compilable Gradle Android project configured with targetSdk 33, Compose, OkHttp, and required permissions.

- [ ] **Step 1: Create root and app gradle build configuration**
  Write `settings.gradle.kts`, root `build.gradle.kts`, `local.properties` (pointing to `sdk.dir=/Users/singh/Library/Android/sdk`), and `app/build.gradle.kts` with dependencies: Compose Material3, Coroutines, OkHttp `com.squareup.okhttp3:okhttp:4.12.0`, JUnit.
- [ ] **Step 2: Create AndroidManifest.xml with required permissions and service declarations**
  Include `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `INTERNET`, `WAKE_LOCK`. Declare `SnoreDetectionService` with `android:foregroundServiceType="microphone"`.
- [ ] **Step 3: Verify build scaffolding compiles**
  Run: `gradle assembleDebug`
  Expected: BUILD SUCCESSFUL (outputs `app/build/outputs/apk/debug/app-debug.apk`).
- [ ] **Step 4: Commit**
  ```bash
  git add settings.gradle.kts build.gradle.kts app/ local.properties
  git commit -m "chore: scaffold Android project with targetSdk 33 and dependencies"
  ```

---

### Task 2: Snore Detection Algorithm & Energy Analyzer

**Files:**
- Create: `app/src/main/java/com/rough/snoremonitor/detection/SnoreDetector.kt`
- Create: `app/src/test/java/com/rough/snoremonitor/detection/SnoreDetectorTest.kt`

**Interfaces:**
- Consumes: Raw audio amplitude samples (PCM 16-bit or calculated RMS floats).
- Produces: `SnoreDetector` class with method `processRms(rmsDb: Float, timestampMs: Long): Boolean`. Returns `true` when a sustained rhythmic snoring pattern is confirmed.

- [ ] **Step 1: Write failing unit test for `SnoreDetector`**
  Write tests in `SnoreDetectorTest.kt`:
  - `testIsolatedLoudNoiseDoesNotTrigger()`: A single 70dB burst followed by silence returns `false`.
  - `testRhythmicSnoringTriggersAfterRequiredCycles()`: 5 cycles of 60dB peaks spaced 3.0 seconds apart returns `true`.
  - `testLowVolumeBackgroundNoiseDoesNotTrigger()`: Continuous 30dB baseline returns `false`.
- [ ] **Step 2: Run test to verify it fails**
  Run: `gradle testDebugUnitTest --tests "com.rough.snoremonitor.detection.SnoreDetectorTest"`
  Expected: FAIL (class not found).
- [ ] **Step 3: Implement `SnoreDetector`**
  Implement sliding time-window peak detection:
  - Track background noise baseline via moving average.
  - Detect peaks where RMS exceeds baseline + threshold (e.g. 15dB surge).
  - Record peak timestamps and verify inter-peak interval is between 2.0s and 5.5s.
  - Return `true` once 5 consecutive intervals match the snoring cadence.
- [ ] **Step 4: Run test to verify it passes**
  Run: `gradle testDebugUnitTest --tests "com.rough.snoremonitor.detection.SnoreDetectorTest"`
  Expected: PASS
- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/java/com/rough/snoremonitor/detection/ app/src/test/java/com/rough/snoremonitor/detection/
  git commit -m "feat: implement rhythmic snore detection algorithm with unit tests"
  ```

---

### Task 3: Telegram Dispatcher for Audio Snippets

**Files:**
- Create: `app/src/main/java/com/rough/snoremonitor/dispatch/TelegramDispatcher.kt`
- Create: `app/src/test/java/com/rough/snoremonitor/dispatch/TelegramDispatcherTest.kt`

**Interfaces:**
- Consumes: `botToken: String`, `chatId: String`, `audioFile: File`, `caption: String`.
- Produces: `suspend fun sendVoiceSnippet(botToken: String, chatId: String, audioFile: File, caption: String): Result<Boolean>`.

- [ ] **Step 1: Write failing unit test for `TelegramDispatcher`**
  Write mock test in `TelegramDispatcherTest.kt` verifying multipart request construction to `https://api.telegram.org/bot<token>/sendVoice` with `chat_id`, `caption`, and file payload.
- [ ] **Step 2: Run test to verify it fails**
  Run: `gradle testDebugUnitTest --tests "com.rough.snoremonitor.dispatch.TelegramDispatcherTest"`
  Expected: FAIL
- [ ] **Step 3: Implement `TelegramDispatcher`**
  Using OkHttp `MultipartBody.Builder`:
  - Set `type(MultipartBody.FORM)`.
  - Add `chat_id` and `caption` form fields.
  - Add `voice` form data part with `audio/mp4` or `audio/ogg` mime type.
  - Execute request asynchronously; parse HTTP 200 response into `Result.success(true)`. Catch network errors and return `Result.failure(e)`.
- [ ] **Step 4: Run test to verify it passes**
  Run: `gradle testDebugUnitTest --tests "com.rough.snoremonitor.dispatch.TelegramDispatcherTest"`
  Expected: PASS
- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/java/com/rough/snoremonitor/dispatch/ app/src/test/java/com/rough/snoremonitor/dispatch/
  git commit -m "feat: implement Telegram Bot audio snippet dispatcher"
  ```

---

### Task 4: Audio Snippet Recorder

**Files:**
- Create: `app/src/main/java/com/rough/snoremonitor/recorder/SnippetRecorder.kt`
- Create: `app/src/test/java/com/rough/snoremonitor/recorder/SnippetRecorderTest.kt`

**Interfaces:**
- Consumes: Target directory (`context.cacheDir`), duration (`durationMs = 15_000L`).
- Produces: `suspend fun recordSnippet(outputFile: File, durationMs: Long): Result<File>`.

- [ ] **Step 1: Write test interface and contract for `SnippetRecorder`**
  Verify file path resolution, timestamp formatting, and completion callback logic.
- [ ] **Step 2: Implement `SnippetRecorder`**
  Use `MediaRecorder` configured with `AudioSource.MIC`, `OutputFormat.MPEG_4`, `AudioEncoder.AAC`, sampling rate `16000Hz`, encoding bit rate `32000bps`.
  Use coroutine delay for `durationMs` before calling `stop()` and `release()`.
- [ ] **Step 3: Verify build and unit test execution**
  Run: `gradle testDebugUnitTest --tests "com.rough.snoremonitor.recorder.*"`
  Expected: PASS
- [ ] **Step 4: Commit**
  ```bash
  git add app/src/main/java/com/rough/snoremonitor/recorder/ app/src/test/java/com/rough/snoremonitor/recorder/
  git commit -m "feat: implement 15-second AAC audio snippet recorder"
  ```

---

### Task 5: Snore Detection Foreground Service & Lifecycle

**Files:**
- Create: `app/src/main/java/com/rough/snoremonitor/service/SnoreDetectionService.kt`
- Create: `app/src/main/java/com/rough/snoremonitor/service/ServiceNotificationManager.kt`

**Interfaces:**
- Consumes: `SnoreDetector`, `SnippetRecorder`, `TelegramDispatcher`, `AudioManager`.
- Produces: `SnoreDetectionService : Service` with full foreground lifecycle, audio recording loop, one-and-done trigger, and `stopSelf()`.

- [ ] **Step 1: Implement `ServiceNotificationManager`**
  Create notification channel `"snore_monitor_channel"` with priority `LOW` (silent ongoing indicator). Provide helper `createForegroundNotification()` displaying *"Listening for sleep audio..."*.
- [ ] **Step 2: Implement `SnoreDetectionService`**
  - In `onStartCommand()`, call `startForeground(NOTIFICATION_ID, notification)`.
  - Request audio focus via `AudioManager.requestAudioFocus()`; register callback to pause when focus is lost.
  - Launch coroutine reading `AudioRecord` PCM buffer, calculating RMS decibels, and feeding to `SnoreDetector`.
  - When `snoreDetector.processRms()` returns `true`:
    1. Stop `AudioRecord` to free the microphone.
    2. Call `snippetRecorder.recordSnippet(file, 15000L)`.
    3. Call `telegramDispatcher.sendVoiceSnippet(token, chatId, file, "Brother is asleep - snoring confirmed.")`.
    4. Call `stopForeground(STOP_FOREGROUND_REMOVE)` and `stopSelf()`.
- [ ] **Step 3: Compile and verify service integration**
  Run: `gradle assembleDebug`
  Expected: BUILD SUCCESSFUL
- [ ] **Step 4: Commit**
  ```bash
  git add app/src/main/java/com/rough/snoremonitor/service/
  git commit -m "feat: implement SnoreDetectionService with audio focus and one-and-done shutdown"
  ```

---

### Task 6: Alarm Scheduling & Boot Receiver

**Files:**
- Create: `app/src/main/java/com/rough/snoremonitor/scheduler/AlarmScheduler.kt`
- Create: `app/src/main/java/com/rough/snoremonitor/receiver/AlarmReceiver.kt`
- Create: `app/src/main/java/com/rough/snoremonitor/receiver/BootReceiver.kt`

**Interfaces:**
- Consumes: Target time (default 23:00 IST / Indian Standard Time).
- Produces: `AlarmScheduler.scheduleDailyAlarm(context, hour, minute)` and receivers to launch `SnoreDetectionService`.

- [ ] **Step 1: Implement `AlarmScheduler`**
  Calculate next occurrence of 23:00 in Asia/Kolkata timezone (`ZoneId.of("Asia/Kolkata")`).
  Call `alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)`.
- [ ] **Step 2: Implement `AlarmReceiver` and `BootReceiver`**
  `AlarmReceiver.onReceive()` calls `ContextCompat.startForegroundService(context, Intent(context, SnoreDetectionService::class.java))`.
  `BootReceiver.onReceive()` restores the scheduled alarm after device reboot.
- [ ] **Step 3: Write unit tests verifying timezone and next-alarm time calculations**
  Verify if current time is 23:30, next trigger is scheduled for 23:00 tomorrow. If current time is 20:00, next trigger is scheduled for 23:00 today.
- [ ] **Step 4: Run unit tests**
  Run: `gradle testDebugUnitTest --tests "com.rough.snoremonitor.scheduler.*"`
  Expected: PASS
- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/java/com/rough/snoremonitor/scheduler/ app/src/main/java/com/rough/snoremonitor/receiver/
  git commit -m "feat: implement 11:00 PM IST AlarmScheduler and BootReceiver"
  ```

---

### Task 7: Jetpack Compose User Interface & Configuration

**Files:**
- Create: `app/src/main/java/com/rough/snoremonitor/data/AppPreferences.kt`
- Create: `app/src/main/java/com/rough/snoremonitor/ui/MainActivity.kt`
- Create: `app/src/main/java/com/rough/snoremonitor/ui/MainScreen.kt`

**Interfaces:**
- Consumes: User inputs (Telegram Bot Token, Chat ID, Schedule Time, Sensitivity).
- Produces: Main activity UI with permission requests, test mic button, and live status.

- [ ] **Step 1: Implement `AppPreferences`**
  Store and retrieve `botToken`, `chatId`, `scheduledHour` (default 23), `scheduledMinute` (default 0), and `sensitivity` using Android `SharedPreferences`.
- [ ] **Step 2: Implement `MainScreen` in Jetpack Compose**
  - Status Card: "Scheduled for 11:00 PM IST" or "Service Running".
  - Configuration Fields: Telegram Bot Token, Chat ID, with a "Send Test Ping" button.
  - Action Buttons: "Start Listening Now (Manual Test)" and "Schedule 11:00 PM".
  - Runtime Permission Handler: Requests `RECORD_AUDIO` and `POST_NOTIFICATIONS`.
- [ ] **Step 3: Connect `MainActivity`**
  Set Compose content to `MainScreen` and handle permission callbacks.
- [ ] **Step 4: Verify full project compilation**
  Run: `gradle assembleDebug`
  Expected: BUILD SUCCESSFUL
- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/java/com/rough/snoremonitor/data/ app/src/main/java/com/rough/snoremonitor/ui/
  git commit -m "feat: implement Jetpack Compose UI and configuration persistence"
  ```

---

### Task 8: End-to-End Build & APK Packaging

**Files:**
- Output: `app/build/outputs/apk/debug/app-debug.apk`
- Create: `README.md` (Setup instructions for Telegram Bot Father, APK installation, and usage)

- [ ] **Step 1: Build debug APK**
  Run: `gradle assembleDebug`
  Expected: Output `app/build/outputs/apk/debug/app-debug.apk` exists.
- [ ] **Step 2: Run all unit tests**
  Run: `gradle test`
  Expected: All tests pass.
- [ ] **Step 3: Create README.md with clear guide**
  Document step-by-step how to get a free Telegram Bot Token from `@BotFather`, find Chat ID via `@userinfobot`, sideload the APK onto the brother's Android phone, and verify reception on the upstairs phone.
- [ ] **Step 4: Commit**
  ```bash
  git add README.md
  git commit -m "docs: add setup and usage guide for snore detection monitor"
  ```
