package com.voicetext.transcribe

import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.voicetext.transcribe.databinding.ActivitySettingsBinding

/**
 * Settings screen: lets the user set, replace or remove the OpenAI API key
 * used for Whisper transcription. The saved key is never shown in full.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var keyStore: ApiKeyStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        keyStore = ApiKeyStore(this)

        binding.backBtn.setOnClickListener { finish() }
        binding.pasteBtn.setOnClickListener { pasteFromClipboard() }
        binding.saveKeyBtn.setOnClickListener { saveKey() }
        binding.removeKeyBtn.setOnClickListener { confirmRemoveKey() }

        updateKeyStatus()
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
        if (text.isNullOrEmpty()) {
            Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_SHORT).show()
        } else {
            binding.apiKeyInput.setText(text)
            binding.apiKeyInput.setSelection(text.length)
        }
    }

    private fun saveKey() {
        val key = binding.apiKeyInput.text?.toString()?.trim().orEmpty()
        if (key.isEmpty()) {
            binding.apiKeyLayout.error = getString(R.string.api_key_empty)
            return
        }
        if (!key.startsWith("sk-")) {
            binding.apiKeyLayout.error = getString(R.string.api_key_bad_format)
            return
        }
        binding.apiKeyLayout.error = null
        keyStore.apiKey = key
        binding.apiKeyInput.text?.clear()
        updateKeyStatus()
        Toast.makeText(this, R.string.api_key_saved, Toast.LENGTH_SHORT).show()
    }

    private fun confirmRemoveKey() {
        AlertDialog.Builder(this)
            .setMessage(R.string.api_key_remove_confirm)
            .setPositiveButton(R.string.action_remove_key) { _, _ ->
                keyStore.apiKey = null
                updateKeyStatus()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateKeyStatus() {
        val masked = keyStore.maskedKey()
        binding.keyStatusText.text = if (masked != null) {
            getString(R.string.api_key_status_set, masked)
        } else {
            getString(R.string.api_key_status_missing_here)
        }
        binding.removeKeyBtn.isEnabled = masked != null
        binding.saveKeyBtn.setText(if (masked != null) R.string.action_replace_key else R.string.action_save_key)
    }
}
