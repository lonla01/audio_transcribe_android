package com.voicetext.transcribe

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.delay

/**
 * MVP stub for the transcription backend.
 *
 * This reads the real shared audio bytes (so you can verify the share-target
 * plumbing actually works end-to-end), but returns a fake transcript instead
 * of calling a real speech-to-text engine.
 *
 * TODO next: replace the body of transcribe() with one of:
 *   - A call to a cloud STT API (Whisper API, Google Speech-to-Text, etc.)
 *   - An on-device whisper.cpp call via JNI for offline/low-latency transcription
 *
 * Keeping this as a single suspend function means swapping the engine later
 * won't require touching TranscribeShareActivity at all.
 */
class TranscriptionEngine(private val contentResolver: ContentResolver) {

    suspend fun transcribe(audioUri: Uri): Result<String> {
        return try {
            val bytes = contentResolver.openInputStream(audioUri)?.use { it.readBytes() }
                ?: return Result.failure(IllegalStateException("Could not open shared audio"))

            if (bytes.isEmpty()) {
                return Result.failure(IllegalStateException("Shared audio file was empty"))
            }

            // Simulate network/inference latency so you can test the loading state.
            delay(1200)

            val sizeKb = bytes.size / 1024
            val mockTranscript = "[Mock transcript — plug in real STT in " +
                "TranscriptionEngine.kt]\n\n" +
                "Received audio file successfully (${sizeKb} KB, ${bytes.size} bytes). " +
                "The share-to-transcribe pipeline is working: this app received the " +
                "voice note's audio bytes directly from WhatsApp's share sheet. " +
                "Replace this mock with a real Whisper API call or on-device " +
                "whisper.cpp model to get an actual transcript here."

            Result.success(mockTranscript)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
