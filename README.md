# Snore Monitor - Android App

An automated, private sleep-monitoring Android application designed to detect snoring on a downstairs phone, record a 15-second audio snippet, and send it to your upstairs phone via Telegram so you can verify your brother is asleep before going to sleep yourself.

---

## Features
- **Automated 11:00 PM IST Trigger:** Automatically arms and starts listening at 11:00 PM IST using Android exact alarms (`AlarmManager`).
- **Rhythmic Snore Detection:** Filters out transient room noise (coughs, door movements, items dropping) by requiring repeated periodic breathing peaks (2.0s to 5.5s intervals).
- **15-Second Audio Capture:** Automatically records a clean 15-second `.m4a` audio clip once snoring is detected.
- **Telegram Bot Delivery:** Sends the audio clip directly as a voice message to your Telegram app upstairs, allowing you to press play right from your lock screen or chat.
- **One-and-Done Sleep Lifecycle:** Stops listening and shuts down the service immediately after dispatching the snippet so it doesn't wake you up with continuous notifications throughout the night.
- **Audio Focus Handling:** Automatically pauses and yields the microphone if your brother takes a phone call or plays video/audio.

---

## Quick Setup (Takes 2 Minutes)

### 1. Get Your Free Telegram Bot Token & Chat ID
1. Open Telegram on your phone and search for `@BotFather`.
2. Tap **Start**, then send `/newbot`.
3. Follow the prompts to give your bot a name (e.g., `MySnoreMonitorBot`).
4. `@BotFather` will give you an **API Token** (e.g. `7123456789:ABCdefGhIjkLmNoPqRsTuVwXyZ`). Copy this.
5. In Telegram, search for `@userinfobot`, tap **Start**, and it will reply with your personal numeric **Id** (e.g. `123456789`). Copy this ID.
6. Open your newly created bot in Telegram and tap **Start** once so it has permission to message you.

### 2. Install the APK on Downstairs Phone
1. Transfer `app-debug.apk` to your brother's Android phone (via WhatsApp, USB cable, Google Drive, or local share).
2. Tap the APK file and select **Install** (enable *"Allow from this source"* if prompted by Android).
3. Open **Snore Monitor**.

### 3. Configure the App
1. Allow the **Audio Recording** and **Notification** permissions when prompted.
2. Paste your **Bot Token** and **Chat ID**.
3. Tap **"Send Test Ping to Upstairs Phone"** to verify connection. You will receive a message on Telegram!
4. Tap **"Arm for 11 PM"** to activate the nightly automatic schedule, or tap **"Start Listening"** to test it right away.

---

## Project Structure
- `app/src/main/java/com/rough/snoremonitor/detection/SnoreDetector.kt` — Rhythmic peak detection algorithm.
- `app/src/main/java/com/rough/snoremonitor/dispatch/TelegramDispatcher.kt` — Telegram Bot API audio dispatcher.
- `app/src/main/java/com/rough/snoremonitor/recorder/SnippetRecorder.kt` — 15-second AAC audio recorder.
- `app/src/main/java/com/rough/snoremonitor/service/SnoreDetectionService.kt` — Android Foreground Service & audio loop.
- `app/src/main/java/com/rough/snoremonitor/scheduler/AlarmScheduler.kt` — 11:00 PM IST exact alarm scheduler.
- `app/src/main/java/com/rough/snoremonitor/ui/MainActivity.kt` & `MainScreen.kt` — Jetpack Compose dashboard.

---

## Building from Source
```bash
./gradlew assembleDebug
```
The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`
