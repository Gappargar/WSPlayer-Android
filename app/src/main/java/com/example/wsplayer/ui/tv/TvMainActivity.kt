package com.example.wsplayer.ui.tv // Ujistěte se, že balíček odpovídá

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View // <-- Přidán import
import androidx.fragment.app.FragmentActivity
import com.example.wsplayer.AuthTokenManager
import com.example.wsplayer.R
import com.example.wsplayer.data.models.FileModel // <-- Přidán import
import com.example.wsplayer.databinding.ActivityTvMainBinding // <-- ViewBinding pro TV Main
import java.text.NumberFormat // <-- Pro formátování velikosti

/**
 * Hlavní aktivita pro Android TV verzi.
 * Zkontroluje přihlášení a zobrazí buď přihlašovací obrazovku, nebo hlavní obsah.
 * Implementuje OnFileSelectedListener pro zobrazení detailů.
 */
class TvMainActivity : FragmentActivity(), TvBrowseFragment.OnFileSelectedListener {

    private val TAG = "TvMainActivity"
    private lateinit var binding: ActivityTvMainBinding // ViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")

        val authTokenManager = AuthTokenManager(this)
        val token = authTokenManager.getAuthToken()

        if (token.isNullOrEmpty()) {
            Log.w(TAG, "No token found, redirecting to CustomTvLoginActivity.")
            val loginIntent = Intent(this, CustomTvLoginActivity::class.java)
            startActivity(loginIntent)
            finish()
            Log.d(TAG, "Finished TvMainActivity because user is not logged in.")
            return
        }

        Log.d(TAG, "Token found, setting up main TV content.")
        // Použití ViewBindingu
        binding = ActivityTvMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.tv_main_fragment_container, TvBrowseFragment())
                .commitNow()
            Log.d(TAG, "TvBrowseFragment added to container.")
        } else {
            Log.d(TAG, "Restoring activity state, fragment should already be present.")
        }
        Log.d(TAG, "onCreate finished for logged-in user.")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume started")
        val authTokenManager = AuthTokenManager(this)
        if (authTokenManager.getAuthToken().isNullOrEmpty()) {
            Log.w(TAG, "No token found onResume, redirecting to CustomTvLoginActivity.")
            val loginIntent = Intent(this, CustomTvLoginActivity::class.java)
            startActivity(loginIntent)
            finish()
        }
    }

    // ***** IMPLEMENTACE INTERFACE OnFileSelectedListener *****
    override fun onFileSelectedInBrowse(file: FileModel?) {
        if (file != null) {
            binding.tvDetailTitle.text = file.name
            binding.tvDetailType.text = "Typ: ${file.type?.uppercase() ?: "N/A"}" // Ošetření null typu
            binding.tvDetailSize.text = "Velikost: ${formatFileSize(file.size)}"
            // Ošetření null pro hlasy, pokud by v FileModel byly nullable
            val positiveVotes = file.positive_votes ?: 0
            val negativeVotes = file.negative_votes ?: 0
            binding.tvDetailVotes.text = "Hlasy: +$positiveVotes / -$negativeVotes"
            // Můžete přidat další detaily, např.
            // binding.tvDetailPassword.text = if (file.password == 1) "Chráněno heslem" else "Bez hesla"

            binding.detailsPanel.visibility = View.VISIBLE
            Log.d(TAG, "Details updated for: ${file.name}")
        } else {
            binding.detailsPanel.visibility = View.GONE
            Log.d(TAG, "Details panel hidden.")
        }
    }
    // ********************************************************

    // Pomocná funkce pro formátování velikosti souboru (můžete ji přesunout do Utils)
    private fun formatFileSize(sizeInBytes: Long): String {
        if (sizeInBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
        val safeDigitGroups = digitGroups.coerceIn(0, units.size - 1)
        // Použití NumberFormat pro lokalizované formátování desetinných míst
        val nf = NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = 1
        }
        return "${nf.format(sizeInBytes / Math.pow(1024.0, safeDigitGroups.toDouble()))} ${units[safeDigitGroups]}"
    }
}
