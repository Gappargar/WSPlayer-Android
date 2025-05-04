// app/src/main/java/com/example/wsplayer/data/repository/WebshareRepository.kt
package com.example.wsplayer.data.repository // Váš balíček - ZKONTROLUJTE

import android.os.Build // Potřeba pro Build info (pokud se používá v getFileLink)
import android.util.Log // Logování

import com.example.wsplayer.data.api.WebshareApiService // Import Retrofit API rozhraní
// **Importujte vaše modelové třídy z vašeho data.models balíčku**
// ZKONTROLUJTE, že cesta odpovídá vašemu umístění DataModels.kt
import com.example.wsplayer.data.models.* // Import datových tříd (* import pro všechny třídy v models)

import com.example.wsplayer.utils.HashingUtils // Import utility pro hašování (používá se ve login/getSalt metodách ViewModelu)
import com.example.wsplayer.utils.XmlUtils // Import utility pro parsování XML
import kotlinx.coroutines.Dispatchers // Pro background vlákna
import kotlinx.coroutines.withContext // Pro přepnutí kontextu
import retrofit2.Response // <-- Import pro Retrofit Response
import kotlin.Result // Import třídy Result
import com.example.wsplayer.AuthTokenManager // Import AuthTokenManager
// Repository pro komunikaci s Webshare.cz API
// Slouží jako jediný zdroj dat pro ViewModely.
// Zapouzdřuje logiku volání API, zpracování dat a správu lokálního stavu přes AuthTokenManager.

