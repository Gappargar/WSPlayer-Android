package com.example.wsplayer // Váš balíček pro MainActivity - ZKONTROLUJTE

import android.app.UiModeManager // Import pro UiModeManager
import android.content.Context // Import pro Context
import android.content.Intent // Import pro Intent
import android.content.res.Configuration // Import pro Configuration
import android.os.Bundle
import android.os.Handler // Import pro Handler (pro zpoždění)
import android.os.Looper // Import pro Looper (pro zpoždění)
import android.view.View // Import pro View (GONE, VISIBLE)
import android.view.inputmethod.InputMethodManager // Import pro skrývání klávesnice
import android.widget.Toast // Import pro Toast zprávy
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider

// Importy pro ViewModel a Factory (ZKONTROLUJTE CESTU)
import com.example.wsplayer.ui.auth.LoginViewModel
import com.example.wsplayer.ui.auth.LoginViewModelFactory

// Importy pro Repository, AuthTokenManager, ApiService - potřeba pro Factory volání (ZKONTROLUJTE CESTU)
// import com.example.wsplayer.data.repository.WebshareRepository // Není přímo potřeba zde, pokud vše řeší Factory
// import com.example.wsplayer.AuthTokenManager // Není přímo potřeba zde
import com.example.wsplayer.data.api.WebshareApiService // Import ApiService

// Import pro SearchActivity (pro přesměrování) (ZKONTROLUJTE CESTU)
import com.example.wsplayer.ui.search.SearchActivity

// Import pro R třídu (pro string resources)
import com.example.wsplayer.R

// Import pro View Binding (vygenerovaná třída) (ZKONTROLUJTE CESTU)
import com.example.wsplayer.databinding.ActivityMainBinding

// Import pro vaši budoucí TV aktivitu (UPRAVTE CESTU, POKUD JE JINÁ)
// import com.example.wsplayer.ui.tv.TvMainActivity


// Hlavní aktivita aplikace - slouží jako přihlašovací obrazovka
class MainActivity : AppCompatActivity() {

    // Binding pro UI prvky v activity_main.xml
    private lateinit var binding: ActivityMainBinding

    // ViewModel pro správu stavu přihlášení
    private lateinit var viewModel: LoginViewModel

    // --- Metoda volaná při vytvoření Activity ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        println("MainActivity: >>> onCreate spuštěn. PID: ${android.os.Process.myPid()}")

