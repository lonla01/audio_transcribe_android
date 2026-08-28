package com.voicetext.transcribe

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.voicetext.transcribe.databinding.ActivityTranscribeOverlayBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class TranscribeShareActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTranscribeOverlayBinding
    private var latestTranscript: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTranscribeOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val audioUri = extractAudioUri(intent)
        if (audioUri == null) {
            Toast.makeText(this, R.string.error_no_audio, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Fire transcription immediately (in parallel with UI setup below) so the
        // network/inference time and the UI inflation time overlap instead of stacking.
        val engine = TranscriptionEngine(contentResolver)
        val transcriptionDeferred = lifecycleScope.async(Dispatchers.IO) {
            engine.transcribe(audioUri)
        }

        setupUi()
        showLoadingState()

        lifecycleScope.launch {
            val result = transcriptionDeferred.await()
            result.onSuccess { transcript ->
                latestTranscript = transcript
                showTranscriptState(transcript)
            }.onFailure { error ->
                showErrorState(error.message ?: getString(R.string.error_generic))
            }
        }
    }

    private fun extractAudioUri(intent: Intent?): Uri? {
        if (intent == null || intent.action != Intent.ACTION_SEND) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun setupUi() {
        // Tapping the scrim outside the card dismisses instantly, like a chip, not a full app.
        binding.scrim.setOnClickListener { dismissFast() }

        binding.copyBtn.setOnClickListener {
            latestTranscript?.let { copyToClipboard(it) }
        }
        binding.sendBackBtn.setOnClickListener {
            latestTranscript?.let { sendBackToWhatsApp(it) }
        }
    }

    private fun showLoadingState() {
        binding.loadingPulse.visibility = View.VISIBLE
        binding.transcriptText.visibility = View.GONE
        binding.actionsRow.visibility = View.GONE
        binding.statusText.text = getString(R.string.status_transcribing)
        binding.card.alpha = 0f
        binding.card.animate().alpha(1f).setDuration(120).start()
    }

    private fun showTranscriptState(transcript: String) {
        binding.loadingPulse.visibility = View.GONE
        binding.transcriptText.visibility = View.VISIBLE
        binding.actionsRow.visibility = View.VISIBLE
        binding.statusText.text = getString(R.string.status_done)
        binding.transcriptText.text = transcript
    }

    private fun showErrorState(message: String) {
        binding.loadingPulse.visibility = View.GONE
        binding.transcriptText.visibility = View.VISIBLE
        binding.actionsRow.visibility = View.GONE
        binding.statusText.text = getString(R.string.status_error)
        binding.transcriptText.text = message
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("transcript", text))
        Toast.makeText(this, R.string.copied_toast, Toast.LENGTH_SHORT).show()
    }

    private fun sendBackToWhatsApp(text: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
            setPackage("com.whatsapp")
        }
        try {
            startActivity(sendIntent)
        } catch (e: PackageManager.NameNotFoundException) {
            Toast.makeText(this, R.string.whatsapp_not_found, Toast.LENGTH_SHORT).show()
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, R.string.whatsapp_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dismissFast() {
        binding.card.animate().alpha(0f).setDuration(100).withEndAction { finish() }.start()
    }

    override fun onBackPressed() {
        dismissFast()
    }
}
