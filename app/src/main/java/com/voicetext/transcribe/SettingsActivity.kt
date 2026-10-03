package com.voicetext.transcribe

import android.content.ClipboardManager
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
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
        binding.apiKeyInput.doOnTextChanged { _, _, _, _ -> clearError() }

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
            // With a key already saved, an empty Save (e.g. a second tap right
            // after saving) is a no-op rather than an error.
            if (keyStore.apiKey == null) {
                binding.apiKeyLayout.error = getString(R.string.api_key_empty)
            }
            return
        }
        if (!key.startsWith("sk-")) {
            binding.apiKeyLayout.error = getString(R.string.api_key_bad_format)
            return
        }
        clearError()
        keyStore.apiKey = key
        binding.apiKeyInput.text?.clear()
        hideKeyboard()
        updateKeyStatus()
        Toast.makeText(this, R.string.api_key_saved, Toast.LENGTH_SHORT).show()
    }

    /** Clears the error and collapses the space it reserved under the field. */
    private fun clearError() {
        binding.apiKeyLayout.error = null
        binding.apiKeyLayout.isErrorEnabled = false
    }

    private fun hideKeyboard() {
        binding.apiKeyInput.clearFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.apiKeyInput.windowToken, 0)
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
