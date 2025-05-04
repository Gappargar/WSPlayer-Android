package com.example.wsplayer.ui.settings // Váš balíček + .ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View // Import pro View (pokud se používá)
import android.widget.Toast // Import pro Toast (pokud se používá)
import androidx.appcompat.app.AppCompatActivity // Základní třída Activity
import androidx.lifecycle.Observer // Import Observeru pro LiveData
import androidx.lifecycle.ViewModelProvider // Import ViewModelProvider

// Importy pro získání LoginViewModelu
import com.example.wsplayer.ui.auth.LoginViewModel
import com.example.wsplayer.ui.auth.LoginViewModelFactory

import com.example.wsplayer.databinding.ActivitySettingsBinding // Import Binding třídy pro activity_settings.xml
import com.example.wsplayer.MainActivity // Import pro MainActivity (není potřeba pro spuštění Intentu odsud v nové strategii)


// Importy pro Repository a ApiService (pro Factory) - pokud je SettingsActivity Factory jinde než v ui.settings
// import com.example.wsplayer.data.api.WebshareApiService
// import com.example.wsplayer.data.repository.WebshareRepository
// import com.example.wsplayer.AuthTokenManager


// Activity pro zobrazení nastavení
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    // **PROMĚNNÁ PRO LoginViewModel**
    private lateinit var loginViewModel: LoginViewModel


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Nastavení UI pomocí View Bindingu ---
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Nastavení nadpisu ActionBaru (volitelné)
        supportActionBar?.title = "Nastavení"

        // --- Inicializace ViewModelu ---
        val apiService = com.example.wsplayer.data.api.WebshareApiService.create()
        loginViewModel = ViewModelProvider(this, LoginViewModelFactory(applicationContext, apiService))
            .get(LoginViewModel::class.java)


        // **Nastavení posluchače na tlačítko Odhlásit se**
        binding.buttonLogout.setOnClickListener { // <- Zde se nastavuje posluchač
            println("SettingsActivity: Kliknuto na Odhlásit se.") // Log

            // Zavolá ViewModel metodu pro odhlášení (ta smaže token/credentials lokálně a zavolá API)
            loginViewModel.logout()

            // **Expliciní ukončení SettingsActivity**
            finish() // <-- EXPLICITNÍ UKONČENÍ TÉTO ACTIVITY

            // **Spuštění MainActivity (přihlašovací obrazovky) s VLAKAMI PRO NOVÝ, VYČIŠTĚNÝ ZÁSOBNÍK**
            // Toto by se mělo spustit AŽ po pokusu o ukončení SettingsActivity
            val intent = Intent(this, MainActivity::class.java) // Intent pro MainActivity
            // Vlajky: NEW_TASK spustí MainActivity v novém zásobníku, CLEAR_TASK ukončí všechny aktivity ve starém zásobníku (SearchActivity, SettingsActivity)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // <- Tyto vlajky

            startActivity(intent) // Spustí MainActivity

            // Observer níže na LoginState.Idle v této aktivitě už nebude potřeba pro volání finish(),
            // protože finish() voláme explicitně zde. Můžete ho odstranit nebo ponechat pro debug.
        }

        // **Observer pro stav přihlášení z LoginViewModelu - pro samo-ukončení při odhlášení (ZDE UŽ NENÍ POTŘEBA SPUSŤOVAT INTENT)**
        // Tento observer v SettingsActivity nyní primárně jen loguje, protože finish() voláme explicitně výše.
        // Ale ponecháme ho pro konzistenci.
        loginViewModel.loginState.observe(this, Observer { state ->
            if (state is LoginViewModel.LoginState.Idle) { // Pokud je stav Idle (což signalizuje logout)
                println("SettingsActivity: LoginState je Idle (z observeru).") // Log
                // finish() // Tady už nevoláme finish() ani Intent, voláme finish() a Intent výše v posluchači kliknutí
            }
        })


        // TODO: Nastavit posluchače pro další prvky nastavení
    }

    // TODO: Zvážit, zda je potřeba přepsat onResume, onPause, onDestroy
}