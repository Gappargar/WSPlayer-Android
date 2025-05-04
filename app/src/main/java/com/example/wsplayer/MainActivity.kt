// app/src/main/java/com/example/wsplayer/MainActivity.kt
package com.example.wsplayer // Váš balíček pro MainActivity - ZKONTROLUJTE

import android.content.Context // Import pro Context
import android.content.Intent // Import pro Intent
import android.os.Bundle
import android.os.Handler // Import pro Handler (pro zpoždění)
import android.os.Looper // Import pro Looper (pro zpoždění)
import android.view.View // Import pro View (GONE, VISIBLE)
import android.view.inputmethod.InputMethodManager // Import pro skrývání klávesnice
import android.widget.Toast // Import pro Toast zprávy
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer // Nechat pokud používáte Observer { } syntaxi, jinak smazat
import androidx.lifecycle.ViewModelProvider

// Importy pro ViewModel a Factory
import com.example.wsplayer.ui.auth.LoginViewModel // Import LoginViewModel
import com.example.wsplayer.ui.auth.LoginViewModelFactory // Import LoginViewModelFactory

// Importy pro Repository, AuthTokenManager, ApiService - potřeba pro Factory
import com.example.wsplayer.data.repository.WebshareRepository
import com.example.wsplayer.AuthTokenManager // Import AuthTokenManager
import com.example.wsplayer.data.api.WebshareApiService // Import ApiService

// Import pro datové třídy a stavy, pokud je MainActivity přímo používá (obvykle ne, ale pro jistotu)
// import com.example.wsplayer.data.models.* // Pokud zde přímo používáte např. UserDataResponse

// Import pro SearchActivity (pro přesměrování)
import com.example.wsplayer.ui.search.SearchActivity

// Import pro R třídu (pro string resources)
import com.example.wsplayer.R


// Hlavní aktivita aplikace - slouží jako přihlašovací obrazovka
class MainActivity : AppCompatActivity() {

    // Binding pro UI prvky v activity_main.xml
    private lateinit var binding: ActivityMainBinding // Import vygenerovaného bindingu

    // ViewModel pro správu stavu přihlášení
    private lateinit var viewModel: LoginViewModel // Toto je LoginViewModel

    // --- Metoda volaná při vytvoření Activity ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        println("MainActivity: >>> onCreate spuštěn. PID: ${android.os.Process.myPid()}") // Log

        // --- Nastavení uživatelského rozhraní pomocí View Bindingu ---
        // Předpokládá, že máte activity_main.xml a v něm povolený View Binding
        // import com.example.wsplayer.databinding.ActivityMainBinding se provede automaticky
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        println("MainActivity: ContentView nastaven.")


        // --- Inicializace ViewModelu pomocí Factory ---
        // Vytvoříme instanci ApiService a Repository (zde nebo v Factory)
        // Factory si sama vytvoří Repository (a uvnitř i AuthTokenManager).
        val apiService = WebshareApiService.create() // Vytvoří instanci ApiService
        val viewModelFactory = LoginViewModelFactory(applicationContext, apiService) // Factory pro LoginViewModel
        viewModel = ViewModelProvider(this, viewModelFactory).get(LoginViewModel::class.java) // Získání ViewModelu


