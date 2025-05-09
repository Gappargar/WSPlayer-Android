package com.example.wsplayer.ui.player // Ujistěte se, že balíček odpovídá

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
// ***** PŘIDÁNY IMPORTY *****
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
// ***************************
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.wsplayer.R
import com.example.wsplayer.databinding.ActivityPlayerBinding // Import ViewBinding

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null // Instance ExoPlayeru

    private var playWhenReady = true // Přehrát hned, jak bude připraveno
    private var mediaItemIndex = 0 // Index aktuální položky (pokud by byl playlist)
    private var playbackPosition = 0L // Pozice přehrávání v ms

    companion object {
        const val EXTRA_VIDEO_URL = "extra_video_url"
        const val EXTRA_VIDEO_TITLE = "extra_video_title" // Volitelný název videa
        private const val TAG = "PlayerActivity"
        // Klíče pro uložení stavu
        private const val STATE_PLAY_WHEN_READY = "state_play_when_ready"
        private const val STATE_MEDIA_ITEM_INDEX = "state_media_item_index"
        private const val STATE_PLAYBACK_POSITION = "state_playback_position"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "onCreate called")

        // Obnovení stavu, pokud existuje
        if (savedInstanceState != null) {
            playWhenReady = savedInstanceState.getBoolean(STATE_PLAY_WHEN_READY)
            mediaItemIndex = savedInstanceState.getInt(STATE_MEDIA_ITEM_INDEX)
            playbackPosition = savedInstanceState.getLong(STATE_PLAYBACK_POSITION)
            Log.d(TAG, "Restored state: playWhenReady=$playWhenReady, itemIndex=$mediaItemIndex, position=$playbackPosition")
        }
    }

    // Inicializace přehrávače - volá se v onStart nebo onResume
    private fun initializePlayer() {
        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        if (videoUrl.isNullOrEmpty()) {
            Log.e(TAG, "Video URL is null or empty. Cannot initialize player.")
            Toast.makeText(this, "Chyba: URL videa není k dispozici.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        Log.d(TAG, "Initializing player with URL: $videoUrl")

        // Vytvoření instance ExoPlayeru
        player = ExoPlayer.Builder(this)
            .build()
            .also { exoPlayer ->
                binding.playerView.player = exoPlayer // Připojení přehrávače k PlayerView

                // Vytvoření MediaItem z URL
                val mediaItem = MediaItem.fromUri(videoUrl)
                exoPlayer.setMediaItem(mediaItem)

                // Nastavení listeneru pro sledování stavu přehrávače
                exoPlayer.addListener(playbackStateListener)

                // Obnovení stavu přehrávání
                exoPlayer.playWhenReady = playWhenReady
                exoPlayer.seekTo(mediaItemIndex, playbackPosition)
                exoPlayer.prepare() // Příprava přehrávače
                Log.d(TAG, "Player prepared, playWhenReady=$playWhenReady, seeking to $playbackPosition")
            }
    }

    // Uvolnění přehrávače - volá se v onStop nebo onPause
    private fun releasePlayer() {
        player?.let { exoPlayer ->
            // Uložení aktuálního stavu před uvolněním
            playbackPosition = exoPlayer.currentPosition
            mediaItemIndex = exoPlayer.currentMediaItemIndex
            playWhenReady = exoPlayer.playWhenReady
            exoPlayer.removeListener(playbackStateListener) // Odstranění listeneru
            exoPlayer.release() // Uvolnění zdrojů přehrávače
            Log.d(TAG, "Player released. Saved state: playWhenReady=$playWhenReady, itemIndex=$mediaItemIndex, position=$playbackPosition")
        }
        player = null // Nastavení na null
    }

    // Skrytí systémových lišt pro celoobrazovkový režim
    private fun hideSystemUi() {
        // Povolí zobrazení obsahu pod systémovými lištami
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Získání ovladače pro systémové lišty
        WindowInsetsControllerCompat(window, binding.playerView).let { controller ->
            // Skrytí systémových lišt (status bar, navigation bar)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            // Nastavení chování při gestu swipe (lišty se objeví dočasně)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        Log.d(TAG, "System UI hidden")
    }

    // Životní cyklus Activity a správa přehrávače
    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            initializePlayer()
        }
        Log.d(TAG, "onStart called")
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi() // Skrytí UI při návratu do popředí
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || player == null) {
            initializePlayer() // Inicializovat zde pro starší API nebo pokud ještě není inicializován
        }
        Log.d(TAG, "onResume called")
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            releasePlayer() // Uvolnit zde pro starší API
        }
        Log.d(TAG, "onPause called")
    }

    override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            releasePlayer() // Uvolnit zde pro API >= 24
        }
        Log.d(TAG, "onStop called")
    }

    // Uložení stavu přehrávače pro případ rekonfigurace
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Uložení stavu, i když se přehrávač uvolňuje v onStop/onPause
        outState.putBoolean(STATE_PLAY_WHEN_READY, playWhenReady)
        outState.putInt(STATE_MEDIA_ITEM_INDEX, mediaItemIndex)
        // Uložit aktuální pozici, pokud přehrávač existuje, jinak uložit poslední známou
        outState.putLong(STATE_PLAYBACK_POSITION, player?.currentPosition ?: playbackPosition)
        Log.d(TAG, "onSaveInstanceState: Saving state playWhenReady=$playWhenReady, itemIndex=$mediaItemIndex, position=${outState.getLong(STATE_PLAYBACK_POSITION)}")
    }

    // Listener pro sledování stavu přehrávače a chyb
    private val playbackStateListener: Player.Listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val stateString: String = when (playbackState) {
                ExoPlayer.STATE_IDLE -> "ExoPlayer.STATE_IDLE      -"
                ExoPlayer.STATE_BUFFERING -> "ExoPlayer.STATE_BUFFERING -"
                ExoPlayer.STATE_READY -> "ExoPlayer.STATE_READY     -"
                ExoPlayer.STATE_ENDED -> "ExoPlayer.STATE_ENDED     -"
                else -> "UNKNOWN_STATE             -"
            }
            Log.d(TAG, "changed state to $stateString")
            // Zde můžete reagovat na změny stavu, např. zobrazit/skrýt ProgressBar
            // binding.progressBarPlayer.visibility = if (playbackState == ExoPlayer.STATE_BUFFERING) View.VISIBLE else View.GONE
        }

        override fun onPlayerError(error: PlaybackException) {
            // Ošetření chyby přehrávání
            Log.e(TAG, "Player Error: ${error.errorCodeName} - ${error.message}", error) // Použití errorCodeName pro lepší info
            Toast.makeText(this@PlayerActivity, "Chyba přehrávání (${error.errorCodeName}): ${error.message}", Toast.LENGTH_LONG).show()
            // Můžete zde aktivitu ukončit nebo zobrazit chybovou zprávu
            // finish()
        }
    }
}