class WebshareRepository(
    private val apiService: WebshareApiService, // Přijímá instanci API služby
    private val authTokenManager: AuthTokenManager // Přijímá instanci AuthTokenManageru
) {

    private val TAG = "WebshareRepository" // Logovací tag pro Repository


    // --- Správa autentizačního tokenu a credentials (Deleguje na AuthTokenManager) ---

    // Uloží autentizační token (volá se zevnitř login metody)
    private fun saveAuthToken(token: String) {
        authTokenManager.saveToken(token) // Deleguje na AuthTokenManager
        Log.d(TAG, "Token uložen pomocí AuthTokenManageru.")
    }

    // Načte autentizační token (volá se ve ViewModelech k ověření přihlášení)
    fun getAuthToken(): String? {
        Log.d(TAG, "Získávám token z AuthTokenManageru...")
        return authTokenManager.getAuthToken() // Deleguje na AuthTokenManager
    }

    // **Smaže autentizační token** (PUBLIC metoda pro ViewModels jako součást logoutu nebo při chybě tokenu)
    fun clearAuthToken() {
        Log.d(TAG, "Volám clearAuthToken() na AuthTokenManageru.")
        authTokenManager.clearToken() // Deleguje na AuthTokenManager.clearToken() (Ověřte název metody v AuthTokenManageru!)
    }

    // Načte uložené credentials (pro auto-login)
    fun loadCredentials(): Pair<String, String>? {
        Log.d(TAG, "Načítám uložené credentials přes AuthTokenManager...")
        return authTokenManager.loadCredentials() // Vrací Pair<username, passwordHash> nebo null
    }

    // Uloží uživatelské jméno a hašované heslo
    fun saveCredentials(username: String, passwordHash: String) { // Očekává již HASH hesla
        Log.d(TAG, "Ukládám credentials přes AuthTokenManager.")
        authTokenManager.saveCredentials(username, passwordHash) // Deleguje na AuthTokenManager
    }

    // Smaže uložené credentials
    fun clearCredentials() {
        Log.d(TAG, "Mažu uložené credentials přes AuthTokenManager.")
        authTokenManager.clearCredentials() // Deleguje
    }


    // --- Implementace přihlašovací logiky (pro LoginViewModel) ---
    // Vrací Result<String>, kde String je WST token při úspěchu, nebo chyba
    // **Nepřijímá rememberMe - tato logika je ve ViewModelu**
    suspend fun login(username: String, passwordHash: String): Result<String> { // Přijímá již hašované heslo
        return withContext(Dispatchers.IO) { // Spustit v background vlákně
            try {
                // --- Fáze 1: Odeslání přihlašovacího požadavku na API ---
                Log.d(TAG, "Odesílám přihlašovací požadavek na API pro uživatele '$username' s hashem.")
                // **Zkontrolujte ZDE volání apiService.login()**
                // Názvy parametrů (username, password, keep_logged_in) se MUSÍ shodovat s těmi v ApiService.kt
                // Typ návratu je Response<String>
                val loginResponseRetrofit: Response<String> = apiService.login( // Explicitní typování
                    username = username, // Název parametru MUSÍ být 'username' (podle ApiService @Field)
                    password = passwordHash, // Název parametru MUSÍ být 'password' (podle ApiService @Field)
                    keepLoggedIn = 1 // Název parametru MUSÍ být 'keep_logged_in' (podle ApiService @Field)
                )

                if (!loginResponseRetrofit.isSuccessful) {
                    val errorBody = loginResponseRetrofit.errorBody()?.string() ?: "Není k dispozici"
                    Log.e(TAG, "Chyba sítě při přihlášení: ${loginResponseRetrofit.code()}, Tělo chyby: $errorBody")
                    return@withContext Result.failure(Exception("Chyba sítě při přihlášení: ${loginResponseRetrofit.code()}"))
                }

                val loginBody: String? = loginResponseRetrofit.body() // Získá RAW XML tělo jako String?
                if (loginBody.isNullOrEmpty()) {
                    Log.e(TAG, "Prázdná odpověď serveru při přihlášení.")
                    return@withContext Result.failure(Exception("Prázdná odpověď serveru při přihlášení."))
                }

                Log.d(TAG, "Parsuji XML odpověď přihlášení...")
                // **Zavolat parsovací metodu z XmlUtils s tělem Stringu**
                val loginData: LoginResponse = XmlUtils.parseLoginResponseXml(loginBody) // Volá metodu z XmlUtils, která vrátí LoginResponse objekt

                // Zde už pracujete s naparsovaným objektem loginData (typu LoginResponse)
                when (loginData.status) { // Přístup k vlastnosti 'status' na objektu LoginResponse
                    "OK" -> {
                        val token = loginData.token // Přístup k vlastnosti 'token'
                        if (token.isNullOrEmpty()) {
                            Log.e(TAG, "Přihlášení OK, ale token nebyl v odpovědi nalezen.")
                            return@withContext Result.failure(Exception("Přihlášení OK, ale token nebyl v odpovědi nalezen."))
                        }

                        // Token se uloží UVNITŘ Repository při úspěšném přihlášení
                        saveAuthToken(token) // **Uloží token pomocí private metody**


                        Log.d(TAG, "Přihlášení úspěšné! Token získán.")
                        Result.success(token) // Vrátí token ViewModelu
                    }
                    "ERROR", "FATAL" -> {
                        // Přístup k vlastnostem 'message' a 'code' na objektu LoginResponse
                        Log.e(TAG, "Webshare API chyba při přihlášení: ${loginData.message} (${loginData.code})")
                        Result.failure(Exception("Webshare API chyba při přihlášení: ${loginData.message} (${loginData.code})"))
                    }
                    else -> {
                        Log.e(TAG, "Neznámý status odpovědi přihlášení: ${loginData.status}")
                        Result.failure(Exception("Neznámý status odpovědi přihlášení: ${loginData.status}"))
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, "Neočekávaná chyba v přihlašovací logice: ${e.message}")
                Result.failure(e)
            }
        }
    }

    // --- Implementace logiky pro získání soli pro heslo (pro LoginViewModel) ---
    suspend fun getSalt(username: String): Result<String> { // Vrací Result<String> (sůl)
        return withContext(Dispatchers.IO) { // Spustit v background vlákně
            try {
                Log.d(TAG, "Volám API pro získání soli pro uživatele '$username'.")
                // **Zkontrolujte ZDE volání apiService.getSalt()**
                // Názvy parametrů (username_or_email) se MUSÍ shodovat s ApiService
                val saltResponseRetrofit: Response<String> = apiService.getSalt(username = username) // Očekává Response<String>

                if (!saltResponseRetrofit.isSuccessful) {
                    val errorBody = saltResponseRetrofit.errorBody()?.string() ?: "Není k dispozici"
                    Log.e(TAG, "Chyba sítě při získávání soli: ${saltResponseRetrofit.code()}, Tělo chyby: $errorBody")
                    return@withContext Result.failure(Exception("Chyba sítě při získávání soli: ${saltResponseRetrofit.code()}"))
                }

                val saltBody: String? = saltResponseRetrofit.body() // Získá RAW XML tělo jako String?
                if (saltBody.isNullOrEmpty()) {
                    Log.e(TAG, "Prázdná odpověď serveru při získávání soli.")
                    return@withContext Result.failure(Exception("Prázdná odpověď serveru při získávání soli."))
                }

                Log.d(TAG, "Parsuji XML odpověď soli...")
                // **Zavolat parsovací metodu z XmlUtils s tělem Stringu**
                val saltData: SaltResponse = XmlUtils.parseSaltResponseXml(saltBody) // Volá metodu z XmlUtils, vrací SaltResponse

                // Zde už pracujete s naparsovaným objektem saltData (typu SaltResponse)
                if (saltData.status != "OK") {
                    Log.e(TAG, "Webshare API chyba při získávání soli: ${saltData.message} (${saltData.code})")
                    return@withContext Result.failure(Exception("Webshare API chyba při získávání soli: ${saltData.message} (${saltData.code})"))
                }

                val salt = saltData.salt // Přístup k vlastnosti 'salt'
                if (salt.isNullOrEmpty()) {
                    Log.e(TAG, "Sůl nebyla v odpovědi API nalezena, ačkoli status byl OK.")
                    return@withContext Result.failure(Exception("Sůl nebyla v odpovědi API nalezena, ačkoli status byl OK."))
                }

                Log.d(TAG, "Sůl úspěšně získána: '$salt'.")
                Result.success(salt) // Vrátí sůl (String)
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, "Neočekávaná chyba při získávání soli: ${e.message}")
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
                    Log.d(TAG, "Volám API pro odhlášení s tokenem...")
                    val logoutResponseRetrofit = apiService.logout(token) // Volání API logout

                    if (logoutResponseRetrofit.isSuccessful) {
                        val logoutBody = logoutResponseRetrofit.body() // Očekává String
                        if (!logoutBody.isNullOrEmpty()) {
                            // Předpokládáme, že odpověď je jen status OK/ERROR/FATAL (dle dokumentace)
                            val status = XmlUtils.parseLogoutResponseXml(logoutBody) // Potřebuje parsovací metodu pro logout
                            if (status == "OK") {
                                Log.d(TAG, "API odhlášení úspěšné. Status OK.")
                            } else {
                                Log.e(TAG, "API odhlášení vrátilo status: $status. Lokální smazání proběhne nezávisle.")
                            }
                        } else {
                            Log.e(TAG, "API odhlášení vrátilo prázdné tělo odpovědi.")
                        }
                    } else {
                        val errorBody = logoutResponseRetrofit.errorBody()?.string() ?: "Není k dispozici"
                        Log.e(TAG, "Chyba sítě/serveru při API odhlášení: ${logoutResponseRetrofit.code()}. Tělo chyby: $errorBody. Lokální smazání proběhne.")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e(TAG, "Neočekávaná chyba při API odhlášení: ${e.message}. Lokální smazání proběhne.")
                }
            } else {
                Log.d(TAG, "Žádný token pro odhlášení, API logout nevolám, lokální smazání proběhne.")
            }

            // **VŽDY smažte lokální token a credentials po pokusu o odhlášení**
            clearAuthToken() // **Volá public metodu clearAuthToken()**
            clearCredentials()

            // Po dokončení lokálního mazání vraťte úspěch (protože lokální stav je teď odhlášen)
            Result.success(Unit) // Vrací jednotku (Unit) jako signál úspěchu operace
        }
    }



    // --- Implementace logiky vyhledávání souborů (pro SearchViewModel) ---
    // Vrací Result<SearchResponse> při úspěchu, nebo chyba při selhání
    suspend fun searchFiles(query: String, category: String? = null, page: Int, itemsPerPage: Int = 20): Result<SearchResponse> { // <-- Zde je parametr itemsPerPage
        Log.d(TAG, "searchFiles() volán s dotazem: '$query', kategorií: '$category', stránka: $page, na stránku: $itemsPerPage") // Log na začátku metody

        return withContext(Dispatchers.IO) { // Spustit v background vlákně
            val token = getAuthToken() // Získáme WST token ze SharedPreferences
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Vyhledávání selhalo, uživatel není přihlášen (token chybí).")
                return@withContext Result.failure(Exception("Uživatel není přihlášen. Prosím, nejprve se přihlaste."))
            }

            try {
                Log.d(TAG, "Volám API pro vyhledávání s dotazem: '$query', kategorií: '$category', stránka: $page.")
                // **Zkontrolujte ZDE volání apiService.searchFiles()**
                // Názvy parametrů (token, query, category, offset, limit) se MUSÍ shodovat s těmi v ApiService (WebshareApiService.kt)
                // Parametr 'page' z Repository se mapuje na 'offset' v ApiService
                // Parametr 'itemsPerPage' z Repository se mapuje na 'limit' v ApiService
                val searchResponseRetrofit: Response<String> = apiService.searchFiles(
                    token = token, // Název parametru v ApiService je 'token' (předává se do @Field("wst"))
                    query = query, // Název parametru v ApiService je 'query' (předává se do @Field("what")) - pokud jste opravil(a) @Field
                    category = category, // Název parametru v ApiService je 'category'
                    offset = page, // <-- Správný název parametru v ApiService je 'offset' (předává se do @Field("offset"))
                    limit = itemsPerPage // <-- Správný název parametru v ApiService je 'limit' (předává se do @Field("limit"))
                    // Pokud máte další volitelné parametry v ApiService (např. device info), musí být i zde volány se správnými názvy
                )

                if (!searchResponseRetrofit.isSuccessful) {
                    val errorBody = searchResponseRetrofit.errorBody()?.string() ?: "Není k dispozici"
                    Log.e(TAG, "Chyba sítě při vyhledávání: ${searchResponseRetrofit.code()}, Tělo chyby: $errorBody") // .code pro HTTP kód
                    if (searchResponseRetrofit.code() == 401 /* Unauthorized */) {
                        Log.e(TAG, "Chyba 401 Unauthorized při vyhledávání - mažu token.")
                        clearAuthToken() // Maže token pomocí public metody v Repository (která volá AuthTokenManager)
                        return@withContext Result.failure(Exception("Token vypršel. Prosím, přihlaste se znovu."))
                    }
                    return@withContext Result.failure(Exception("Chyba sítě při vyhledávání: ${searchResponseRetrofit.code()}")) // .code pro HTTP kód
                }

                val searchBody: String? = searchResponseRetrofit.body() // Získá RAW XML tělo jako String?
                if (searchBody.isNullOrEmpty()) {
                    Log.e(TAG, "Prázdná odpověď serveru při vyhledávání.")
                    return@withContext Result.failure(Exception("Prázdná odpověď serveru při vyhledávání."))
                }

                Log.d(TAG, "Parsuji XML odpověď vyhledávání...")
                // **Zavolat parsovací metodu z XmlUtils s tělem Stringu**
                val searchData: SearchResponse = XmlUtils.parseSearchResponseXml(searchBody) // Volá metodu z XmlUtils, která musí vrátit SearchResponse objekt

                // Zde už pracujete s naparsovaným objektem searchData (typu SearchResponse)
                when (searchData.status) { // Přístup k vlastnosti 'status' na objektu SearchResponse
                    "OK" -> {
                        val files = searchData.files // Přístup k vlastnosti 'files' na objektu SearchResponse (List<FileModel>?)
                        val total = searchData.total // Přístup k vlastnosti 'total' na objektu SearchResponse (Int)
                        Log.d(TAG, "Vyhledávání API status OK. Nalezeno $total souborů celkem.")
                        Result.success(searchData) // Vrátí SearchResponse objekt obsahující seznam FileModel a total
                    }
                    "ERROR", "FATAL" -> {
                        // Přístup k vlastnostem 'message' a 'code' na objektu SearchResponse (z parsovaného XML)
                        Log.e(TAG, "Webshare API chyba při vyhledávání: ${searchData.message} (${searchData.code})")
                        // TODO: Zkontrolovat konkrétní API kódy chyb
                        if (searchData.code == 102 /* Příklad kódu pro neplatný token dle API */) {
                            Log.e(TAG, "API kód ${searchData.code} při vyhledávání - mažu token.")
                            clearAuthToken() // Maže token pomocí public metody
                            return@withContext Result.failure(Exception("Token vypršel nebo je neplatný. Prosím, přihlaste se znovu."))
                        }
                        Result.failure(Exception("Webshare API chyba při vyhledávání: ${searchData.message} (${searchData.code})"))
                    }
                    else -> {
                        Log.e(TAG, "Neznámý status odpovědi vyhledávání: ${searchData.status}")
                        Result.failure(Exception("Neznámý status odpovědi vyhledávání: ${searchData.status}"))
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, "Neočekávaná chyba při vyhledávání: ${e.message}")
                Result.failure(e)
            }
        }
    }


    // --- Implementace logiky získání přímého odkazu na soubor pro přehrávání (pro SearchViewModel) ---
    // Vrací Result<String> (URL odkazu) při úspěchu, nebo chyba při selhání
    suspend fun getFileLink(fileId: String, filePassword: String? = null): Result<String> { // Přijímá fileId a heslo souboru
        Log.d(TAG, "getFileLink() volán pro File ID: $fileId.") // Log na začátku metody

        return withContext(Dispatchers.IO) { // Spustit v background vlákně
            val token = getAuthToken() // Získáme WST token ze SharedPreferences
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Získání odkazu selhalo, uživatel není přihlášen (token chybí).")
                return@withContext Result.failure(Exception("Uživatel není přihlášen. Prosím, nejprve se přihlaste."))
            }

            // TODO: Pokud soubor vyžaduje heslo (filePassword != null), zde by se měla spustit logika pro zadání hesla
            // API file_password_salt, hašování hesla souboru (pomocí HashingUtils) a předat hash do API volání fileLink.
            // Prozatím je tato logika jen placeholder.
            if (filePassword != null) {
                Log.e(TAG, "Získání odkazu pro soubor chráněný heslem - implementace chybí.")
                // Předpokládáme, že FileLinkState.Error se jmenuje Error v DataModels (pokud LinkError, použijte LinkError)
                return@withContext Result.failure(NotImplementedError("Získání odkazu pro soubor chráněný heslem zatím není implementováno.")) // Vrací chybu, pokud je heslo zadáno ale funkce není implementována
            }

            // Haš hesla souboru k odeslání API (zatím null pro nechráněné)
            val passwordHashToSend = filePassword // Zde by se předával haš hesla, pokud by bylo zadáno a hašováno

            try {
                Log.d(TAG, "Volám API pro získání odkazu pro File ID: $fileId s tokenem.")
                // **OPRAVA: Zkontrolujte ZDE volání apiService.getFileLink()**
                // Názvy parametrů se MUSÍ shodovat s těmi v ApiService (authHeader, fileId, tokenData, password, download_type, atd.)
                // Typ návratu je Response<String>
                val fileLinkResponseRetrofit: Response<String> = apiService.getFileLink(
                    authHeader = token!!, // Název parametru v ApiService MUSÍ být 'authHeader'
                    fileId = fileId, // Název parametru v ApiService MUSÍ být 'fileId' (dle @Field("ident"))
                    tokenData = token!!, // Název parametru v ApiService MUSÍ být 'tokenData' (@Field("wst"))
                    password = passwordHashToSend, // <-- Název parametru v ApiService MUSÍ být 'password' (@Field("password")) - sem předáváme haš
                    download_type = "video_stream" // <-- Název parametru v ApiService MUSÍ být 'download_type' (@Field("download_type"))
                    // TODO: Zkontrolujte další volitelné parametry zařízení v ApiService a případně je zde předejte
                    // device_uuid = getDeviceUuid(),
                    // device_vendor = Build.MANUFACTURER,
                    // device_model = Build.MODEL,
                    // device_res_x = getScreenWidth(),
                    // device_res_y = getScreenHeight()
                )

                if (!fileLinkResponseRetrofit.isSuccessful) {
                    val errorBody = fileLinkResponseRetrofit.errorBody()?.string() ?: "Není k dispozici"
                    Log.e(TAG, "Chyba sítě při získání odkazu: ${fileLinkResponseRetrofit.code()}, Tělo chyby: $errorBody") // .code pro HTTP kód
                    if (fileLinkResponseRetrofit.code() == 401 /* Unauthorized */) {
                        Log.e(TAG, "Chyba 401 Unauthorized při získání odkazu - mažu token.")
                        clearAuthToken() // Maže token pomocí public metody v Repository
                        return@withContext Result.failure(Exception("Token vypršel. Prosím, přihlaste se znovu."))
                    }
                    return@withContext Result.failure(Exception("Chyba sítě při získání odkazu: ${fileLinkResponseRetrofit.code()}")) // .code pro HTTP kód
                }

                val fileLinkBody: String? = fileLinkResponseRetrofit.body() // Získá RAW XML String?
                if (fileLinkBody.isNullOrEmpty()) {
                    Log.e(TAG, "Prázdná odpověď serveru při získání odkazu.")
                    return@withContext Result.failure(Exception("Prázdná odpověď serveru při získání odkazu."))
                }

                Log.d(TAG, "Parsuji XML odpověď odkazu na soubor...")
                // **Zavolat parsovací metodu z XmlUtils s tělem Stringu**
                val fileLinkData: FileLinkResponse = XmlUtils.parseFileLinkResponseXml(fileLinkBody) // Volá metodu z XmlUtils, která musí vrátit FileLinkResponse objekt

                // Zde už pracujete s naparsovaným objektem fileLinkData (typu FileLinkResponse)
                when (fileLinkData.status) { // Přístup k vlastnosti 'status' na FileLinkResponse
                    "OK" -> {
                        val url = fileLinkData.link // Přístup k vlastnosti 'link' na FileLinkResponse
                        if (url.isNullOrEmpty()) {
                            Log.e(TAG, "Získání odkazu OK, ale URL nebylo nalezeno.")
                            return@withContext Result.failure(Exception("Získání odkazu OK, ale URL nebylo nalezeno v odpovědi."))
                        }
                        Log.d(TAG, "Odkaz na soubor úspěšně získán.")
                        Result.success(url) // Vrátí URL string
                    }
                    "ERROR", "FATAL" -> {
                        // Přístup k vlastnostem 'message' a 'code' na FileLinkResponse
                        Log.e(TAG, "Webshare API chyba při získání odkazu: ${fileLinkData.message} (${fileLinkData.code})")
                        // TODO: Ošetřit konkrétní API kódy chyb (např. neplatný token, špatné heslo souboru)
                        if (fileLinkData.code == 102 /* Příklad kódu pro neplatný token */) {
                            Log.e(TAG, "API kód ${fileLinkData.code} při získání odkazu - mažu token.")
                            clearAuthToken() // Maže token pomocí public metody
                            return@withContext Result.failure(Exception("Token vypršel nebo je neplatný. Prosím, přihlaste se znovu."))
                        }
                        // Můžete zde přidat specifické chyby pro heslo souboru atd.
                        Result.failure(Exception("Webshare API chyba při získání odkazu: ${fileLinkData.message} (${fileLinkData.code})"))
                    }
                    else -> {
                        Log.e(TAG, "Neznámý status odpovědi pro odkaz: ${fileLinkData.status}")
                        Result.failure(Exception("Neznámý status odpovědi pro odkaz: ${fileLinkData.status}"))
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, "Neočekávaná chyba při získání odkazu: ${e.message}")
                Result.failure(e)
            }
        }
    }
    // Implementace logiky pro získání uživatelských dat (pro SearchViewModel)
    // Vrací Result<UserDataResponse> při úspěchu, nebo chyba při selhání
    suspend fun getUserData(): Result<UserDataResponse> { // Vrací UserDataResponse
        Log.d(TAG, "getUserData() volán.") // Log na začátku metody

        return withContext(Dispatchers.IO) { // Spustit v background vlákně
            val token = getAuthToken() // Získáme WST token ze SharedPreferences
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Získání uživatelských dat selhalo, uživatel není přihlášen.")
                return@withContext Result.failure(Exception("Uživatel není přihlášen. Prosím, nejprve se přihlaste."))
            }

            try {
                Log.d(TAG, "Volám API pro získání uživatelských dat s tokenem.")
                // **OPRAVA: Zkontrolujte ZDE volání apiService.getUserData()**
                // Názvy parametrů se MUSÍ shodovat s těmi v ApiService.kt (authHeader, tokenData)
                // Typ návratu je Response<String>
                val userDataResponseRetrofit: Response<String> = apiService.getUserData(
                    authHeader = token!!, // <-- Název parametru v ApiService je 'authHeader'
                    tokenData = token!! // <-- Název parametru v ApiService je 'tokenData' (@Field("wst"))
                    // Zkontrolujte další volitelné parametry zařízení, pokud jsou definovány v ApiService
                    // device_uuid = ..., device_vendor = ..., etc. // Názvy musí odpovídat ApiService
                )

                if (!userDataResponseRetrofit.isSuccessful) {
                    val errorBody = userDataResponseRetrofit.errorBody()?.string() ?: "Není k dispozici"
                    Log.e(TAG, "Chyba sítě při získání uživatelských dat: ${userDataResponseRetrofit.code()}, Tělo chyby: $errorBody") // .code pro HTTP kód
                    if (userDataResponseRetrofit.code() == 401 /* Unauthorized */) {
                        Log.e(TAG, "Chyba 401 Unauthorized při získání uživatelských dat - mažu token.")
                        clearAuthToken() // Maže token pomocí public metody v Repository
                        return@withContext Result.failure(Exception("Token vypršel. Prosím, přihlaste se znovu."))
                    }
                    return@withContext Result.failure(Exception("Chyba sítě při získání uživatelských dat: ${userDataResponseRetrofit.code()}")) // .code pro HTTP kód
                }

                val userDataBody: String? = userDataResponseRetrofit.body() // Získá RAW XML String?
                if (userDataBody.isNullOrEmpty()) {
                    Log.e(TAG, "Prázdná odpověď serveru při získání uživatelských dat.")
                    return@withContext Result.failure(Exception("Prázdná odpověď serveru při získání uživatelských dat."))
                }

                Log.d(TAG, "Parsuji XML odpověď uživatelských dat...")
                // **Zavolat parsovací metodu z XmlUtils s tělem Stringu**
                val userData: UserDataResponse = XmlUtils.parseUserDataResponseXml(userDataBody) // Volá metodu z XmlUtils, která musí vrátit UserDataResponse objekt

                // Zde už pracujete s naparsovaným objektem userData (typu UserDataResponse)
                when (userData.status) { // Přístup k vlastnosti 'status' na UserDataResponse
                    "OK" -> {
                        Log.d(TAG, "Uživatelská data úspěšně zíksána.")
                        Result.success(userData) // Vrátí UserDataResponse objekt
                    }
                    "ERROR", "FATAL" -> {
                        // Přístup k vlastnostem 'message' a 'code' na UserDataResponse
                        Log.e(TAG, "Webshare API chyba při získání uživatelských dat: ${userData.message} (${userData.code})")
                        if (userData.code == 102 /* Příklad kódu pro neplatný token dle API */) {
                            Log.e(TAG, "API kód ${userData.code} při získání uživatelských dat - mažu token.")
                            clearAuthToken() // Maže token pomocí public metody
                            return@withContext Result.failure(Exception("Token vypršel nebo je neplatný. Prosím, přihlaste se znovu."))
                        }
                        Result.failure(Exception("Webshare API chyba při získání uživatelských dat: ${userData.message} (${userData.code})"))
                    }
                    else -> {
                        Log.e(TAG, "Neznámý status odpovědi pro uživatelská data: ${userData.status}")
                        Result.failure(Exception("Neznámý status odpovědi pro uživatelská data: ${userData.status}"))
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, "Neočekávaná chyba při získání uživatelských dat: ${e.message}")
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

    // TODO: Implementovat metody pro získání info o zařízení a rozlišení pro předání do API volání


    // TODO: Ujistěte se, že vaše třída AuthTokenManager.kt má metody saveToken, getToken, clearToken, saveCredentials, loadCredentials, clearCredentials
}