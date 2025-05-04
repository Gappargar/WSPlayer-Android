// app/src/main/java/com/example/wsplayer/data/repository/WebshareRepository.kt
package com.example.wsplayer.data.repository // Váš balíček - ZKONTROLUJTE

import android.os.Build // Potřeba pro Build info (pokud se používá v getFileLink)

import com.example.wsplayer.data.api.WebshareApiService // Import Retrofit API rozhraní
// **Importujte vaše modelové třídy z vašeho data.models balíčku**
import com.example.wsplayer.data.models.* // Import datových tříd (* import pro všechny třídy v models)

import com.example.wsplayer.utils.HashingUtils // Import utility pro hašování (používá se ve login/getSalt metodách ViewModelu)
import com.example.wsplayer.utils.XmlUtils // Import utility pro parsování XML

import kotlinx.coroutines.Dispatchers // Pro background vlákna
import kotlinx.coroutines.withContext // Pro přepnutí kontextu

import kotlin.Result // Import třídy Result
import com.example.wsplayer.AuthTokenManager // Import AuthTokenManager


// Repository pro komunikaci s Webshare.cz API
// Slouží jako jediný zdroj dat pro ViewModely.
// Zapouzdřuje logiku volání API, zpracování dat a správu lokálního stavu přes AuthTokenManager.
class WebshareRepository(
    private val apiService: WebshareApiService, // Přijímá instanci API služby
    private val authTokenManager: AuthTokenManager // Přijímá instanci AuthTokenManageru
) {

    // --- Správa autentizačního tokenu a credentials (Deleguje na AuthTokenManager) ---

    // Uloží autentizační token (volá se zevnitř login metody)
    private fun saveAuthToken(token: String) {
        authTokenManager.saveToken(token)
        println("Repository: Token uložen pomocí AuthTokenManageru.") // Log
    }

    // Načte autentizační token (volá se z ViewModelů)
    fun getAuthToken(): String? {
        println("Repository: Získávám token z AuthTokenManageru...") // Log
        return authTokenManager.getAuthToken()
    }

    // **Smaže autentizační token** (PUBLIC metoda pro ViewModels jako součást logoutu nebo při chybě tokenu)
    fun clearAuthToken() {
        println("WebshareRepository: Volám clearAuthToken() na AuthTokenManageru.") // Log
        authTokenManager.clearToken() // Deleguje na AuthTokenManager.clearToken()
    }

    // Načte uložené credentials (pro auto-login)
    fun loadCredentials(): Pair<String, String>? {
        println("Repository: Načítám uložené credentials přes AuthTokenManager...") // Log
        return authTokenManager.loadCredentials() // Vrací Pair<username, passwordHash> nebo null
    }

    // Uloží uživatelské jméno a hašované heslo
    fun saveCredentials(username: String, passwordHash: String) { // Očekává již HASH hesla
        println("Repository: Ukládám credentials přes AuthTokenManager.") // Log
        authTokenManager.saveCredentials(username, passwordHash) // Deleguje
    }

    // Smaže uložené credentials
    fun clearCredentials() {
        println("Repository: Mažu uložené credentials přes AuthTokenManager.") // Log
        authTokenManager.clearCredentials() // Deleguje
    }

    // --- Implementace přihlašovací logiky (pro LoginViewModel) ---
    // Vrací Result<String>, kde String je WST token při úspěchu, nebo chyba
    // **Nepřijímá rememberMe - tato logika je ve ViewModelu**
    suspend fun login(username: String, passwordHash: String): Result<String> { // Přijímá již hašované heslo
        return withContext(Dispatchers.IO) { // Spustit v background vlákně
            try {
                // --- Fáze 1: Odeslání přihlašovacího požadavku na API ---
                println("Repository: Odesílám přihlašovací požadavek na API pro uživatele '$username' s hashem.") // Log
                // Keep_logged_in=1 zde v Repository, protože API endpoint to vyžaduje pro vrácení tokenu
                val loginResponseRetrofit = apiService.login(username, passwordHash, 1) // keep_logged_in = 1

                if (!loginResponseRetrofit.isSuccessful) {
                    val errorBody = loginResponseRetrofit.errorBody()?.string() ?: "Není k dispozici"
                    println("Repository: Chyba sítě při přihlášení: ${loginResponseRetrofit.code()}, Tělo chyby: $errorBody") // Log
                    return@withContext Result.failure(Exception("Chyba sítě při přihlášení: ${loginResponseRetrofit.code()}"))
                }

                val loginBody = loginResponseRetrofit.body() // Očekává se LoginResponse
                if (loginBody == null) { // Zkontrolovat null odpověď od Retrofitu
                    println("Repository: Prázdná odpověď serveru při přihlášení.") // Log
                    return@withContext Result.failure(Exception("Prázdná odpověď serveru při přihlášení."))
                }

                println("Repository: Parsuji XML odpověď přihlášení...") // Log
                // Předpokládáme, že Retrofit již provedl parsování do LoginResponse díky nastavení v ApiService
                val loginData = loginBody // LoginData je typu LoginResponse

                when (loginData.status) {
                    "OK" -> {
                        val token = loginData.token
                        if (token.isNullOrEmpty()) {
                            println("Repository: Přihlášení OK, ale token nebyl v odpovědi nalezen.") // Log
                            return@withContext Result.failure(Exception("Přihlášení OK, ale token nebyl v odpovědi nalezen."))
                        }

                        // Token se uloží UVNITŘ Repository při úspěšném přihlášení
                        saveAuthToken(token) // **Uloží token pomocí private metody**

                        println("Repository: Přihlášení úspěšné! Token získán.") // Log
                        Result.success(token) // Vrátí token ViewModelu
                    }
                    "ERROR", "FATAL" -> {
                        println("Repository: Webshare API chyba při přihlášení: ${loginData.message} (${loginData.code})") // Log
                        Result.failure(Exception("Webshare API chyba při přihlášení: ${loginData.message} (${loginData.code})"))
                    }
                    else -> {
                        println("Repository: Neznámý status odpovědi přihlášení: ${loginData.status}") // Log
                        Result.failure(Exception("Neznámý status odpovědi přihlášení: ${loginData.status}"))
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                println("Repository: Neočekávaná chyba v přihlašovací logice: ${e.message}") // Log
                Result.failure(e)
            }
        }
    }

    // --- Implementace logiky pro získání soli pro heslo (pro LoginViewModel) ---
    suspend fun getSalt(username: String): Result<String> { // Vrací Result<String> (sůl)
        return withContext(Dispatchers.IO) { // Spustit v background vlákně
            try {
                println("Repository: Volám API pro získání soli pro uživatele '$username'.") // Log
                val saltResponseRetrofit = apiService.getSalt(username) // Očekává SaltResponse

                if (!saltResponseRetrofit.isSuccessful) {
                    val errorBody = saltResponseRetrofit.errorBody()?.string() ?: "Není k dispozici"
                    println("Repository: Chyba sítě při získávání soli: ${saltResponseRetrofit.code()}, Tělo chyby: $errorBody") // Log
                    return@withContext Result.failure(Exception("Chyba sítě při získávání soli: ${saltResponseRetrofit.code()}"))
                }

                val saltBody = saltResponseRetrofit.body() // Typ SaltResponse
                if (saltBody == null) { // Zkontrolovat null odpověď od Retrofitu
                    println("Repository: Prázdná odpověď serveru při získávání soli.") // Log
                    return@withContext Result.failure(Exception("Prázdná odpověď serveru při získávání soli."))
                }

                println("Repository: Parsuji XML odpověď soli...") // Log
                // Předpokládáme, že Retrofit již provedl parsování do SaltResponse
                val saltData = saltBody // SaltData je typu SaltResponse

                if (saltData.status != "OK") {
                    println("Repository: Webshare API chyba při získávání soli: ${saltData.message} (${saltData.code})") // Log
                    return@withContext Result.failure(Exception("Webshare API chyba při získávání soli: ${saltData.message} (${saltData.code})"))
                }

                val salt = saltData.salt
                if (salt.isNullOrEmpty()) {
                    println("Repository: Sůl nebyla v odpovědi API nalezena, ačkoli status byl OK.") // Log
                    return@withContext Result.failure(Exception("Sůl nebyla v odpovědi API nalezena, ačkoli status byl OK."))
                }

                println("Repository: Sůl úspěšně získána: '$salt'.") // Log
                Result.success(salt) // Vrátí sůl (String)
            } catch (e: Exception) {
                e.printStackTrace()
                println("Repository: Neočekávaná chyba při získávání soli: ${e.message}") // Log
                Result.failure(e)
            }
        }
    }

    // --- Implementace logiky odhlášení (volá LoginViewModel nebo SearchViewModel/SettingsViewModel) ---
    // Vrací Result<Unit> (jednotku) při úspěchu nebo chyba
    suspend fun logout(): Result<Unit> {
        return withContext(Dispatchers.IO) { // Spustit v background vlákně
            val token = getAuthToken() // Získáme aktuální token pro volání API

            if (!token.isNullOrEmpty()) {
                try {
                    println("Repository: Volám API pro odhlášení s tokenem...") // Log
                    val logoutResponseRetrofit = apiService.logout(token) // Volání API logout

                    if (logoutResponseRetrofit.isSuccessful) {
                        val logoutBody = logoutResponseRetrofit.body() // Očekává se LogoutResponse nebo podobný
                        if (!logoutBody.isNullOrEmpty()) {
                            // Předpokládáme, že odpověď je jen status OK/ERROR/FATAL (dle dokumentace)
                            val status = XmlUtils.parseLogoutResponseXml(logoutBody) // Potřebuje parsovací metodu pro logout
                            if (status == "OK") {
                                println("Repository: API odhlášení úspěšné. Status OK.") // Log
                                // Úspěch API odhlášení
                            } else {
                                println("Repository: API odhlášení vrátilo status: $status. Lokální smazání proběhne.") // Log
                            }
                        } else {
                            println("Repository: API odhlášení vrátilo prázdné tělo odpovědi.") // Log
                        }
                    } else {
                        val errorBody = logoutResponseRetrofit.errorBody()?.string() ?: "Není k dispozici"
                        println("Repository: Chyba sítě/serveru při API odhlášení: ${logoutResponseRetrofit.code()}. Tělo chyby: $errorBody. Lokální smazání proběhne.") // Log
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    println("Repository: Neočekávaná chyba při API odhlášení: ${e.message}. Lokální smazání proběhne.") // Log
                }
            } else {
                println("Repository: Žádný token pro odhlášení, API logout nevolám, lokální smazání proběhne.") // Log
            }

            // **VŽDY smažte lokální token a credentials po pokusu o odhlášení**
            clearAuthToken() // **Volá public metodu**
            clearCredentials()

            // Po dokončení lokálního mazání vraťte úspěch (protože lokální stav je teď odhlášen)
            Result.success(Unit) // Vrací jednotku (Unit) jako signál úspěchu operace
        }
    }



    // --- Implementace logiky vyhledávání souborů (pro SearchViewModel) ---
    // Vrací Result<SearchResponse> při úspěchu, nebo chyba při selhání
    suspend fun searchFiles(query: String, category: String? = null, page: Int, perPage: Int = 20): Result<SearchResponse> { // Přijímá parametry vyhledávání a stránkování
        return withContext(Dispatchers.IO) { // Spustit v background vlákně
            val token = getAuthToken() // Získáme WST token ze SharedPreferences
            if (token.isNullOrEmpty()) {
                println("Repository: Vyhledávání selhalo, uživatel není přihlášen (token chybí).") // Log
                // Neodstraňovat token, ten už tam není.
                return@withContext Result.failure(Exception("Uživatel není přihlášen. Prosím, nejprve se přihlaste."))
            }

            try {
                println("Repository: Volám API pro vyhledávání s dotazem: '$query', kategorií: '$category', stránka: $page.") // Log
                // Zde voláte metodu na vašem Retrofit API rozhraní WebshareApiService
                val searchResponseRetrofit = apiService.searchFiles(
                    wstToken = token,
                    query = query,
                    category = category,
                    page = page,
                    perPage = perPage // Použijte parametr perPage
                )

                if (!searchResponseRetrofit.isSuccessful) {
                    val errorBody = searchResponseRetrofit.errorBody()?.string() ?: "Není k dispozici"
                    println("Repository: Chyba sítě při vyhledávání: ${searchResponseRetrofit.code()}, Tělo chyby: $errorBody") // Log
                    if (searchResponseRetrofit.code() == 401 /* Unauthorized */) {
                        println("Repository: Chyba 401 Unauthorized při vyhledávání - mažu token.") // Log
                        clearAuthToken() // **Maže token pomocí public metody**
                        return@withContext Result.failure(Exception("Token vypršel. Prosím, přihlaste se znovu."))
                    }
                    return@withContext Result.failure(Exception("Chyba sítě při vyhledávání: ${searchResponseRetrofit.code()}"))
                }

                val searchBody = searchResponseRetrofit.body() // Očekává SearchResponse
                if (searchBody == null) { // Zkontrolovat null odpověď od Retrofitu
                    println("Repository: Prázdná odpověď serveru při vyhledávání.") // Log
                    return@withContext Result.failure(Exception("Prázdná odpověď serveru při vyhledávání."))
                }

                println("Repository: Parsuji XML odpověď vyhledávání...") // Log
                // Předpokládáme, že Retrofit již provedl parsování do SearchResponse
                val searchData = searchBody // searchData je typu SearchResponse

                // Kontrola statusu odpovědi (z parsovaného XML)
                when (searchData.status) {
                    "OK" -> {
                        println("Repository: Vyhledávání API status OK. Nalezeno ${searchData.total} souborů celkem.") // Log
                        Result.success(searchData) // Vrátí SearchResponse objekt
                    }
                    "ERROR", "FATAL" -> {
                        println("Repository: Webshare API chyba při vyhledávání: ${searchData.message} (${searchData.code})") // Log
                        // TODO: Zkontrolovat konkrétní API kódy chyb, např. neplatný token (code 102?)
                        if (searchData.code == 102 /* Příklad kódu pro neplatný token dle API */) {
                            println("Repository: API kód ${searchData.code} při vyhledávání - mažu token.") // Log
                            clearAuthToken() // **Maže token pomocí public metody**
                            return@withContext Result.failure(Exception("Token vypršel nebo je neplatný. Prosím, přihlaste se znovu."))
                        }
                        Result.failure(Exception("Webshare API chyba při vyhledávání: ${searchData.message} (${searchData.code})"))
                    }
                    else -> {
                        println("Repository: Neznámý status odpovědi vyhledávání: ${searchData.status}") // Log
                        Result.failure(Exception("Neznámý status odpovědi vyhledávání: ${searchData.status}"))
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                println("Repository: Neočekávaná chyba při vyhledávání: ${e.message}") // Log
                Result.failure(e)
            }
        }
    }


    // --- Implementace logiky získání přímého odkazu na soubor pro přehrávání (pro SearchViewModel) ---
    // Vrací Result<String> (URL odkazu) při úspěchu, nebo chyba při selhání
    suspend fun getFileLink(fileId: String, filePassword: String? = null): Result<String> { // Přijímá fileId a heslo souboru
        return withContext(Dispatchers.IO) { // Spustit v background vlákně
            val token = getAuthToken() // Získáme WST token ze SharedPreferences
            if (token.isNullOrEmpty()) {
                println("Repository: Získání odkazu selhalo, uživatel není přihlášen (token chybí).") // Log
                // Neodstraňovat token, ten už tam není.
                return@withContext Result.failure(Exception("Uživatel není přihlášen. Prosím, nejprve se přihlaste."))
            }

            // TODO: Pokud soubor vyžaduje heslo (filePassword != null), zde by se měla spustit logika pro zadání hesla
            // API file_password_salt, hašování hesla souboru (pomocí HashingUtils) a předat hash do API volání fileLink.
            if (filePassword != null) {
                println("Repository: Získání odkazu pro soubor chráněný heslem - implementace chybí.") // Log
                return@withContext Result.failure(NotImplementedError("Získání odkazu pro soubor chráněný heslem zatím není implementováno."))
            }


            try {
                println("Repository: Volám API pro získání odkazu pro File ID: $fileId s tokenem.") // Log
                val fileLinkResponseRetrofit = apiService.getFileLink(
                    wstTokenHeader = token, // Předání tokenu do hlavičky (pokud API očekává)
                    fileId = fileId,
                    wstTokenData = token, // Předání tokenu jako data parametr (dle API docs)
                    passwordHash = null, // null pro nechráněné soubory
                    downloadType = "video_stream", // Explicitně si vyžádáme video stream
                    // TODO: Volitelně přidat informace o zařízení a rozlišení (pokud to API podporuje a vyžaduje)
                    // deviceUuid = getDeviceUuid(),
                    // deviceVendor = Build.MANUFACTURER,
                    // deviceModel = Build.MODEL,
                    // deviceResX = getScreenWidth(),
                    // deviceResY = getScreenHeight()
                )

                if (!fileLinkResponseRetrofit.isSuccessful) {
                    val errorBody = fileLinkResponseRetrofit.errorBody()?.string() ?: "Není k dispozici"
                    println("Repository: Chyba sítě při získání odkazu: ${fileLinkResponseRetrofit.code()}, Tělo chyby: $errorBody") // Log
                    // TODO: Ošetřit chybu 401 Unauthorized nebo chybu neplatného tokenu (code 102?)
                    // TODO: Ošetřit chyby jako Soubor nenalezen (FILE_LINK_FATAL_1), Špatné heslo (FILE_LINK_FATAL_3), Soubor nedostupný atd. (FATAL chyby z API)
                    if (fileLinkResponseRetrofit.code() == 401 /* Unauthorized */) {
                        println("Repository: Chyba 401 Unauthorized při získání odkazu - mažu token.") // Log
                        clearAuthToken() // **Maže token pomocí public metody**
                        return@withContext Result.failure(Exception("Token vypršel. Prosím, přihlaste se znovu."))
                    }
                    return@withContext Result.failure(Exception("Chyba sítě při získání odkazu: ${fileLinkResponseRetrofit.code()}"))
                }

                val fileLinkBody = fileLinkResponseRetrofit.body() // Očekává FileLinkResponse
                if (fileLinkBody == null) { // Zkontrolovat null odpověď od Retrofitu
                    println("Repository: Prázdná odpověď serveru při získání odkazu.") // Log
                    return@withContext Result.failure(Exception("Prázdná odpověď serveru při získání odkazu."))
                }

                println("Repository: Parsuji XML odpověď odkazu na soubor...") // Log
                // Předpokládáme, že Retrofit již provedl parsování do FileLinkResponse
                val fileLinkData = fileLinkBody // fileLinkData je typu FileLinkResponse

                // Kontrola statusu odpovědi (z parsovaného XML)
                when (fileLinkData.status) {
                    "OK" -> {
                        val url = fileLinkData.link
                        if (url.isNullOrEmpty()) {
                            println("Repository: Získání odkazu OK, ale URL nebylo nalezeno.") // Log
                            return@withContext Result.failure(Exception("Získání odkazu OK, ale URL nebylo nalezeno v odpovědi."))
                        }
                        println("Repository: Odkaz na soubor úspěšně získán.") // Log
                        Result.success(url) // Vrátí URL string
                    }
                    "ERROR", "FATAL" -> {
                        println("Repository: Webshare API chyba při získání odkazu: ${fileLinkData.message} (${fileLinkData.code})") // Log
                        // TODO: Zkontrolovat konkrétní API kódy chyb
                        if (fileLinkData.code == 102 /* Příklad kódu pro neplatný token dle API */) {
                            println("Repository: API kód ${fileLinkData.code} při získání odkazu - mažu token.") // Log
                            clearAuthToken() // **Maže token pomocí public metody**
                            return@withContext Result.failure(Exception("Token vypršel nebo je neplatný. Prosím, přihlaste se znovu."))
                        }
                        Result.failure(Exception("Webshare API chyba při získání odkazu: ${fileLinkData.message} (${fileLinkData.code})"))
                    }
                    else -> {
                        println("Repository: Neznámý status odpovědi pro odkaz: ${fileLinkData.status}") // Log
                        Result.failure(Exception("Neznámý status odpovědi pro odkaz: ${fileLinkData.status}"))
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                println("Repository: Neočekávaná chyba při získání odkazu: ${e.message}") // Log
                Result.failure(e)
            }
        }
    }


    // Implementace logiky pro získání uživatelských dat (pro SearchViewModel)
    // Bude vyžadovat WST token
    // Vrací Result<UserDataResponse> při úspěchu, nebo chyba při selhání
    suspend fun getUserData(): Result<UserDataResponse> { // Vrací UserDataResponse
        return withContext(Dispatchers.IO) { // Spustit v background vlákně
            val token = getAuthToken() // Získáme WST token ze SharedPreferences
            if (token.isNullOrEmpty()) {
                println("Repository: Získání uživatelských dat selhalo, uživatel není přihlášen.") // Log
                // Neodstraňovat token, ten už tam není.
                return@withContext Result.failure(Exception("Uživatel není přihlášen. Prosím, nejprve se přihlaste."))
            }

            try {
                println("Repository: Volám API pro získání uživatelských dat s tokenem.") // Log
                // Volání API pro získání uživatelských dat (předáme token v hlavičce I v těle)
                val userDataResponseRetrofit = apiService.getUserData(
                    wstTokenHeader = token, // Předáváme token do hlavičky (pokud API očekává)
                    wstTokenData = token // Předáváme token jako data parametr (dle API docs)
                )

                if (!userDataResponseRetrofit.isSuccessful) {
                    val errorBody = userDataResponseRetrofit.errorBody()?.string() ?: "Není k dispozici"
                    println("Repository: Chyba sítě při získání uživatelských dat: ${userDataResponseRetrofit.code()}, Tělo chyby: $errorBody") // Log
                    // TODO: Ošetřit chybu 401 Unauthorized nebo chybu neplatného tokenu (code 102?)
                    if (userDataResponseRetrofit.code() == 401 /* Unauthorized */) {
                        println("Repository: Chyba 401 Unauthorized při získání uživatelských dat - mažu token.") // Log
                        clearAuthToken() // **Maže token pomocí public metody**
                        return@withContext Result.failure(Exception("Token vypršel. Prosím, přihlaste se znovu."))
                    }
                    return@withContext Result.failure(Exception("Chyba sítě při získání uživatelských dat: ${userDataResponseRetrofit.code()}"))
                }

                val userDataBody = userDataResponseRetrofit.body() // Očekává UserDataResponse
                if (userDataBody == null) { // Zkontrolovat null odpověď od Retrofitu
                    println("Repository: Prázdná odpověď serveru při získání uživatelských dat.") // Log
                    return@withContext Result.failure(Exception("Prázdná odpověď serveru při získání uživatelských dat."))
                }

                println("Repository: Parsuji XML odpověď uživatelských dat...") // Log
                // Předpokládáme, že Retrofit již provedl parsování do UserDataResponse
                val userData = userDataBody // userData je typu UserDataResponse

                // Kontrola statusu odpovědi (z parsovaného XML)
                when (userData.status) {
                    "OK" -> {
                        println("Repository: Uživatelská data úspěšně zíksána.") // Log
                        Result.success(userData) // Úspěch, vracíme UserDataResponse objekt
                    }
                    "ERROR", "FATAL" -> {
                        println("Repository: Webshare API chyba při získání uživatelských dat: ${userData.message} (${userData.code})") // Log
                        // TODO: Zkontrolovat konkrétní API kódy chyb
                        if (userData.code == 102 /* Příklad kódu pro neplatný token dle API */) {
                            println("Repository: API kód ${userData.code} při získání uživatelských dat - mažu token.") // Log
                            clearAuthToken() // **Maže token pomocí public metody**
                            return@withContext Result.failure(Exception("Token vypršel nebo je neplatný. Prosím, přihlaste se znovu."))
                        }
                        Result.failure(Exception("Webshare API chyba při získání uživatelských dat: ${userData.message} (${userData.code})"))
                    }
                    else -> {
                        println("Repository: Neznámý status odpovědi pro uživatelská data: ${userData.status}") // Log
                        Result.failure(Exception("Neznámý status odpovědi pro uživatelská data: ${userData.status}"))
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                println("Repository: Neočekávaná chyba při získání uživatelských dat: ${e.message}") // Log
                Result.failure(e)
            }
        }
    }


    // TODO: Implementovat metody pro získání soli pro heslo k souboru (pro FileLink s heslem)
    /*
     suspend fun getFilePasswordSalt(fileId: String): Result<String> {
         // ... (volání API file_password_salt) ...
         Result.failure(NotImplementedError("Získání soli pro heslo k souboru zatím není implementováno.")) // Placeholder
     }
    */

    // TODO: Volitelně implementovat metody pro získání info o zařízení a rozlišení pro předání do API volání


    // TODO: Ujistěte se, že vaše třída AuthTokenManager.kt má metody saveToken, getToken, clearToken, saveCredentials, loadCredentials, clearCredentials
}