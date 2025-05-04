package com.example.wsplayer.ui.auth // Váš balíček + .ui.auth

import androidx.lifecycle.LiveData // Import pro LiveData
import androidx.lifecycle.MutableLiveData // Import pro MutableLiveData
import androidx.lifecycle.ViewModel // Import pro ViewModel
import androidx.lifecycle.viewModelScope // Import pro viewModelScope
import com.example.wsplayer.data.models.UserDataResponse // **Import pro UserDataResponse**
import com.example.wsplayer.data.repository.WebshareRepository // Import Repository
import kotlinx.coroutines.launch // Import pro spuštění Coroutine

// TODO: Pokud budete předávat argumenty ViewModelu přes SavedStateHandle, importujte toto:
// import androidx.lifecycle.SavedStateHandle


// ViewModel pro správu stavu přihlašování a uživatelských dat
// Přijímá WebshareRepository v konstruktoru (předává Factory)
class LoginViewModel(private val repository: WebshareRepository) : ViewModel() {
    // TODO: Pokud budete předávat argumenty ViewModelu přes SavedStateHandle, přidejte ho do konstruktoru:
    // class PlayerViewModel(private val repository: WebshareRepository, private val savedStateHandle: SavedStateHandle) : ViewModel() {

    // LiveData pro sledování stavu přihlášení z UI
    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState // Exponovaná LiveData pro UI

    // Sealed třída pro reprezentaci různých stavů přihlašování
    sealed class LoginState {
        object Idle : LoginState() // <- Dědí správně
        object Loading : LoginState() // <- Dědí správně
        data class Success(val token: String) : LoginState() // <- Dědí správně
        data class Error(val message: String) : LoginState() // <-- **PŘIDÁNO DĚDĚNÍ ZDE!**
        // TODO: Volitelně: LoadingAutoLogin stav
    }

    // LiveData pro uchování uživatelských dat
    private val _userData = MutableLiveData<UserDataResponse?>(null)
    val userData: LiveData<UserDataResponse?> = _userData // Exponovaná LiveData pro UI


    init {
        _loginState.value = LoginState.Idle // Nastaví počáteční stav na Idle (formulář)
        _userData.value = null // Inicializujte uživatelská data na null

        // Při inicializaci ViewModelu se zkusíme automaticky přihlásit, pokud jsou uložené údaje
        checkAutoLogin() // Spustí auto-login logiku
    }

    // Funkce pro pokus o automatické přihlášení
    private fun checkAutoLogin() {
        viewModelScope.launch {
            val credentials = repository.loadCredentials() // Načte uložené credentials

            if (credentials != null) {
                val (username, password) = credentials

                println("LoginViewModel: Nalezeny uložené údaje, zkouším auto-login pro $username...")

                _loginState.value = LoginState.Loading // Nastaví stav na načítání

                val result = repository.login(username, password) // Volání přihlašovací metody Repository

                _loginState.value = if (result.isSuccess) {
                    val token = result.getOrThrow()
                    println("LoginViewModel: Auto-login pro $username úspěšný.")

                    // Po úspěšném auto-loginu: Získat uživatelská data
                    loadUserData() // Zavolat metodu pro získání uživatelských dat

                    LoginState.Success(token) // Auto-login úspěšný
                } else {
                    val errorMessage = result.exceptionOrNull()?.message ?: "Neznámá chyba auto-loginu."
                    println("LoginViewModel: Auto-login pro $username selhal: $errorMessage")
                    // Při selhání auto-loginu se vraťte do Idle stavu (zobrazte přihlašovací formulář)
                    LoginState.Idle // Auto-login selhal
                }
            } else {
                println("LoginViewModel: Žádné uložené údaje nenalezeny, zobrazení formuláře.")
                _loginState.value = LoginState.Idle // Explicitně nastavíme Idle
            }
        }
    }


    // Funkce volaná z UI pro manuální přihlášení
    fun login(username: String, password: String, rememberMe: Boolean) {
        // Zabraňte spuštění nového přihlášení, pokud už jedno probíhá
        if (_loginState.value == LoginState.Loading) {
            return
        }

        // Základní validace
        if (username.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("Prosím, vyplňte uživatelské jméno i heslo.")
            return
        }

        _loginState.value = LoginState.Loading // Nastaví stav na načítání

        viewModelScope.launch {
            val result = repository.login(username, password) // Volání přihlašovací metody v Repository

            _loginState.value = if (result.isSuccess) {
                val token = result.getOrThrow()

                // Po úspěšném manuálním přihlášení: Uložit/smazat údaje dle checkboxu A Získat uživatelská data
                if (rememberMe) {
                    repository.saveCredentials(username, password) // Uloží credentials (apply)
                    println("LoginViewModel: Uživatel si přál zapamatovat, údaje uloženy.")
                } else {
                    repository.clearCredentials() // Smaže credentials (apply)
                    println("LoginViewModel: Uživatel si nepřál zapamatovat, údaje smazány (pokud existovaly).")
                }

                // Získat uživatelská data po úspěšném přihlášení
                loadUserData() // Zavolat metodu pro získání uživatelských dat

                LoginState.Success(token) // Manuální přihlášení úspěšné
            } else {
                val errorMessage = result.exceptionOrNull()?.message ?: "Neznámá chyba přihlášení."
                println("LoginViewModel: Manuální přihlášení selhalo: $errorMessage")
                LoginState.Error(errorMessage) // Manuální přihlášení selhalo
            }
        }
    }

    // Funkce pro získání uživatelských dat z Repository
    private fun loadUserData() {
        viewModelScope.launch {
            val result = repository.getUserData() // Volání metody Repository

            // Aktualizace LiveData s uživatelskými daty
            _userData.value = if (result.isSuccess) {
                println("LoginViewModel: Uživatelská data úspěšně načtena.") // Log
                result.getOrNull() // Při úspěchu vrátí UserDataResponse objekt nebo null
            } else {
                // Při selhání získání uživatelských dat (např. chyba sítě, neplatný token), nastavíme LiveData na null.
                // Chyba už byla zalogována v Repository nebo zde níže.
                println("LoginViewModel: Selhalo získání uživatelských dat: ${result.exceptionOrNull()?.message}") // Log chyby
                null // Nastavíme data na null
            }
        }
    }


    // Funkce pro odhlášení (smaže token a credentials, nastaví stav Idle a smaže user data)
    fun logout() {
        viewModelScope.launch {
            repository.logout() // Zavolá suspend funkci logout v Repository (smaže token a credentials)
            _loginState.value = LoginState.Idle // Explicitně nastavíme stav na Idle (UI se vrátí na formulář)
            _userData.value = null // Vymažeme uživatelská data při odhlášení
            println("LoginViewModel: Uživatel odhlášen, stav nastaven na Idle.") // Log
        }
    }

    // TODO: Volitelně: Funkce pro resetování stavu chyby v UI
}