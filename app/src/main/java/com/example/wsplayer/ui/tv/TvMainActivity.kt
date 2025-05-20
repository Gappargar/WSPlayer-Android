package com.example.wsplayer.ui.tv

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.FragmentActivity
// ViewModel a Factory pro přihlášení, pokud je budete používat místo přímého AuthTokenManageru
// import androidx.lifecycle.ViewModelProvider
// import com.example.wsplayer.ui.auth.LoginViewModel
// import com.example.wsplayer.ui.auth.LoginViewModelFactory
import com.example.wsplayer.AuthTokenManager // Váš AuthTokenManager
import com.example.wsplayer.R
import com.example.wsplayer.data.models.FileModel
import com.example.wsplayer.databinding.ActivityTvMainBinding // ViewBinding pro activity_tv_main.xml
import com.example.wsplayer.ui.settings.SettingsActivity // Pro spuštění nastavení
import java.text.NumberFormat

class TvMainActivity : FragmentActivity(), TvBrowseFragment.OnFileSelectedListener {

    private val TAG = "TvMainActivity"
    private lateinit var binding: ActivityTvMainBinding
    private lateinit var authTokenManager: AuthTokenManager // Používáme váš AuthTokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")

        authTokenManager = AuthTokenManager(this)
        val token = authTokenManager.getAuthToken()

        if (token.isNullOrEmpty()) {
            Log.w(TAG, "No token found, redirecting to CustomTvLoginActivity.")
            val loginIntent = Intent(this, CustomTvLoginActivity::class.java)
            loginIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(loginIntent)
            finish() // Ukončit tuto aktivitu, aby se na ni uživatel nemohl vrátit tlačítkem Zpět
            Log.d(TAG, "Finished TvMainActivity because user is not logged in.")
            return // Důležité ukončit onCreate zde
        }

        Log.d(TAG, "Token found, setting up main TV content.")
        binding = ActivityTvMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                // Ujistěte se, že R.id.tv_main_fragment_container odpovídá ID FrameLayoutu
                // ve vašem activity_tv_main.xml (dokument activity_tv_main_xml_with_nav_and_details)
                .replace(R.id.tv_main_fragment_container, TvBrowseFragment())
                .commitNow()
            Log.d(TAG, "TvBrowseFragment added to container.")
        } else {
            Log.d(TAG, "Restoring activity state, fragment should already be present.")
        }

        // Nastavení listenerů pro navigační menu
        setupNavigationMenuListeners()

        // Na začátku skryjeme panel detailů
        // Ujistěte se, že binding.detailsPanel odkazuje na správné ID z vašeho layoutu
        binding.detailsPanel.visibility = View.GONE
        Log.d(TAG, "onCreate finished for logged-in user.")
    }

    private fun setupNavigationMenuListeners() {
        // Ujistěte se, že ID tlačítek (nav_search_button, nav_settings_button)
        // odpovídají vašemu activity_tv_main.xml
        binding.navSearchButton.setOnClickListener {
            Log.d(TAG, "Search navigation button clicked.")
            // Spustíme CustomTvSearchActivity (předpokládáme, že existuje a je deklarována v manifestu)
            val intent = Intent(this, CustomTvSearchActivity::class.java)
            startActivity(intent)
        }

        binding.navSettingsButton.setOnClickListener {
            Log.d(TAG, "Settings navigation button clicked.")
            // TODO: V budoucnu zvážit TV specifickou obrazovku pro nastavení
            val intent = Intent(this, SettingsActivity::class.java) // Prozatím spouští mobilní verzi
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume started")
        // Znovu zkontrolovat přihlášení pro případ, že se uživatel odhlásil v jiné aktivitě
        // nebo se vrací z přihlašovací obrazovky.
        if (authTokenManager.getAuthToken().isNullOrEmpty()) {
            Log.w(TAG, "No token found onResume, redirecting to CustomTvLoginActivity.")
            val loginIntent = Intent(this, CustomTvLoginActivity::class.java)
            loginIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(loginIntent)
            finish()
        }
    }

    // Implementace listeneru z TvBrowseFragment pro zobrazení detailů
    override fun onFileSelectedInBrowse(file: FileModel?) {
        // Ujistěte se, že ID prvků v binding (tvDetailTitle, tvDetailType, atd.)
        // odpovídají vašemu activity_tv_main.xml
        if (file != null) {
            binding.tvDetailTitle.text = file.name
            binding.tvDetailType.text = "Typ: ${file.type?.uppercase() ?: "N/A"}"
            binding.tvDetailSize.text = "Velikost: ${formatFileSize(file.size)}"

            val positiveVotes = file.positive_votes ?: 0
            val negativeVotes = file.negative_votes ?: 0
            binding.tvDetailVotes.text = "Hlasy: +$positiveVotes / -$negativeVotes"

            var extraInfo = ""
            if (!file.videoQuality.isNullOrBlank()) extraInfo += "Kvalita: ${file.videoQuality} "
            if (!file.videoLanguage.isNullOrBlank()) extraInfo += "Jazyk: ${file.videoLanguage} "
            if (!file.displayDate.isNullOrBlank()) extraInfo += "(${file.displayDate})" // Datum z historie

            binding.tvDetailInfo.text = extraInfo.trim()
            binding.tvDetailInfo.visibility = if (extraInfo.isNotBlank()) View.VISIBLE else View.GONE

            binding.detailsPanel.visibility = View.VISIBLE
            Log.d(TAG, "Details updated for: ${file.name}")
        } else {
            binding.detailsPanel.visibility = View.GONE
            Log.d(TAG, "Details panel hidden.")
        }
    }

    // Pomocná funkce pro formátování velikosti souboru
    private fun formatFileSize(sizeInBytes: Long): String {
        if (sizeInBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
        val safeDigitGroups = digitGroups.coerceIn(0, units.size - 1)
        val nf = NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = 1
        }
        return "${nf.format(sizeInBytes / Math.pow(1024.0, safeDigitGroups.toDouble()))} ${units[safeDigitGroups]}"
    }
}
