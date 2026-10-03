package com.voicetext.transcribe

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.voicetext.transcribe.databinding.ActivityHistoryBinding
import com.voicetext.transcribe.databinding.ItemTranscriptBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Past transcripts, newest first. Tapping one shows it in full with Copy and
 * Delete; "Clear all" empties the history. Opening a transcript here counts
 * as using it, so it restarts its 30-day expiry.
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var store: TranscriptStore
    private val adapter = TranscriptAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = TranscriptStore.get(this)

        binding.backBtn.setOnClickListener { finish() }
        binding.clearAllBtn.setOnClickListener { confirmClearAll() }
        binding.list.adapter = adapter
        binding.list.emptyView = binding.emptyText
        binding.list.setOnItemClickListener { _, _, position, _ -> showTranscript(adapter.getItem(position)) }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                store.purgeExpired()
                store.list()
            }
            adapter.items = items
            binding.clearAllBtn.isEnabled = items.isNotEmpty()
        }
    }

    private fun showTranscript(transcript: Transcript) {
        lifecycleScope.launch(Dispatchers.IO) { store.touch(transcript.audioHash) }
        AlertDialog.Builder(this)
            .setTitle(formatDate(this, transcript.createdAt))
            .setMessage(transcript.text)
            .setPositiveButton(R.string.action_copy) { _, _ -> copyToClipboard(transcript.text) }
            .setNeutralButton(R.string.action_delete) { _, _ -> delete(transcript) }
            .setNegativeButton(R.string.action_close, null)
            .show()
    }

    private fun delete(transcript: Transcript) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { store.delete(transcript.audioHash) }
            reload()
        }
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setMessage(R.string.history_clear_confirm)
            .setPositiveButton(R.string.action_clear_all) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { store.clearAll() }
                    reload()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("transcript", text))
        Toast.makeText(this, R.string.copied_toast, Toast.LENGTH_SHORT).show()
    }

    private inner class TranscriptAdapter : BaseAdapter() {
        var items: List<Transcript> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView?.let { ItemTranscriptBinding.bind(it) }
                ?: ItemTranscriptBinding.inflate(layoutInflater, parent, false)
            val item = items[position]
            row.dateText.text = formatDate(this@HistoryActivity, item.createdAt)
            row.previewText.text = item.text
            return row.root
        }
    }
}
