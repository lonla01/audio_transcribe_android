# Voice Transcribe — Android MVP

A minimal "share-to-transcribe" app for WhatsApp voice notes. This MVP proves
out the full plumbing — receiving a shared voice note from WhatsApp's native
share sheet, showing an instant floating overlay, and sending a transcript
back — using a **mock transcription engine** so you can test the whole flow
right away without needing an API key or on-device model yet.

## What's real vs. mocked

**Real (fully working):**
- Appears as "Transcribe" in WhatsApp's native long-press → Share menu
- Reads the actual shared audio bytes from WhatsApp
- Floating transient overlay UI (fade in/out, not a full-screen app switch)
- Copy transcript to clipboard
- Send transcript back into WhatsApp (opens WhatsApp's chat picker)

**Mocked (see `TranscriptionEngine.kt`):**
- The actual speech-to-text step. It reads your real audio file, simulates
  ~1.2s of "processing" time, and returns a placeholder string confirming the
  file size it read. Swap the body of `transcribe()` for a real Whisper API
  call or on-device whisper.cpp/JNI call when you're ready — nothing else in
  the app needs to change.

## How to open and run

1. Unzip this project.
2. Open the folder in Android Studio (Giraffe/Koala or newer recommended) via
   **File → Open**, and select the `VoiceTranscribeWA` folder itself (the one
   containing `settings.gradle.kts`).
3. Android Studio will detect there's no Gradle wrapper jar bundled (it's
   omitted here since this was built without network access) and will offer
   to generate one automatically on first sync — accept that prompt. If it
   doesn't prompt automatically, go to **File → Sync Project with Gradle Files**,
   or run `gradle wrapper --gradle-version 8.7` from a terminal in this folder
   if you have Gradle installed locally.
4. Let Gradle sync (first sync will download the Android Gradle Plugin,
   Kotlin, and AndroidX dependencies — needs internet access).
5. Run the app on a device or emulator that also has WhatsApp installed.
   Note: on an **emulator**, you'll need WhatsApp itself installed on it too
   (or push a test app with a real voice note) — testing on a **real phone**
   with WhatsApp already installed is much easier.

## How to test the actual flow

1. Install the app on your phone (Run ▶ from Android Studio, or build a debug
   APK: `./gradlew assembleDebug`, then install
   `app/build/outputs/apk/debug/app-debug.apk`).
2. Open WhatsApp, find any chat with a voice message.
3. Long-press the voice message bubble → tap **Share**.
4. In the share sheet, find and tap **"Transcribe"** — that's this app.
5. You should see a floating card appear immediately with a spinner, then
   (after ~1.2s) a mock transcript confirming it read your real audio file's
   size in KB.
6. Try **Copy** (check your clipboard) and **Send to WhatsApp** (should jump
   straight into WhatsApp's chat picker with the transcript pre-filled).
7. Tap outside the card, or press back, to dismiss — should fade out fast.

## Known rough edges in this MVP (intentional, for later)

- No history/persistence yet — closing the overlay discards the transcript.
- No real STT — see `TranscriptionEngine.kt`.
- No settings (language selection, on-device vs. cloud toggle, etc.)
- `minSdk 26` — adaptive icons only, no legacy icon densities generated.
- The "Send to WhatsApp" button re-opens WhatsApp's own chat picker rather
  than returning to the exact chat the voice note came from — there's no
  public API for that, this is expected and was called out during design.

## Project structure

```
app/src/main/java/com/voicetext/transcribe/
  MainActivity.kt              — launcher screen (just instructions)
  TranscribeShareActivity.kt   — the share-target entry point + overlay logic
  TranscriptionEngine.kt       — mock STT, swap this out for the real thing
app/src/main/res/layout/
  activity_main.xml
  activity_transcribe_overlay.xml
app/src/main/res/values/
  strings.xml, colors.xml, themes.xml
```
