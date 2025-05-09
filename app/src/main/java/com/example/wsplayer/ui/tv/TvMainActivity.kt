package com.example.wsplayer.ui.tv // Ujistěte se, že balíček odpovídá

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity // Leanback aktivity často dědí z FragmentActivity
import com.example.wsplayer.AuthTokenManager // Import pro AuthTokenManager
import com.example.wsplayer.R // Váš R soubor
import com.example.wsplayer.ui.tv.CustomTvLoginActivity // <-- Import pro NOVOU přihlašovací aktivitu
import com.example.wsplayer.ui.tv.TvBrowseFragment

/**
 * Hlavní aktivita pro Android TV verzi.
 * Zkontroluje přihlášení a zobrazí buď přihlašovací obrazovku, nebo hlavní obsah.
 */
class TvMainActivity : FragmentActivity() {

    private val TAG = "TvMainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")

        // Kontrola přihlášení hned na začátku
        val authTokenManager = AuthTokenManager(this)
        val token = authTokenManager.getAuthToken()

        if (token.isNullOrEmpty()) {
            // Není přihlášen -> Přesměrovat na CustomTvLoginActivity
            Log.w(TAG, "No token found, redirecting to CustomTvLoginActivity.")
            val loginIntent = Intent(this, CustomTvLoginActivity::class.java)
            startActivity(loginIntent)
            finish() // Ukončit tuto aktivitu, dokud se uživatel nepřihlásí
            Log.d(TAG, "Finished TvMainActivity because user is not logged in.")
            return   // Nepokračovat v nastavování UI pro TvMainActivity
        }

        // Pokud je přihlášen, pokračovat normálně
        Log.d(TAG, "Token found, setting up main TV content.")
        setContentView(R.layout.activity_tv_main) // Nastaví layout s kontejnerem

        if (savedInstanceState == null) {
            // Vložení hlavního fragmentu (např. TvBrowseFragment)
            supportFragmentManager.beginTransaction()
                .replace(R.id.tv_main_fragment_container, TvBrowseFragment()) // Použití importované třídy
                .commitNow()
            Log.d(TAG, "TvBrowseFragment added to container.")
        } else {
            Log.d(TAG, "Restoring activity state, fragment should already be present.")
        }
        Log.d(TAG, "onCreate finished for logged-in user.")
    }

    // Můžete přidat onResume pro případ, že by se token smazal, zatímco je appka na pozadí
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume started")
        // Znovu zkontrolovat token pro případ, že byl smazán jinde (např. odhlášení v nastavení)
        val authTokenManager = AuthTokenManager(this)
        if (authTokenManager.getAuthToken().isNullOrEmpty()) {
            Log.w(TAG, "No token found onResume, redirecting to CustomTvLoginActivity.")
            val loginIntent = Intent(this, CustomTvLoginActivity::class.java)
            startActivity(loginIntent)
            finish()
        }
    }
}