        // --- Detekce Android TV ---
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            println("MainActivity: Detekováno TV zařízení. Spouštím TvMainActivity.")
            // Předpokládáme, že vaše TV aktivita je com.example.wsplayer.ui.tv.TvMainActivity
            // Pokud ji ještě nemáte, toto způsobí chybu při spuštění na TV.
            // Nezapomeňte ji vytvořit a zaregistrovat v AndroidManifest.xml s LEANBACK_LAUNCHER.
            try {
                val tvIntent = Intent()
                // Použití plně kvalifikovaného názvu třídy pro Intent, aby se předešlo problémům s importy, pokud třída ještě neexistuje
                tvIntent.setClassName(this, "com.example.wsplayer.ui.tv.TvMainActivity")
                tvIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(tvIntent)
            } catch (e: Exception) {
                // Pokud TvMainActivity ještě neexistuje nebo je špatně nakonfigurována, zobrazíme chybu
                // a pokračujeme s mobilním UI, aby aplikace nespadla.
                println("MainActivity: Chyba při spouštění TvMainActivity: ${e.message}")
                Toast.makeText(this, "Chyba spouštění TV rozhraní, zobrazuji mobilní.", Toast.LENGTH_LONG).show()
                setupMobileUi() // Pokračovat s mobilním UI jako fallback
                return // Ukončit zde, pokud setupMobileUi() neobsahuje return
            }
            finish() // Ukončíme mobilní MainActivity, aby nebyla v zásobníku
            return   // Důležité, aby se nevykonal zbytek onCreate pro mobilní verzi
        }

        // --- Pokud nejsme na TV, pokračuj s normálním nastavením pro mobilní MainActivity ---
        println("MainActivity: Detekováno mobilní zařízení. Nastavuji mobilní UI.")
        setupMobileUi()
    }

    private fun setupMobileUi() {
        // --- Nastavení uživatelského rozhraní pomocí View Bindingu ---
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        println("MainActivity: Mobilní ContentView nastaven.")

        // --- Inicializace ViewModelu pomocí Factory ---
        val apiService = WebshareApiService.create()
        val viewModelFactory = LoginViewModelFactory(applicationContext, apiService)
        viewModel = ViewModelProvider(this, viewModelFactory)[LoginViewModel::class.java]


        // --- Sledování stavu přihlašování z ViewModelu ---
        println("MainActivity: Nastavuji Observer pro viewModel.loginState.")
        viewModel.loginState.observe(this) { state ->
            println("MainActivity: Observer loginState spuštěn. Aktuální stav: $state")
            when (state) {
                is LoginViewModel.LoginState.Idle -> {
                    println("MainActivity: LoginState je Idle.")
                    binding.progressBar.visibility = View.GONE
                    binding.buttonLogin.isEnabled = true
                    binding.errorText.visibility = View.GONE
                    binding.loggedInInfo.visibility = View.GONE

                    binding.editTextUsername.visibility = View.VISIBLE
                    binding.editTextPassword.visibility = View.VISIBLE
                    binding.checkBoxRememberMe.visibility = View.VISIBLE
                    binding.buttonLogin.visibility = View.VISIBLE
                    binding.textViewTitle.visibility = View.VISIBLE
                    binding.logoutButton.visibility = View.GONE
                }
                is LoginViewModel.LoginState.Loading -> {
                    println("MainActivity: LoginState je Loading.")
                    binding.progressBar.visibility = View.VISIBLE
                    binding.buttonLogin.isEnabled = false
                    binding.errorText.visibility = View.GONE
                    binding.loggedInInfo.visibility = View.GONE

                    binding.editTextUsername.visibility = View.GONE
                    binding.editTextPassword.visibility = View.GONE
                    binding.checkBoxRememberMe.visibility = View.GONE
                    binding.buttonLogin.visibility = View.GONE
                    binding.textViewTitle.visibility = View.GONE
                }
                is LoginViewModel.LoginState.Success -> {
                    println("MainActivity: >>> Observer LoginState.Success spuštěn. Token získán.")
                    binding.progressBar.visibility = View.GONE
                    binding.buttonLogin.isEnabled = true

                    binding.editTextUsername.visibility = View.GONE
                    binding.editTextPassword.visibility = View.GONE
                    binding.checkBoxRememberMe.visibility = View.GONE
                    binding.buttonLogin.visibility = View.GONE
                    binding.textViewTitle.visibility = View.GONE
                    binding.errorText.visibility = View.GONE
                    binding.loggedInInfo.visibility = View.GONE

                    Toast.makeText(this, getString(R.string.login_success_toast), Toast.LENGTH_SHORT).show()

                    println("MainActivity: Spouštím SearchActivity s flags NEW_TASK | CLEAR_TASK.")
                    val intent = Intent(this, SearchActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    println("MainActivity: SearchActivity voláno startActivity().")

                    println("MainActivity: finish() ZAVOLÁNO po krátkém zpoždění (200ms).")
                    Handler(Looper.getMainLooper()).postDelayed({
                        println("MainActivity: finish() provádím z Handleru.")
                        finish()
                        println("MainActivity: finish() volání dokončeno z Handleru.")
                    }, 200)
                }
                is LoginViewModel.LoginState.Error -> {
                    println("MainActivity: LoginState je Error.")
                    binding.progressBar.visibility = View.GONE
                    binding.buttonLogin.isEnabled = true

                    binding.errorText.text = getString(R.string.login_error_message, state.message)
                    binding.errorText.visibility = View.VISIBLE
                    binding.loggedInInfo.visibility = View.GONE

                    binding.editTextUsername.visibility = View.VISIBLE
                    binding.editTextPassword.visibility = View.VISIBLE
                    binding.checkBoxRememberMe.visibility = View.VISIBLE
                    binding.buttonLogin.visibility = View.VISIBLE
                    binding.textViewTitle.visibility = View.VISIBLE
                    binding.logoutButton.visibility = View.GONE

                    Toast.makeText(this, getString(R.string.login_error_toast, state.message), Toast.LENGTH_LONG).show()
                }
            }
        }

        // --- Nastavení posluchače na tlačítko přihlášení ---
        println("MainActivity: Nastavuji OnClickListener pro tlačítko Přihlásit.")
        binding.buttonLogin.setOnClickListener {
            println("MainActivity: Kliknuto na tlačítko Přihlásit.")
            val username = binding.editTextUsername.text.toString().trim()
            val password = binding.editTextPassword.text.toString()
            val rememberMe = binding.checkBoxRememberMe.isChecked

            viewModel.login(username, password, rememberMe)

            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
            println("MainActivity: Klávesnice skryta.")
        }

        binding.logoutButton.setOnClickListener {
            println("MainActivity: Kliknuto na tlačítko Odhlásit (v MainActivity, nemělo by zde být).")
        }

        println("MainActivity: <<< Mobilní UI onCreate dokončen.")
    }


    // Metoda volaná, když Activity končí (např. po finish())
    override fun onDestroy() {
        println("MainActivity: >>> onDestroy spuštěn.")
        super.onDestroy()
        println("MainActivity: <<< onDestroy dokončen.")
    }
}
