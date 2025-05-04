// app/src/main/java/com/example/wsplayer/ui/auth/LoginViewModel.kt
package com.example.wsplayer.ui.auth // Váš balíček - ZKONTROLUJTE

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope // Pro práci s coroutines ve ViewModelu

// **Import Repository**
import com.example.wsplayer.data.repository.WebshareRepository

// **Import pro utility pro hašování a parsování XML**
import com.example.wsplayer.utils.HashingUtils
import com.example.wsplayer.utils.XmlUtils // Pokud se parsuje přímo zde

// **Import pro modelové třídy a stavy**
// ZKONTROLUJTE, že cesta odpovídá vašemu umístění DataModels.kt
import com.example.wsplayer.data.models.UserDataResponse // Používá se při načítání uživatelských dat
import com.example.wsplayer.data.models.LoginResponse // Používá se pro parsování login odpovědi (uvnitř Repository)
import com.example.wsplayer.data.models.SaltResponse // Používá se pro parsování salt odpovědi (uvnitř Repository)


import kotlinx.coroutines.Dispatchers // Pro background vlákna
import kotlinx.coroutines.launch // Pro spouštění coroutines
import kotlinx.coroutines.withContext // Pro přepnutí kontextu uvnitř coroutine


// ViewModel pro přihlašovací obrazovku (MainActivity)
// Spravuje stav UI a logiku přihlášení
// Přijímá instanci WebshareRepository v konstruktoru (dodá LoginViewModelFactory)
class LoginViewModel(private val repository: WebshareRepository) : ViewModel() {

    // --- Stavy pro UI (LiveData) ---

    // Sealed class reprezentující různé stavy procesu přihlašování
    // Definovaná přímo zde, protože se primárně týká stavu LoginActivity
    // Pokud ji používáte i jinde (např. v SettingsActivity pro logout signalizaci),
    // měla by být v data.models a zde naimportována.
    sealed class LoginState {
        object Idle : LoginState() // Počáteční stav / zobrazení formuláře
        object Loading : LoginState() // Probíhá přihlašování / auto-login
        data class Success(val token: String) : LoginState() // Přihlášení úspěšné (nese token)
        data class Error(val message: String) : LoginState() // Došlo k chybě (nese zprávu)
        // TODO: Případně přidat stav pro auto-login selhání s možností zkusit manuálně
    }

    // Stav aktuálního přihlašování
    private val _loginState = MutableLiveData<LoginState>(LoginState.Idle)
    val loginState: LiveData<LoginState> = _loginState // Activity sleduje tento LiveData

    // **LiveData pro uživatelská data** (volitelné, pokud je LoginActivity zobrazuje)
    // private val _userData = MutableLiveData<UserDataResponse?>()
    // val userData: LiveData<UserDataResponse?> = _userData


    // Inicializační blok - spustí se PŘI PRVNÍM vytvoření ViewModelu
    // Toto se stane při startu MainActivity, pokud není zničena systémem
    init {
        println("LoginViewModel: Init blok spuštěn. PID: ${android.os.Process.myPid()}") // Log
        // Při startu aplikace se pokusíme o auto-login
        checkAutoLogin()
    }

