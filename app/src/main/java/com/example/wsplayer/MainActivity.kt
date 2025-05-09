package com.example.wsplayer // Váš balíček pro MainActivity - ZKONTROLUJTE

import android.app.UiModeManager // Import pro UiModeManager
import android.content.Context // Import pro Context
import android.content.Intent // Import pro Intent
import android.content.res.Configuration // Import pro Configuration
import android.os.Bundle
import android.os.Handler // Import pro Handler (pro zpoždění)
import android.os.Looper // Import pro Looper (pro zpoždění)
import android.util.Log // Import pro Logování
import android.view.View // Import pro View (GONE, VISIBLE)
import android.view.inputmethod.InputMethodManager // Import pro skrývání klávesnice
import android.widget.Toast // Import pro Toast zprávy
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider

// Importy pro ViewModel a Factory (ZKONTROLUJTE CESTU)
import com.example.wsplayer.ui.auth.LoginViewModel
import com.example.wsplayer.ui.auth.LoginViewModelFactory

// Importy pro AuthTokenManager a ApiService - potřeba pro Factory volání (ZKONTROLUJTE CESTU)
import com.example.wsplayer.AuthTokenManager // Import AuthTokenManager
import com.example.wsplayer.data.api.WebshareApiService // Import ApiService

// Import pro SearchActivity (pro přesměrování) (ZKONTROLUJTE CESTU)
import com.example.wsplayer.ui.search.SearchActivity

// Import pro R třídu (pro string resources)
import com.example.wsplayer.R

// Import pro View Binding (vygenerovaná třída) (ZKONTROLUJTE CESTU)
import com.example.wsplayer.databinding.ActivityMainBinding

// Import pro TV aktivity (UPRAVTE CESTY, POKUD JE TO NUTNÉ)
import com.example.wsplayer.ui.tv.CustomTvLoginActivity // Používáme novou CustomTvLoginActivity
import com.example.wsplayer.ui.tv.TvMainActivity


// Hlavní aktivita aplikace - slouží jako přihlašovací obrazovka nebo brána pro TV
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: LoginViewModel
    private val TAG = "MainActivity" // Přidán TAG pro logování

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, ">>> onCreate spuštěn. PID: ${android.os.Process.myPid()}")

        // --- Detekce Android TV ---
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            Log.d(TAG, "Detekováno TV zařízení.")
            // Zkontrolovat, zda je uživatel přihlášen
            val authTokenManager = AuthTokenManager(this) // Vytvoření instance pro kontrolu tokenu
            val token = authTokenManager.getAuthToken()

            if (token.isNullOrEmpty()) {
                // Není přihlášen -> Spustit CustomTvLoginActivity
                Log.d(TAG, "Není token, spouštím CustomTvLoginActivity.")
                val loginIntent = Intent(this, CustomTvLoginActivity::class.java) // Změna na CustomTvLoginActivity
                startActivity(loginIntent)
            } else {
                // Je přihlášen -> Spustit TvMainActivity
                Log.d(TAG, "Token nalezen, spouštím TvMainActivity.")
                val tvIntent = Intent(this, TvMainActivity::class.java)
                // Vyčistit task a nastavit TvMainActivity jako nový kořen
                tvIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(tvIntent)
            }
            finish() // Ukončit tuto MainActivity, protože jsme přesměrovali na TV aktivitu
            Log.d(TAG, "MainActivity (mobilní brána) ukončena pro TV.")
            return   // Důležité, aby se nevykonal zbytek onCreate pro mobilní verzi
        }

        // --- Pokud nejsme na TV, pokračuj s normálním nastavením pro mobilní MainActivity ---
        Log.d(TAG, "Detekováno mobilní zařízení. Nastavuji mobilní UI.")
        setupMobileUi()
    }

    private fun setupMobileUi() {
        // --- Nastavení uživatelského rozhraní pomocí View Bindingu ---
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "Mobilní ContentView nastaven.")

        // --- Inicializace ViewModelu pomocí Factory ---
        val apiService = WebshareApiService.create()
        val viewModelFactory = LoginViewModelFactory(applicationContext, apiService)
        viewModel = ViewModelProvider(this, viewModelFactory)[LoginViewModel::class.java]


        // --- Sledování stavu přihlašování z ViewModelu ---
        Log.d(TAG, "Nastavuji Observer pro viewModel.loginState.")
        viewModel.loginState.observe(this) { state ->
            Log.d(TAG, "Observer loginState spuštěn. Aktuální stav: $state")
            when (state) {
                is LoginViewModel.LoginState.Idle -> {
                    Log.d(TAG, "LoginState je Idle.")
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
                    Log.d(TAG, "LoginState je Loading.")
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
                    Log.d(TAG, ">>> Observer LoginState.Success spuštěn. Token získán.")
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

                    Log.d(TAG, "Spouštím SearchActivity s flags NEW_TASK | CLEAR_TASK.")
                    val intent = Intent(this, SearchActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    Log.d(TAG, "SearchActivity voláno startActivity().")

                    Log.d(TAG, "finish() ZAVOLÁNO po krátkém zpoždění (200ms).")
                    Handler(Looper.getMainLooper()).postDelayed({
                        Log.d(TAG, "finish() provádím z Handleru.")
                        finish()
                        Log.d(TAG, "finish() volání dokončeno z Handleru.")
                    }, 200)
                }
                is LoginViewModel.LoginState.Error -> {
                    Log.d(TAG, "LoginState je Error.")
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
        Log.d(TAG, "Nastavuji OnClickListener pro tlačítko Přihlásit.")
        binding.buttonLogin.setOnClickListener {
            Log.d(TAG, "Kliknuto na tlačítko Přihlásit.")
            val username = binding.editTextUsername.text.toString().trim()
            val password = binding.editTextPassword.text.toString()
            val rememberMe = binding.checkBoxRememberMe.isChecked

            viewModel.login(username, password, rememberMe)

            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
            Log.d(TAG, "Klávesnice skryta.")
        }

        binding.logoutButton.setOnClickListener {
            Log.d(TAG, "Kliknuto na tlačítko Odhlásit (v MainActivity, nemělo by zde být).")
        }

        Log.d(TAG, "<<< Mobilní UI onCreate dokončen.")
    }


    override fun onDestroy() {
        Log.d(TAG, ">>> onDestroy spuštěn.")
        super.onDestroy()
        Log.d(TAG, "<<< onDestroy dokončen.")
    }
}
