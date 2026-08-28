package com.voicetext.transcribe

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.voicetext.transcribe.databinding.ActivityMainBinding

/**
 * MVP launcher screen. There's no real UI to build here yet: the whole point
 * of this app is that users never really "open" it directly — they reach it
 * via WhatsApp's share sheet (see TranscribeShareActivity). This screen just
 * explains that, so testers aren't confused when opening the app icon directly.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}