        // --- Sledování stavu přihlašování z ViewModelu (zjednodušená syntaxe observeru) ---
        println("MainActivity: Nastavuji Observer pro viewModel.loginState.") // Log
        // Použijte zjednodušenou syntaxi lambda observeru
        viewModel.loginState.observe(this) { state -> // 'state' je aktuální LoginState
            println("MainActivity: Observer loginState spuštěn. Aktuální stav: $state") // Log aktuálního stavu
            when (state) { // Zpracování různých stavů přihlašování
                is LoginViewModel.LoginState.Idle -> {
                    println("MainActivity: LoginState je Idle.") // Log
                    binding.progressBar.visibility = View.GONE
                    binding.buttonLogin.isEnabled = true
                    binding.errorText.visibility = View.GONE // Opraven název
                    binding.loggedInInfo.visibility = View.GONE // Opraven název

                    // Zobrazit přihlašovací formulář
                    binding.editTextUsername.visibility = View.VISIBLE
                    binding.editTextPassword.visibility = View.VISIBLE
                    binding.checkBoxRememberMe.visibility = View.VISIBLE
                    binding.buttonLogin.visibility = View.VISIBLE // Opraven název
                    binding.textViewTitle.visibility = View.VISIBLE // Opraven název

                    // Zobrazit/skrýt tlačítka (Login viditelný, Logout skrytý)
                    binding.buttonLogin.visibility = View.VISIBLE // Opraven název
                    binding.logoutButton.visibility = View.GONE // Opraven název
                }
                is LoginViewModel.LoginState.Loading -> {
                    println("MainActivity: LoginState je Loading.") // Log
                    binding.progressBar.visibility = View.VISIBLE
                    binding.buttonLogin.isEnabled = false // Opraven název
                    binding.errorText.visibility = View.GONE // Opraven název
                    binding.loggedInInfo.visibility = View.GONE // Opraven název

                    // Skrýt přihlašovací prvky během načítání
                    binding.editTextUsername.visibility = View.GONE
                    binding.editTextPassword.visibility = View.GONE
                    binding.checkBoxRememberMe.visibility = View.GONE
                    binding.buttonLogin.visibility = View.GONE // Opraven název
                    binding.textViewTitle.visibility = View.GONE // Opraven název
                }
                is LoginViewModel.LoginState.Success -> {
                    println("MainActivity: >>> Observer LoginState.Success spuštěn. Token získán.") // Log

                    binding.progressBar.visibility = View.GONE
                    binding.buttonLogin.isEnabled = true // Opraven název

                    // Skrýt přihlašovací prvky (formulář)
                    binding.editTextUsername.visibility = View.GONE
                    binding.editTextPassword.visibility = View.GONE
                    binding.checkBoxRememberMe.visibility = View.GONE
                    binding.buttonLogin.visibility = View.GONE // Opraven název
                    binding.textViewTitle.visibility = View.GONE // Opraven název
                    binding.errorText.visibility = View.GONE // Opraven název
                    binding.loggedInInfo.visibility = View.GONE // Opraven název

                    // Zobrazit zprávu o úspěchu (používá string resource)
                    Toast.makeText(this, getString(R.string.login_success_toast), Toast.LENGTH_SHORT).show()

                    // --- Přejít na SearchActivity a vymazat Task ---
                    println("MainActivity: Spouštím SearchActivity s flags NEW_TASK | CLEAR_TASK.") // Log
                    val intent = Intent(this, SearchActivity::class.java)
                    // Tyto flags zajistí, že SearchActivity se stane novým kořenem Tasku
                    // a původní Task (s LoginActivity) bude vymazán.
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                    startActivity(intent) // Spustí SearchActivity
                    println("MainActivity: SearchActivity voláno startActivity().") // Log

                    // **Workaround pro nativní pád na MIUI při rychlém přechodu**
                    // Ukončíme MainActivity s krátkým zpožděním, aby systém měl více času na animaci a přípravu nové Activity
                    println("MainActivity: finish() ZAVOLÁNO po krátkém zpoždění (200ms).") // Log PŘED Handlerem
                    Handler(Looper.getMainLooper()).postDelayed({
                        println("MainActivity: finish() provádím z Handleru.") // Log UVNITŘ Handleru
                        finish() // Ukončí MainActivity po zpoždění
                        println("MainActivity: finish() volání dokončeno z Handleru.") // Log PO finish() v Handleru
                    }, 200) // Zpoždění v milisekundách (zkuste 200, 300, 500ms pokud 200 nepomůže)


                    // Zde už by NIKDY neměl být kód, který touchuje UI této Activity.
                }
                is LoginViewModel.LoginState.Error -> {
                    println("MainActivity: LoginState je Error.") // Log
                    binding.progressBar.visibility = View.GONE
                    binding.buttonLogin.isEnabled = true // Opraven název

                    // Zobrazit chybovou zprávu uživateli (používá string resource s placeholderem)
                    binding.errorText.text = getString(R.string.login_error_message, state.message) // Opraven název a použití resource
                    binding.errorText.visibility = View.VISIBLE // Opraven název
                    binding.loggedInInfo.visibility = View.GONE // Opraven název

                    // Zobrazit přihlašovací formulář zpět
                    binding.editTextUsername.visibility = View.VISIBLE
                    binding.editTextPassword.visibility = View.VISIBLE
                    binding.checkBoxRememberMe.visibility = View.VISIBLE
                    binding.buttonLogin.visibility = View.VISIBLE // Opraven název
                    binding.textViewTitle.visibility = View.VISIBLE // Opraven název

                    // Zobrazit/skrýt tlačítka
                    binding.buttonLogin.visibility = View.VISIBLE // Opraven název
                    binding.logoutButton.visibility = View.GONE // Opraven název

                    Toast.makeText(this, getString(R.string.login_error_toast, state.message), Toast.LENGTH_LONG).show() // Použití resource a placeholderu
                }
            }
        }

        // --- Nastavení posluchače na tlačítko přihlášení ---
        println("MainActivity: Nastavuji OnClickListener pro tlačítko Přihlásit.") // Log
        binding.buttonLogin.setOnClickListener { // Opraven název bindingu
            println("MainActivity: Kliknuto na tlačítko Přihlásit.") // Log
            val username = binding.editTextUsername.text.toString().trim()
            val password = binding.editTextPassword.text.toString() // Heslo nebudeme trimovat
            val rememberMe = binding.checkBoxRememberMe.isChecked

            // Volání přihlašovací metody ve ViewModelu s údaji A se stavem rememberMe
            viewModel.login(username, password, rememberMe)

            // Volitelně: skrýt klávesnici po kliknutí na tlačítko
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
            println("MainActivity: Klávesnice skryta.") // Log
        }

        // Listener pro tlačítko Odhlásit se (obvykle není v MainActivity, ale pokud ho máte v layoutu a chcete ho zde ovládat)
        binding.logoutButton.setOnClickListener { // Opraven název bindingu
            println("MainActivity: Kliknuto na tlačítko Odhlásit (v MainActivity, nemělo by zde být).") // Log
            // Pokud by zde bylo viditelné a kliknutelné, zde byste volal(a) viewModel.logout()
            // ViewModel by pak nastavil LoginState.Idle, což by observer zde zachytil a zobrazil formulář.
        }

        println("MainActivity: <<< onCreate dokončen.") // Log
    }

    // Metoda volaná, když Activity končí (např. po finish())
    override fun onDestroy() {
        println("MainActivity: >>> onDestroy spuštěn.") // Log
        // Zde můžete uvolnit zdroje, které byly alokovány specificky pro tuto Activity
        // např. zrušit registraci BroadcastReceiverů, pokud nějaké máte
        super.onDestroy()
        println("MainActivity: <<< onDestroy dokončen.") // Log
    }

    // Volitelně: Ošetření stisknutí tlačítka Zpět na přihlašovací obrazovce
    // Ve výchozím stavu (pokud není přepsáno) jen ukončí Activity
    // override fun onBackPressed() {
    //     println("MainActivity: Tlačítko Zpět stisknuto.") // Log
    //     super.onBackPressed() // Výchozí chování - ukončí Activity
    // }
}