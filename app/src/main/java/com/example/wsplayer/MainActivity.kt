package com.example.wsplayer // Váš balíček + .ui.auth - ZKONTROLUJTE

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.wsplayer.data.api.WebshareApiService
import com.example.wsplayer.data.repository.WebshareRepository
import com.example.wsplayer.AuthTokenManager
import android.os.Handler
import android.os.Looper
import com.example.wsplayer.databinding.ActivityMainBinding
import com.example.wsplayer.ui.search.SearchActivity // Import SearchActivity
import com.example.wsplayer.R // Import pro přístup k řetězcovým zdrojům
import com.example.wsplayer.ui.auth.LoginViewModel
import com.example.wsplayer.ui.auth.LoginViewModelFactory

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: LoginViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- Inicializace ViewModelu pomocí Factory ---
        val apiService = WebshareApiService.create()
        val viewModelFactory = LoginViewModelFactory(applicationContext, apiService)
        viewModel = ViewModelProvider(this, viewModelFactory).get(LoginViewModel::class.java)

        // --- Sledování stavu přihlašování z ViewModelu (zjednodušená syntaxe observeru) ---
        viewModel.loginState.observe(this) { state ->
            when (state) {
                is LoginViewModel.LoginState.Idle -> {
                    binding.progressBar.visibility = View.GONE
                    binding.buttonLogin.isEnabled = true
                    binding.errorText.visibility = View.GONE // Opraven název
                    binding.loggedInInfo.visibility = View.GONE

                    binding.editTextUsername.visibility = View.VISIBLE
                    binding.editTextPassword.visibility = View.VISIBLE
                    binding.checkBoxRememberMe.visibility = View.VISIBLE
                    binding.buttonLogin.visibility = View.VISIBLE // Opraven název
                    binding.textViewTitle.visibility = View.VISIBLE

                    binding.buttonLogin.visibility = View.VISIBLE // Opraven název
                    binding.logoutButton.visibility = View.GONE // Opraven název
                }
                is LoginViewModel.LoginState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.buttonLogin.isEnabled = false // Opraven název
                    binding.errorText.visibility = View.GONE // Opraven název
                    binding.loggedInInfo.visibility = View.GONE

                    binding.editTextUsername.visibility = View.GONE
                    binding.editTextPassword.visibility = View.GONE
                    binding.checkBoxRememberMe.visibility = View.GONE
                    binding.buttonLogin.visibility = View.GONE // Opraven název
                    binding.textViewTitle.visibility = View.GONE
                }
                is LoginViewModel.LoginState.Success -> {
                    println("MainActivity: >>> Observer LoginState.Success spuštěn.")

                    binding.progressBar.visibility = View.GONE
                    binding.buttonLogin.isEnabled = true // Opraven název

                    binding.editTextUsername.visibility = View.GONE
                    binding.editTextPassword.visibility = View.GONE
                    binding.checkBoxRememberMe.visibility = View.GONE
                    binding.buttonLogin.visibility = View.GONE // Opraven název
                    binding.textViewTitle.visibility = View.GONE
                    binding.errorText.visibility = View.GONE // Opraven název
                    binding.loggedInInfo.visibility = View.GONE // Opraven název

                    Toast.makeText(this, getString(R.string.login_success_toast), Toast.LENGTH_SHORT).show() // Použití string resource

                    // --- Přejít na SearchActivity a vymazat Task ---
                    println("MainActivity: Spouštím SearchActivity s flags CATEGORY_HOME | NEW_TASK.")
                    val intent = Intent(this, SearchActivity::class.java)
                    intent.addCategory(Intent.CATEGORY_HOME) // Nastaví ji jako "domovskou" v novém tasku
                    intent.addCategory(Intent.CATEGORY_DEFAULT) // Standardní defaultní kategorie
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Vždy potřebujeme nový task
                    // flags CLEAR_TASK už nepoužíváme, CATEGORY_HOME + NEW_TASK má podobný efekt na Task Stack

                    startActivity(intent) // Spustí SearchActivity
                    println("MainActivity: SearchActivity voláno startActivity().")

                    // Ukončíme MainActivity IHNED po spuštění nové Activity
                    // V předchozím testu jsme volali finish() bez zpoždění, zopakujme to chování
                    println("MainActivity: finish() ZAVOLÁNO.") // Log to confirm
                    finish()


                    // Zde už by NIKDY neměl být kód, který touchuje UI této Activity.
                }
                is LoginViewModel.LoginState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.buttonLogin.isEnabled = true // Opraven název

                    // Zobrazit chybovou zprávu uživateli pomocí string resource s placeholderem
                    binding.errorText.text = getString(R.string.login_error_message, state.message) // Opraven název a použití resource
                    binding.errorText.visibility = View.VISIBLE // Opraven název
                    binding.loggedInInfo.visibility = View.GONE // Opraven název

                    binding.editTextUsername.visibility = View.VISIBLE
                    binding.editTextPassword.visibility = View.VISIBLE
                    binding.checkBoxRememberMe.visibility = View.VISIBLE
                    binding.buttonLogin.visibility = View.VISIBLE // Opraven název
                    binding.textViewTitle.visibility = View.VISIBLE

                    binding.buttonLogin.visibility = View.VISIBLE // Opraven název
                    binding.logoutButton.visibility = View.GONE // Opraven název

                    Toast.makeText(this, getString(R.string.login_error_toast, state.message), Toast.LENGTH_LONG).show() // Použití resource a placeholderu
                }
            }
        }

        // --- Nastavení posluchače na tlačítko přihlášení ---
        binding.buttonLogin.setOnClickListener { // Opraven název
            val username = binding.editTextUsername.text.toString().trim()
            val password = binding.editTextPassword.text.toString()

            val rememberMe = binding.checkBoxRememberMe.isChecked

            viewModel.login(username, password, rememberMe)

            // Skrýt klávesnici po kliknutí na tlačítko
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        }

        // Listener pro tlačítko Odhlásit se - zde by normálně nemělo být viditelné
        binding.logoutButton.setOnClickListener { // Opraven název
            // Pokud by zde bylo viditelné a kliknutelné, zde byste volal(a) viewModel.logout()
        }
    }

    // onResume, onBackPressed atd. - pokud je potřeba, jinak nechte zakomentované nebo odstraňte
}