    // --- Metoda pro auto-login ---
    // Pokusí se přihlásit pomocí uložených credentials
    fun checkAutoLogin() {
        println("LoginViewModel: checkAutoLogin() volán.") // Log
        // Zkontrolovat, zda již nejsme ve stavu Loading nebo Success (abychom nespouštěli auto-login vícekrát)
        if (_loginState.value == LoginState.Loading || _loginState.value is LoginState.Success) {
            println("LoginViewModel: Již probíhá načítání nebo je přihlášeno, přeskakuji auto-login.") // Log
            return
        }

        _loginState.value = LoginState.Loading // Nastavit stav na načítání (během auto-loginu)

        viewModelScope.launch {
            println("LoginViewModel: Spouštím auto-login coroutine.") // Log
            val credentials = repository.loadCredentials() // Načíst uložené credentials (Pair<username, passwordHash>?)

            if (credentials != null) {
                val (username, passwordHash) = credentials
                println("LoginViewModel: Nalezeny uložené credentials. Pokouším se přihlásit s těmito údaji.") // Log

                // Pokusíme se přihlásit v Repository (která použije hash a získá/uloží token)
                val loginResult = repository.login(username, passwordHash) // Použít login metodu z Repository

                if (loginResult.isSuccess) {
                    val token = loginResult.getOrThrow()
                    // Při úspěchu auto-loginu credentials již uložené jsou, token byl uložen v Repository
                    println("LoginViewModel: Auto-login úspěšný s tokenem.") // Log
                    loadUserData() // Načíst uživatelská data po úspěšném auto-loginu
                    _loginState.postValue(LoginState.Success(token)) // Nastavit stav na úspěch
                } else {
                    println("LoginViewModel: Auto-login selhal. ${loginResult.exceptionOrNull()?.message}. Mažu credentials a zobrazuji formulář.") // Log
                    repository.clearCredentials() // Při selhání auto-loginu smazat neplatné credentials
                    _loginState.postValue(LoginState.Error("Automatické přihlášení selhalo: ${loginResult.exceptionOrNull()?.message}")) // Nastavit stav chyby s informací o selhání
                    // _loginState.postValue(LoginState.Idle) // Možná rovnou přejít do Idle po chybě?
                }
            } else {
                println("LoginViewModel: Žádné uložené credentials nenalezeny, zobrazení formuláře.") // Log
                // Žádné credentials pro auto-login, přejdeme rovnou do stavu Idle
                _loginState.postValue(LoginState.Idle) // Nastavit stav na Idle
            }
        }
    }

    // --- Metoda pro manuální přihlášení ---
    // Spustí přihlašovací proces na základě zadaných údajů
    // **Přijímá navíc rememberMe** pro rozhodnutí o uložení credentials
    fun login(username: String, password: String, rememberMe: Boolean) {
        println("LoginViewModel: login() volán s username '$username', rememberMe=$rememberMe.") // Log
        // Zkontrolovat vstupní údaje (prázdné pole atd.)
        if (username.isEmpty() || password.isEmpty()) {
            _loginState.value = LoginState.Error("Uživatelské jméno a heslo nesmí být prázdné.") // Nastavit stav chyby
            println("LoginViewModel: Prázdné údaje.") // Log
            return
        }

        // Zkontrolovat, zda již nejsme ve stavu Loading (abychom nespouštěli login vícekrát)
        if (_loginState.value == LoginState.Loading) {
            println("LoginViewModel: Již probíhá načítání, přeskakuji login.") // Log
            return
        }


        _loginState.value = LoginState.Loading // Nastavit stav na načítání

        viewModelScope.launch {
            println("LoginViewModel: Spouštím manuální login coroutine.") // Log
            // --- Fáze 1: Získání soli z Repository ---
            val saltResult = repository.getSalt(username) // Volání getSalt z Repository

            if (saltResult.isFailure) {
                println("LoginViewModel: Získání soli selhalo: ${saltResult.exceptionOrNull()?.message}") // Log
                _loginState.postValue(LoginState.Error("Chyba při získání soli: ${saltResult.exceptionOrNull()?.message}"))
                return@launch // Ukončit coroutine
            }

            val salt = saltResult.getOrThrow()

            // --- Fáze 2: Hašování hesla (pomocí Utility třídy, volá se zde, ne v Repository) ---
            // Hašování probíhá na background vlákně díky viewModelScope.launch, i když Utility nemusí být suspend
            println("LoginViewModel: Provádím hašování hesla.") // Log
            val passwordHashResult = HashingUtils.calculateWebsharePasswordHash(password, salt) // Volání Utility

            if (passwordHashResult.isFailure) {
                println("LoginViewModel: Hašování selhalo: ${passwordHashResult.exceptionOrNull()?.message}") // Log
                _loginState.postValue(LoginState.Error("Chyba při hašování hesla: ${passwordHashResult.exceptionOrNull()?.message}"))
                return@launch // Ukončit coroutine
            }

            val passwordHash = passwordHashResult.getOrThrow()

            // --- Fáze 3: Přihlášení v Repository s hashem hesla ---
            println("LoginViewModel: Volám Repository.login s hashem hesla.") // Log
            // repository.login přijímá username a HASH hesla
            val loginResult = repository.login(username, passwordHash) // Volání login z Repository

            if (loginResult.isSuccess) {
                val token = loginResult.getOrThrow()
                println("LoginViewModel: Repository.login úspěšný.") // Log

                // **Fáze 4: Správa lokálních dat na základě rememberMe**
                // Repository již uložila token. Zde uložíme/smažeme credentials podle checkboxu.
                if (rememberMe) {
                    // Uživatel si přál být zapamatován - uložit credentials (username + hash)
                    println("LoginViewModel: Checkbox 'Zapamatovat si mě' byl true. Ukládám credentials...") // Log
                    repository.saveCredentials(username, passwordHash) // Uložit credentials (s hashem)
                } else {
                    // Uživatel si NEPŘÁL být zapamatován - smazat uložené credentials
                    println("LoginViewModel: Checkbox 'Zapamatovat si mě' byl false. Mažu credentials...") // Log
                    repository.clearCredentials() // Smazat credentials
                }

                // **Fáze 5: Načtení uživatelských dat po úspěšném přihlášení**
                loadUserData() // Načíst uživatelská data (asynchronně)
                println("LoginViewModel: Načtení uživatelských dat spuštěno.") // Log

                // **Fáze 6: Nastavení úspěšného stavu**
                _loginState.postValue(LoginState.Success(token)) // **Nastavit stav na úspěch**
                println("LoginViewModel: Nastavuji stav LoginState.Success($token).") // Log

            } else {
                println("LoginViewModel: Repository.login selhal: ${loginResult.exceptionOrNull()?.message}") // Log
                // Přihlášení v Repository selhalo (síť, API chyba atd.)
                _loginState.postValue(LoginState.Error("Přihlášení selhalo: ${loginResult.exceptionOrNull()?.message}")) // Nastavit stav chyby
            }
        }
    }

