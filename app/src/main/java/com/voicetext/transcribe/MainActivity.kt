package com.voicetext.transcribe

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.voicetext.transcribe.databinding.ActivityMainBinding

/**
 * Launcher screen. Users mostly reach the app via WhatsApp's share sheet (see
 * TranscribeShareActivity); this screen explains that and links to Settings,
 * where the OpenAI API key used for Whisper transcription is managed.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh after returning from Settings.
        val masked = ApiKeyStore(this).maskedKey()
        binding.keyStatusText.text = if (masked != null) {
            getString(R.string.api_key_status_set, masked)
        } else {
            getString(R.string.api_key_status_missing)
        }
    }
}
