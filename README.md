# Voice Transcribe — Android MVP

A minimal "share-to-transcribe" app for WhatsApp voice notes. Share a voice
note from WhatsApp's native share sheet, get a floating overlay with the
transcript (via the OpenAI Whisper API), then copy it or send it back.

## Transcription (OpenAI Whisper)

`TranscriptionEngine.kt` uploads the shared audio to
`POST https://api.openai.com/v1/audio/transcriptions` with model `whisper-1`.

- **API key:** open the app → **Settings**, enter your OpenAI API key and
  tap **Save key** (you can replace or remove it there later). It's stored in
  app-private SharedPreferences, never baked into the APK. To type a key that
  lives in an environment variable on your computer straight into the phone,
  tap the key field and run
  `adb shell input text "$OPENAI_API_KEY"`.
- **Formats:** WhatsApp voice notes are Ogg/Opus, often shared as `.opus` or
  without a name. Whisper picks the decoder from the file extension, so the
  upload is renamed to a supported extension (`.ogg` for Opus).
- **Limits:** Whisper accepts files up to 25 MB.
- **Errors** shown in the overlay: missing/invalid key, no internet, rate
  limit/quota, OpenAI error messages, and "empty transcription" when Whisper
  hears no speech.

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
   the Whisper transcript (make sure you saved an API key first).
6. Try **Copy** (check your clipboard) and **Send to WhatsApp** (should jump
   straight into WhatsApp's chat picker with the transcript pre-filled).
7. Tap outside the card, or press back, to dismiss — should fade out fast.

## Known rough edges in this MVP (intentional, for later)

- No history/persistence yet — closing the overlay discards the transcript.
- No settings (language selection, on-device vs. cloud toggle, etc.)
- `minSdk 26` — adaptive icons only, no legacy icon densities generated.
- The "Send to WhatsApp" button re-opens WhatsApp's own chat picker rather
  than returning to the exact chat the voice note came from — there's no
  public API for that, this is expected and was called out during design.

## Project structure

```
app/src/main/java/com/voicetext/transcribe/
  MainActivity.kt              — launcher screen (instructions + Settings link)
  SettingsActivity.kt          — set / replace / remove the OpenAI API key
  ApiKeyStore.kt               — persists the OpenAI API key
  TranscribeShareActivity.kt   — the share-target entry point + overlay logic
  TranscriptionEngine.kt       — OpenAI Whisper API client
app/src/main/res/layout/
  activity_main.xml
  activity_settings.xml
  activity_transcribe_overlay.xml
app/src/main/res/values/
  strings.xml, colors.xml, themes.xml
```