    // --- Metoda pro načtení uživatelských dat po přihlášení ---
    // Načte uživatelská data pomocí tokenu z Repository a případně aktualizuje LiveData
    fun loadUserData() { // Volá se po úspěšném auto-loginu nebo manuálním loginu
        println("LoginViewModel: loadUserData() volán.") // Log
        // Kontrola přihlášení zde není nutná, volá se to po úspěšném přihlášení, kde víme, že token máme.
        viewModelScope.launch {
            val userDataResult = repository.getUserData() // Volání getUserData z Repository

            if (userDataResult.isSuccess) {
                val userData = userDataResult.getOrThrow()
                // Úspěšně načtená uživatelská data
                println("LoginViewModel: Uživatelská data úspěšně načtena.") // Log
                // TODO: Pokud LoginActivity zobrazuje uživatelská data, aktualizovat _userData LiveData zde
                // _userData.postValue(userData)
            } else {
                // Selhání načtení uživatelských dat (např. neplatný token)
                println("LoginViewModel: Načtení uživatelských dat selhalo: ${userDataResult.exceptionOrNull()?.message}") // Log
                // Pokud selže načtení uživatelských dat PO úspěšném přihlášení (např. token neplatný hned),
                // může být potřeba přesměrovat zpět na login. ViewModel by měl signalizovat chybu,
                // a Activity by měla rozhodnout o přesměrování.
                // _loginState.postValue(LoginState.Error("Přihlášení úspěšné, ale nelze načíst data: ${userDataResult.exceptionOrNull()?.message}"))
            }
        }
    }

    // TODO: Metoda pro odhlášení (pokud je v LoginActivity, ale obvykle bývá v Search/Settings Activity)
    // Pokud by byla zde, volala by repository.logout(), repository.clearAuthToken(), repository.clearCredentials()
    // a nastavila _loginState na Idle.
}