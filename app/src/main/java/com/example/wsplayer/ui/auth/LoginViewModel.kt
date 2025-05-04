// app/src/main/java/com/example/wsplayer/ui/auth/LoginViewModel.kt
package com.example.wsplayer.ui.auth // Váš balíček - ZKONTROLUJTE

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope // Pro práci s coroutines ve ViewModelu

// **Import Repository**
import com.example.wsplayer.data.repository.WebshareRepository
// **Import pro utility pro hašování a parsování XML**
import com.example.wsplayer.utils.HashingUtils // Utility pro hašování
import com.example.wsplayer.utils.XmlUtils // Utility pro parsování XML

// **Import pro modelové třídy a stavy**
// ZKONTROLUJTE, že cesta odpovídá vašemu umístění DataModels.kt
import com.example.wsplayer.data.models.UserDataResponse // Používá se při načítání uživatelských dat
import com.example.wsplayer.data.models.LoginResponse // Používá se pro parsování login odpovědi (uvnitř Repository)
import com.example.wsplayer.data.models.SaltResponse // Používá se pro parsování salt odpovědi (uvnitř Repository)


import kotlinx.coroutines.Dispatchers // Pro background vlákna
import kotlinx.coroutines.launch // Pro spouštění coroutines
import kotlinx.coroutines.withContext // Pro přepnutí kontextu uvnitř coroutine

import android.util.Log // Logování


// ViewModel pro přihlašovací obrazovku (MainActivity)
// Spravuje stav UI a logiku přihlášení
// Přijímá instanci WebshareRepository v konstruktoru (dodá LoginViewModelFactory)
class LoginViewModel(private val repository: WebshareRepository) : ViewModel() {

