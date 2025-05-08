package com.example.wsplayer.ui.tv // Ujistěte se, že balíček odpovídá

import android.os.Bundle
import androidx.fragment.app.FragmentActivity // Leanback aktivity často dědí z FragmentActivity
import com.example.wsplayer.R // Váš R soubor

// Předpokládáme, že TvBrowseFragment bude ve stejném balíčku
// import com.example.wsplayer.ui.tv.TvBrowseFragment

class TvMainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_main) // Nastaví layout activity_tv_main.xml

        if (savedInstanceState == null) {
            // Vytvoření a vložení instance TvBrowseFragment
            // TvBrowseFragment je váš vlastní fragment, který bude obsahovat Leanback UI
            supportFragmentManager.beginTransaction()
                .replace(R.id.tv_main_fragment_container, TvBrowseFragment()) // Vložení TvBrowseFragment
                .commitNow()
        }
    }
}
