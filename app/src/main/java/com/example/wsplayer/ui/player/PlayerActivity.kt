package com.example.wsplayer.ui.player // Ujistěte se, že balíček odpovídá

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.example.wsplayer.R
import com.example.wsplayer.databinding.ActivityPlayerBinding // Import ViewBinding

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var currentPosition: Int = 0 // Pro uložení pozice při rekonfiguraci

    companion object {
        const val EXTRA_VIDEO_URL = "extra_video_url"
        const val EXTRA_VIDEO_TITLE = "extra_video_title" // Volitelný název videa
        private const val TAG = "PlayerActivity"
        private const val PLAYBACK_POSITION = "playback_position"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "onCreate called")

        // Skrytí ActionBaru pro celoobrazovkový zážitek
        supportActionBar?.hide()
        // Povolení celoobrazovkového režimu (immersive mode)
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        // Set the content to appear under the system bars so that the
                        // content doesn't resize when the system bars hide and show.
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        // Hide the nav bar and status bar
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN)


        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        val videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) // Můžete použít k nastavení titulku okna

        if (videoTitle != null) {
            title = videoTitle // Nastaví titulek okna, pokud je ActionBar viditelný (což není)
            Log.d(TAG, "Video title: $videoTitle")
        }

        if (videoUrl.isNullOrEmpty()) {
            Log.e(TAG, "Video URL is null or empty. Finishing activity.")
            Toast.makeText(this, "Chyba: URL videa není k dispozici.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        Log.d(TAG, "Video URL: $videoUrl")

        // Obnovení pozice přehrávání, pokud byla uložena
        if (savedInstanceState != null) {
            currentPosition = savedInstanceState.getInt(PLAYBACK_POSITION, 0)
            Log.d(TAG, "Restoring playback position: $currentPosition ms")
        }


        setupVideoView(videoUrl)
    }

    private fun setupVideoView(videoUrl: String) {
        val mediaController = MediaController(this)
        mediaController.setAnchorView(binding.videoView)
        binding.videoView.setMediaController(mediaController)

        try {
            val videoUri = Uri.parse(videoUrl)
            binding.videoView.setVideoURI(videoUri)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting video URI: ${e.message}", e)
            Toast.makeText(this, "Chyba při nastavování videa: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.videoView.setOnPreparedListener { mp ->
            Log.d(TAG, "VideoView onPrepared. Duration: ${mp.duration} ms")
            binding.progressBarPlayer.visibility = View.GONE
            if (currentPosition > 0) {
                Log.d(TAG, "Seeking to saved position: $currentPosition ms")
                binding.videoView.seekTo(currentPosition)
            }
            binding.videoView.start()
            mp.setOnInfoListener { _, what, _ ->
                // Zobrazení/skrytí ProgressBaru podle stavu bufferování
                when (what) {
                    android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                        binding.progressBarPlayer.visibility = View.VISIBLE
                        true
                    }
                    android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                        binding.progressBarPlayer.visibility = View.GONE
                        true
                    }
                    else -> false
                }
            }
        }

        binding.videoView.setOnCompletionListener {
            Log.d(TAG, "VideoView onCompletion.")
            Toast.makeText(this, "Video dokončeno.", Toast.LENGTH_SHORT).show()
            // Můžete zde aktivitu ukončit nebo nabídnout další akce
            // finish()
        }

        binding.videoView.setOnErrorListener { mp, what, extra ->
            Log.e(TAG, "VideoView onError. What: $what, Extra: $extra")
            binding.progressBarPlayer.visibility = View.GONE
            val errorMsg = when (what) {
                android.media.MediaPlayer.MEDIA_ERROR_SERVER_DIED -> "Server pro přehrávání zemřel."
                android.media.MediaPlayer.MEDIA_ERROR_UNKNOWN -> "Neznámá chyba přehrávání."
                else -> "Došlo k chybě při přehrávání videa (what: $what, extra: $extra)."
            }
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            // Můžete zde aktivitu ukončit
            // finish()
            true // Vrácení true znamená, že jste chybu ošetřili
        }

        // Zobrazení ProgressBaru na začátku, než se video připraví
        binding.progressBarPlayer.visibility = View.VISIBLE
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called")
        if (binding.videoView.isPlaying) {
            currentPosition = binding.videoView.currentPosition
            binding.videoView.pause()
            Log.d(TAG, "Video paused. Current position saved: $currentPosition ms")
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
        if (!binding.videoView.isPlaying && currentPosition > 0) {
            // Pokud bylo video pozastaveno a máme uloženou pozici,
            // můžeme ho zde znovu spustit, ale onPrepared to již řeší přes seekTo.
            // binding.videoView.seekTo(currentPosition)
            // binding.videoView.start()
            // Log.d(TAG, "Video resumed from position: $currentPosition ms")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Uložení aktuální pozice přehrávání
        currentPosition = binding.videoView.currentPosition
        outState.putInt(PLAYBACK_POSITION, currentPosition)
        Log.d(TAG, "onSaveInstanceState: Saving position $currentPosition ms")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")
        binding.videoView.stopPlayback() // Uvolnění zdrojů VideoView
    }
}
