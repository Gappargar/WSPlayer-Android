// app/src/main/java/com/example/wsplayer/ui/settings/SettingsActivity.kt
package com.example.wsplayer.ui.settings // Váš balíček pro SettingsActivity - ZKONTROLUJTE

import android.content.Context // Import pro Context
import android.content.Intent // Import pro Intent
import android.os.Bundle
import android.view.View // Import pro View
import android.widget.Toast // Import pro Toast zprávy
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer // Nechat pokud používáte Observer { } syntaxi, jinak smazat
import androidx.lifecycle.ViewModelProvider

// Import pro MainActivity (pro přesměrování zpět) (ZKONTROLUJTE CESTU)
import com.example.wsplayer.MainActivity

import com.example.wsplayer.R // Import pro string resources

// Importy pro ViewModel a Factory (ZKONTROLUJTE CESTU)
import com.example.wsplayer.ui.search.SearchViewModel // ViewModel se logout logikou
import com.example.wsplayer.ui.search.SearchViewModelFactory // Factory pro SearchViewModel

// Import pro View Binding (vygenerovaná třída) (ZKONTROLUJTE CESTU)
import com.example.wsplayer.databinding.ActivitySettingsBinding

// Importy pro Repository, AuthTokenManager, ApiService - potřeba pro Factory volání (ZKONTROLUJTE CESTU)
import com.example.wsplayer.data.repository.WebshareRepository
import com.example.wsplayer.AuthTokenManager // Import AuthTokenManager
import com.example.wsplayer.data.api.WebshareApiService // Import ApiService


// Activity pro obrazovku nastavení
// Spouští se ze SearchActivity
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    // Použijeme SearchViewModel, protože už má implementovanou logout logiku a isUserLoggedIn LiveData
    private lateinit var viewModel: SearchViewModel // Toto je SearchViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        println("SettingsActivity: >>> onCreate spuštěn. PID: ${android.os.Process.myPid()}") // Log

        // --- Nastavení UI pomocí View Bindingu ---
        // Předpokládá, že máte activity_settings.xml a v něm povolený View Binding
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root) // Použití binding.root
        println("SettingsActivity: ContentView nastaven.")

        // --- Inicializace ViewModelu (použijeme SearchViewModel) pomocí Factory ---
        // SearchViewModelFactory potřebuje Context a ApiService pro vytvoření Repository uvnitř
        // Použijeme stejnou Factory jako SearchActivity.
        // Zkontrolujte cesty k ApiService a Factory
        val apiService = WebshareApiService.create() // Zkontrolujte cestu k ApiService
        val viewModelFactory = SearchViewModelFactory(applicationContext, apiService) // Zkontrolujte cestu k Factory
        viewModel = ViewModelProvider(this, viewModelFactory).get(SearchViewModel::class.java) // Získání SearchViewModelu
        println("SettingsActivity: ViewModel (SearchViewModel) získán.")


        // --- Sledování stavu přihlášení z ViewModelu ---
        // Toto zajistí přesměrování na login obrazovku, pokud se uživatel odhlásí (např. voláním logout)
        println("SettingsActivity: Nastavuji Observer pro viewModel.isUserLoggedIn.") // Log
        viewModel.isUserLoggedIn.observe(this) { isLoggedIn ->
            println("SettingsActivity: Observer isUserLoggedIn spuštěn. isUserLoggedIn: $isLoggedIn") // Log stavu
            if (!isLoggedIn) { // Pokud ViewModel signalizuje, že uživatel NENÍ přihlášen (token byl smazán)
                println("SettingsActivity: isUserLoggedIn je false. Přesměrovávám na LoginActivity.")
                // Přesměrovat zpět na LoginActivity a vymazat Task Stack
                val intent = Intent(this, MainActivity::class.java) // Intent pro MainActivity (ZKONTROLUJTE CESTU)
                // Tyto flags zajistí, že se LoginActivity stane novým kořenem Tasku
                // a SettingsActivity (a vše ostatní v jejím Tasku) bude ukončeno.
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish() // **Ukončit SettingsActivity**
                println("SettingsActivity: finish() voláno po přesměrování na LoginActivity.")
            } else {
                // Uživatel je přihlášen (má token) - nic nedělat, zůstat na SettingsActivity
                println("SettingsActivity: isUserLoggedIn je true (token nalezen). Zůstávám na SettingsActivity.")
            }
        }
        println("SettingsActivity: Observer isUserLoggedIn nastaven.")


        // --- Nastavení listeneru na tlačítko Odhlásit se ---
        // Předpokládá, že máte v activity_settings.xml tlačítko s ID @+id/buttonLogout
        println("SettingsActivity: Nastavuji OnClickListener pro tlačítko Odhlásit.") // Log
        binding.buttonLogout.setOnClickListener { // **Zkontrolujte ID tlačítka v activity_settings.xml**
            println("SettingsActivity: Kliknuto na tlačítko Odhlásit.") // Log
            // **Volání metody logout ve ViewModelu**
            viewModel.logout() // <- Volání metody logout na SearchViewModelu
            println("SettingsActivity: Voláno viewModel.logout().") // Log
            // Observer výše se postará o přesměrování po dokončení logoutu ve ViewModelu.
        }

        // TODO: Přidejte další UI prvky a logiku pro nastavení zde...
        // Např. zobrazit info o uživateli z ViewModelu, atd.
        // SearchViewModel obsahuje LiveData pro uživatelská data, pokud jste ji implementoval(a).
        /*
        viewModel.userData.observe(this) { userData ->
            // Aktualizujte UI SettingsActivity s detaily o uživateli
        }
        */

        println("SettingsActivity: <<< onCreate dokončen.")
    }

    override fun onDestroy() {
        println("SettingsActivity: >>> onDestroy spuštěn. PID: ${android.os.Process.myPid()}") // Log
        // Uvolněte zdroje specifické pro SettingsActivity, pokud nějaké máte
        super.onDestroy()
        println("SettingsActivity: <<< onDestroy dokončen.") // Log
    }

    // TODO: SettingsActivity potřebuje svůj vlastní layout activity_settings.xml
    // který obsahuje UI prvky jako je tlačítko Odhlásit se (s odpovídajícím ID buttonLogout).
    // Ujistěte se, že máte povolený View Binding pro activity_settings.xml v build.gradle.
}