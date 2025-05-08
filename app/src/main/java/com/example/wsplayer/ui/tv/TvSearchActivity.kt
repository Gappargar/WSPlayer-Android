package com.example.wsplayer.ui.tv // Ujistěte se, že balíček odpovídá

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.example.wsplayer.R

/**
 * Aktivita, která hostuje TvSearchFragment pro vyhledávání na Android TV.
 */
class TvSearchActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_search) // Budeme potřebovat layout pro tuto aktivitu

        if (savedInstanceState == null) {
            // Vložení TvSearchFragment do kontejneru v layoutu
            supportFragmentManager.beginTransaction()
                .replace(R.id.tv_search_fragment_container, TvSearchFragment()) // Předpokládáme ID kontejneru
                .commitNow()
        }
        // Poznámka: SearchSupportFragment si sám vyžádá oprávnění pro mikrofon,
        // pokud je hlasové vyhledávání podporováno a povoleno.
    }

    // Můžeme zachytit výsledek vyhledávání, pokud by TvSearchFragment nekomunikoval přes ViewModel
    // override fun onSearchRequested(): Boolean {
    //     // ... (logika pro spuštění vyhledávání, pokud je potřeba)
    //     return true
    // }
}
