package com.voicetext.transcribe

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.UUID

/**
 * Transcribes shared audio with the OpenAI Whisper API
 * (POST /v1/audio/transcriptions, model "whisper-1").
 *
 * Uses only platform classes (HttpURLConnection + org.json) so the app has no
 * extra networking dependencies. The API key is supplied by the caller (it's
 * entered in SettingsActivity and stored in [ApiKeyStore]).
 */
class TranscriptionEngine(
    private val contentResolver: ContentResolver,
    private val apiKey: String?,
) {

    suspend fun transcribe(audioUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val key = apiKey?.trim()
            if (key.isNullOrEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("No OpenAI API key set. Add one in Settings.")
                )
            }

            val bytes = contentResolver.openInputStream(audioUri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IllegalStateException("Could not open shared audio"))

            if (bytes.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Shared audio file was empty"))
            }
            if (bytes.size > MAX_UPLOAD_BYTES) {
                val sizeMb = bytes.size / (1024 * 1024)
                return@withContext Result.failure(
                    IllegalStateException("Audio is too large for Whisper (max 25 MB, this file is $sizeMb MB)")
                )
            }

            val (fileName, mimeType) = resolveUploadName(audioUri)
            val text = postToWhisper(key, bytes, fileName, mimeType).trim()

            if (text.isEmpty()) {
                Result.failure(
                    IllegalStateException("Whisper returned an empty transcription. The voice note may be silent or too short.")
                )
            } else {
                Result.success(text)
            }
        } catch (e: UnknownHostException) {
            Result.failure(IOException("No internet connection", e))
        } catch (e: SocketTimeoutException) {
            Result.failure(IOException("Whisper took too long to respond. Try again.", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun postToWhisper(key: String, audio: ByteArray, fileName: String, mimeType: String): String {
        val boundary = "VoiceTranscribe" + UUID.randomUUID().toString().replace("-", "")
        val body = buildMultipartBody(boundary, audio, fileName, mimeType)

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 120_000
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
            setFixedLengthStreamingMode(body.size)
        }

        try {
            connection.outputStream.use { it.write(body) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (status !in 200..299) {
                throw IOException(describeHttpError(status, responseBody))
            }

            return try {
                JSONObject(responseBody).optString("text", "")
            } catch (e: JSONException) {
                throw IOException("Unexpected response from Whisper", e)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun buildMultipartBody(
        boundary: String,
        audio: ByteArray,
        fileName: String,
        mimeType: String,
    ): ByteArray {
        val out = ByteArrayOutputStream(audio.size + 1024)
        fun writeLine(line: String = "") = out.write("$line\r\n".toByteArray(Charsets.UTF_8))

        fun writeField(name: String, value: String) {
            writeLine("--$boundary")
            writeLine("Content-Disposition: form-data; name=\"$name\"")
            writeLine()
            writeLine(value)
        }

        writeField("model", MODEL)
        writeField("response_format", "json")

        writeLine("--$boundary")
        writeLine("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"")
        writeLine("Content-Type: $mimeType")
        writeLine()
        out.write(audio)
        writeLine()
        writeLine("--$boundary--")

        return out.toByteArray()
    }

    /**
     * Whisper decides the audio format from the uploaded file's extension, and
     * rejects ones it doesn't know. WhatsApp voice notes are Opus-in-Ogg but are
     * often shared as ".opus" or with no name at all, so normalise to a
     * supported extension based on the MIME type / original name.
     */
    private fun resolveUploadName(uri: Uri): Pair<String, String> {
        val displayName = queryDisplayName(uri)
        val mime = contentResolver.getType(uri)?.lowercase()
        val nameExt = displayName?.substringAfterLast('.', "")?.lowercase().orEmpty()

        val ext = when {
            nameExt in SUPPORTED_EXTENSIONS -> nameExt
            nameExt == "opus" || nameExt == "oga" -> "ogg"
            else -> MIME_TO_EXTENSION[mime] ?: "ogg" // WhatsApp voice notes are Ogg/Opus
        }
        val uploadMime = when (ext) {
            "ogg" -> "audio/ogg"
            "mp3", "mpga", "mpeg" -> "audio/mpeg"
            "m4a", "mp4" -> "audio/mp4"
            "wav" -> "audio/wav"
            "webm" -> "audio/webm"
            "flac" -> "audio/flac"
            else -> "application/octet-stream"
        }
        return "audio.$ext" to uploadMime
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    private fun describeHttpError(status: Int, body: String): String {
        val apiMessage = try {
            JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
        } catch (e: JSONException) {
            null
        }
        return when (status) {
            401 -> "Invalid OpenAI API key. Check the key in Settings."
            429 -> apiMessage ?: "OpenAI rate limit or quota exceeded. Try again later."
            413 -> "Audio is too large for Whisper (max 25 MB)."
            else -> "Whisper error ($status)" + (apiMessage?.let { ": $it" } ?: "")
        }
    }

    private companion object {
        const val ENDPOINT = "https://api.openai.com/v1/audio/transcriptions"
        const val MODEL = "whisper-1"
        const val MAX_UPLOAD_BYTES = 25 * 1024 * 1024

        val SUPPORTED_EXTENSIONS = setOf("flac", "m4a", "mp3", "mp4", "mpeg", "mpga", "ogg", "wav", "webm")

        val MIME_TO_EXTENSION = mapOf(
            "audio/ogg" to "ogg",
            "audio/opus" to "ogg",
            "audio/ogg; codecs=opus" to "ogg",
            "audio/mpeg" to "mp3",
            "audio/mp3" to "mp3",
            "audio/mp4" to "m4a",
            "audio/m4a" to "m4a",
            "audio/x-m4a" to "m4a",
            "audio/wav" to "wav",
            "audio/x-wav" to "wav",
            "audio/webm" to "webm",
            "audio/flac" to "flac",
            "audio/x-flac" to "flac",
        )
    }
}