    private val TAG = "LoginViewModel" // Logovací tag


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
        Log.d(TAG, "Init blok spuštěn. PID: ${android.os.Process.myPid()}")
        // Při startu aplikace se pokusíme o auto-login
        checkAutoLogin()
    }

    // --- Metoda pro auto-login ---
    // Pokusí se přihlásit pomocí uložených credentials
    fun checkAutoLogin() {
        Log.d(TAG, "checkAutoLogin() volán.")
        // Zkontrolovat, zda již nejsme ve stavu Loading nebo Success (abychom nespouštěli auto-login vícekrát)
        if (_loginState.value == LoginState.Loading || _loginState.value is LoginState.Success) {
            Log.d(TAG, "Již probíhá načítání nebo je přihlášeno, přeskakuji auto-login.")
            return
        }

        _loginState.value = LoginState.Loading // Nastavit stav na načítání (během auto-loginu)

        viewModelScope.launch {
            Log.d(TAG, "Spouštím auto-login coroutine.")
            val credentials = repository.loadCredentials() // Načíst uložené credentials

            if (credentials != null) {
                val (username, passwordHash) = credentials
                Log.d(TAG, "Nalezeny uložené credentials. Pokouším se přihlásit s těmito údaji.")
                // TODO: Místo celého login procesu zde, zkusit POUZE ověřit token, pokud existuje.
                // LoginRepository by měla mít metodu pro ověření tokenu.
                // Prozatím zkusíme celý login proces (který získá nový token, pokud je starý neplatný)
                val loginResult = repository.login(username, passwordHash) // Použít login metodu z Repository

                if (loginResult.isSuccess) {
                    val token = loginResult.getOrThrow()
                    // Při úspěchu auto-loginu credentials již uložené jsou, token byl uložen v Repository
                    Log.d(TAG, "Auto-login úspěšný s tokenem.")
                    loadUserData() // Načíst uživatelská data
                    _loginState.postValue(LoginState.Success(token)) // Nastavit stav na úspěch
                } else {
                    Log.e(TAG, "Auto-login selhal. ${loginResult.exceptionOrNull()?.message}. Mažu credentials a zobrazuji formulář.")
                    repository.clearCredentials() // Při selhání auto-loginu smazat neplatné credentials
                    _loginState.postValue(LoginState.Error("Automatické přihlášení selhalo: ${loginResult.exceptionOrNull()?.message}")) // Nastavit stav chyby
                    // _loginState.postValue(LoginState.Idle) // Možná rovnou přejít do Idle po chybě?
                }
            } else {
                Log.d(TAG, "Žádné uložené credentials nenalezeny, zobrazení formuláře.")
                // Žádné credentials pro auto-login, přejdeme rovnou do stavu Idle
                _loginState.postValue(LoginState.Idle) // Nastavit stav na Idle
            }
        }
    }

    // --- Metoda pro manuální přihlášení ---
    // Spustí přihlašovací proces na základě zadaných údajů
    // **Přijímá navíc rememberMe** pro rozhodnutí o uložení credentials
    fun login(username: String, password: String, rememberMe: Boolean) {
        Log.d(TAG, "login() volán s username '$username', rememberMe=$rememberMe.")
        // Zkontrolovat vstupní údaje (prázdné pole atd.)
        if (username.isEmpty() || password.isEmpty()) {
            _loginState.value = LoginState.Error("Uživatelské jméno a heslo nesmí být prázdné.")
            Log.d(TAG, "Prázdné údaje.")
            return
        }

        // Zkontrolovat, zda již nejsme ve stavu Loading (abychom nespouštěli login vícekrát)
        if (_loginState.value == LoginState.Loading) {
            Log.d(TAG, "Již probíhá načítání, přeskakuji login.")
            return
        }

        _loginState.value = LoginState.Loading // Nastavit stav na načítání

        viewModelScope.launch {
            Log.d(TAG, "Spouštím manuální login coroutine.")
            // --- Fáze 1: Získání soli z Repository ---
            val saltResult = repository.getSalt(username) // Volání getSalt z Repository

            // **OPRAVA: Explicitně ošetřit výsledek z getSalt**
            saltResult.fold(
                onSuccess = { salt ->
                    // *** TENTO BLOK SE SPUSTÍ POUZE PŘI ÚSPĚCHU getSalt ***
                    Log.d(TAG, "Získání soli úspěšné. Salt: $salt")

                    // --- Fáze 2: Hašování hesla (pomocí Utility třídy) ---
                    Log.d(TAG, "Provádím hašování hesla.")
                    val passwordHashResult = HashingUtils.calculateWebsharePasswordHash(password, salt) // Použít získanou sůl

                    if (passwordHashResult.isFailure) {
                        Log.e(TAG, "Hašování selhalo: ${passwordHashResult.exceptionOrNull()?.message}")
                        _loginState.postValue(LoginState.Error("Chyba při hašování hesla: ${passwordHashResult.exceptionOrNull()?.message}"))
                        return@fold // Ukončit blok fold/onSuccess
                    }

                    val passwordHash = passwordHashResult.getOrThrow()

                    // --- Fáze 3: Přihlášení v Repository s hashem hesla ---
                    Log.d(TAG, "Volám Repository.login s hashem hesla.")
                    val loginResult = repository.login(username, passwordHash)

                    if (loginResult.isSuccess) {
                        val token = loginResult.getOrThrow()
                        Log.d(TAG, "Repository.login úspěšný.")

                        // **Fáze 4: Správa lokálních dat na základě rememberMe**
                        if (rememberMe) {
                            Log.d(TAG, "Checkbox 'Zapamatovat si mě' byl true. Ukládám credentials...")
                            repository.saveCredentials(username, passwordHash)
                        } else {
                            Log.d(TAG, "Checkbox 'Zapamatovat si mě' byl false. Mažu credentials...")
                            repository.clearCredentials()
                        }

                        // **Fáze 5: Načtení uživatelských dat po úspěšném přihlášení**
                        loadUserData() // Načíst uživatelská data (asynchronně)
                        Log.d(TAG, "Načtení uživatelských dat spuštěno.")

                        // **Fáze 6: Nastavení úspěšného stavu**
                        _loginState.postValue(LoginState.Success(token))
                        Log.d(TAG, "Nastavuji stav LoginState.Success($token).")

                    } else {
                        Log.e(TAG, "Repository.login selhal: ${loginResult.exceptionOrNull()?.message}")
                        _loginState.postValue(LoginState.Error("Přihlášení selhalo: ${loginResult.exceptionOrNull()?.message}"))
                    }
                },
                onFailure = { error ->
                    // *** TENTO BLOK SE SPUSTÍ POUZE PŘI SELHÁNÍ getSalt ***
                    Log.e(TAG, "Získání soli selhalo ve ViewModelu: ${error.message}")
                    _loginState.postValue(LoginState.Error("Chyba při získání soli: ${error.message}"))
                }
            ) // Konec fold

            // **ZDE KÓD NEPOKRAČUJE, pokud se spustil onSuccess nebo onFailure**
        }
    }

    // --- Metoda pro načtení uživatelských dat po přihlášení ---
    // Načte uživatelská data pomocí tokenu z Repository a případně aktualizuje LiveData
    fun loadUserData() { // Volá se po úspěšném auto-loginu nebo manuálním loginu
        Log.d(TAG, "loadUserData() volán.")
        // Kontrola přihlášení zde není nutná, volá se to po úspěšném přihlášení, kde víme, že token máme.
        viewModelScope.launch {
            val userDataResult = repository.getUserData() // Volání getUserData z Repository

            if (userDataResult.isSuccess) {
                val userData = userDataResult.getOrThrow()
                // Úspěšně načtená uživatelská data
                Log.d(TAG, "Uživatelská data úspěšně načtena.")
                // TODO: Pokud LoginActivity zobrazuje uživatelská data, aktualizovat _userData LiveData zde
                // _userData.postValue(userData)
            } else {
                // Selhání načtení uživatelských dat (např. neplatný token)
                Log.e(TAG, "Načtení uživatelských dat selhalo: ${userDataResult.exceptionOrNull()?.message}")
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