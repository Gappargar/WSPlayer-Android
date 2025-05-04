package com.example.wsplayer.data.repository // Váš balíček + .data.repository

import android.os.Build // Import pro Build info (pokud se používá v getFileLink)
import com.example.wsplayer.data.api.WebshareApiService // Import Retrofit API rozhraní
import com.example.wsplayer.data.models.* // Import datových tříd (* import pro všechny třídy v models)
import com.example.wsplayer.utils.HashingUtils // Import utility pro hašování
import com.example.wsplayer.utils.XmlUtils // Import utility pro parsování XML
import kotlinx.coroutines.Dispatchers // Import pro práci s Dispatchers (vlákny)
import kotlinx.coroutines.withContext // Import pro přepnutí kontextu (vlákna)
import kotlin.Result // Import třídy Result
import com.example.wsplayer.AuthTokenManager // Import AuthTokenManager

// Import pro UserDataResponse (pro metodu getUserData)
import com.example.wsplayer.data.models.UserDataResponse


// Repository pro komunikaci s Webshare.cz API
// Zapouzdřuje logiku volání API a zpracování dat
// Přijímá WebshareApiService pro volání API a AuthTokenManager pro správu tokenu a credentials
class WebshareRepository(
    private val apiService: WebshareApiService,
    private val authTokenManager: AuthTokenManager // AuthTokenManager spravuje token i credentials
) {

    // --- Správa autentizačního tokenu (spravuje AuthTokenManager) ---

    // Uloží autentizační token do AuthTokenManageru
    private fun saveAuthToken(token: String) {
        authTokenManager.saveToken(token) // Deleguje na AuthTokenManager
        println("Repository: Token uložen pomocí AuthTokenManageru.")
    }

    // Načte autentizační token z AuthTokenManageru
    fun getAuthToken(): String? {
        println("Repository: Získávám token z AuthTokenManageru...")
        return authTokenManager.getAuthToken() // Deleguje na AuthTokenManager
    }

    // Smaže autentizační token pomocí AuthTokenManageru
    fun clearAuthToken() {
        authTokenManager.clearToken() // Deleguje na AuthTokenManager
        println("Repository: Token smazán pomocí AuthTokenManageru.")
    }

    // --- Správa přihlašovacích údajů (Credentials) (spravuje AuthTokenManager) ---

    // Načte uložené uživatelské jméno a heslo z AuthTokenManageru
    // Vrací Pair<username, password> nebo null
    fun loadCredentials(): Pair<String, String>? {
        println("Repository: Načítám uložené údaje přes AuthTokenManager...")
        return authTokenManager.loadCredentials() // Deleguje na AuthTokenManager
    }
    
    

    // Uloží uživatelské jméno a heslo do AuthTokenManageru
    fun saveCredentials(username: String, password: String) {
        // TODO: Zvažte ukládání hashe hesla nebo šifrovaného hesla pro vyšší bezpečnost
        authTokenManager.saveCredentials(username, password) // Deleguje na AuthTokenManager (ukládá plain heslo)
        println("Repository: Ukládám credentials přes AuthTokenManager.")
    }

    // Smaže uložené uživatelské jméno a heslo z AuthTokenManageru
    fun clearCredentials() {
        authTokenManager.clearCredentials() // Deleguje na AuthTokenManager
        println("Repository: Mažu uložené credentials přes AuthTokenManager.")
    }

    // --- Logout (odhlášení) ---
    // Metoda pro odhlášení - volá API logout a poté maže lokální data
    suspend fun logout() {
        val token = getAuthToken() // Získáme aktuální token pro volání API

        if (!token.isNullOrEmpty()) { // Pokud máme token, zkusíme volat API
            try {
                println("Repository: Volám API pro odhlášení...")
                val logoutResponseRetrofit = apiService.logout(token) // Volání API logout

                if (logoutResponseRetrofit.isSuccessful) {
                    val logoutBody = logoutResponseRetrofit.body()
                    if (!logoutBody.isNullOrEmpty()) {
                        val status = XmlUtils.parseLogoutResponseXml(logoutBody) // Parsujeme status
                        if (status == "OK") {
                            println("Repository: API odhlášení úspěšné.")
                        } else {
                            println("Repository: API odhlášení vrátilo status: $status. Lokální smazání proběhne.")
                            // I když API nevrátilo OK, lokální stav smažeme
                        }
                    } else {
                        println("Repository: API odhlášení vrátilo prázdnou odpověď.")
                    }
                } else {
                    val errorBody = logoutResponseRetrofit.errorBody()?.string() ?: "Není k dispozici"
                    println("Repository: Chyba sítě při API odhlášení: ${logoutResponseRetrofit.code()}. Lokální smazání proběhne. Tělo chyby: $errorBody")
                    // I při chybě sítě lokální stav smažeme
                }
            } catch (e: Exception) {
                e.printStackTrace()
                println("Repository: Neočekávaná chyba při API odhlášení: ${e.message}. Lokální smazání proběhne.")
                // I při neočekávané chybě lokální stav smažeme
            }
        } else {
            println("Repository: Žádný token pro odhlášení, lokální smazání proběhne.")
        }

        // VŽDY smažte lokální token a credentials po pokusu o odhlášení API
        clearAuthToken() // Smaže token (v AuthTokenManageru)
        clearCredentials() // Smaže credentials (v AuthTokenManageru)
        // TODO: Signalizovat UI/ViewModelu (LoginViewModelu), že je uživatel odhlášen
    }


    // --- Implementace přihlašovací logiky ---
    // Vrací Result<String>, kde String je WST token při úspěchu, nebo chyba při selhání
    // **Nepřijímá rememberMe - tato logika je ve ViewModelu**
    suspend fun login(username: String, password: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // --- Fáze 1: Získání soli ---
                println("Repository: Získávám sůl pro uživatele $username...")
                val saltResponseRetrofit = apiService.getSalt(username)

                if (!saltResponseRetrofit.isSuccessful) {
                    val errorBody = saltResponseRetrofit.errorBody()?.string() ?: "Není k dispozici"
                    println("Repository: Chyba sítě při získávání soli: ${saltResponseRetrofit.code()}, Tělo chyby: $errorBody")
                    return@withContext Result.failure(Exception("Chyba sítě při získávání soli: ${saltResponseRetrofit.code()}"))
                }

                val saltBody = saltResponseRetrofit.body()
                if (saltBody.isNullOrEmpty()) {
                    println("Repository: Prázdná odpověď serveru při získávání soli.")
                    return@withContext Result.failure(Exception("Prázdná odpověď serveru při získávání soli."))
                }

                println("Repository: Parsuji XML odpověď soli...")
                val saltData = XmlUtils.parseSaltResponseXml(saltBody)

                if (saltData.status != "OK") {
                    println("Repository: Webshare API chyba při získávání soli: ${saltData.message} (${saltData.code})")
                    return@withContext Result.failure(Exception("Webshare API chyba při získávání soli: ${saltData.message} (${saltData.code})"))
                }

                val salt = saltData.salt
                if (salt.isNullOrEmpty()) {
                    println("Repository: Sůl nebyla v odpovědi API nalezena, ačkoli status byl OK.")
                    return@withContext Result.failure(Exception("Sůl nebyla v odpovědi API nalezena, ačkoli status byl OK."))
                }

                println("Repository: Sůl úspěšně získána.")

                // --- Fáze 2: Hašování hesla pomocí získané soli ---
                println("Repository: Provádím hašování hesla...")
                val passwordHashResult = HashingUtils.calculateWebsharePasswordHash(password, salt)

                if (passwordHashResult.isFailure) {
                    println("Repository: Hašování selhalo: ${passwordHashResult.exceptionOrNull()?.message}")
                    return@withContext Result.failure(passwordHashResult.exceptionOrNull() ?: Exception("Hašování selhalo z neznámého důvodu."))
                }

                val passwordHash = passwordHashResult.getOrThrow()

                if (passwordHash.isEmpty()) {
                    println("Repository: Hašování vrátilo úspěšný výsledek, ale hash je prázdný.")
                    return@withContext Result.failure(Exception("Hašování vrátilo úspěšný výsledek, ale hash je prázdný."))
                }

                println("Repository: Hašování hesla dokončeno. Hash (prvních 10 znaků): ${passwordHash.take(10)}...")


                // --- Fáze 3: Odeslání přihlašovacího požadavku na API ---
                println("Repository: Odesílám přihlašovací požadavek s hashem...")
                val loginResponseRetrofit = apiService.login(username, passwordHash, 1) // keep_logged_in = 1

                if (!loginResponseRetrofit.isSuccessful) {
                    val errorBody = loginResponseRetrofit.errorBody()?.string() ?: "Není k dispozici"
                    println("Repository: Chyba sítě při přihlášení: ${loginResponseRetrofit.code()}, Tělo chyby: $errorBody")
                    return@withContext Result.failure(Exception("Chyba sítě při přihlášení: ${loginResponseRetrofit.code()}"))
                }

                val loginBody = loginResponseRetrofit.body()
                if (loginBody.isNullOrEmpty()) {
                    println("Repository: Prázdná odpověď serveru při přihlášení.")
                    return@withContext Result.failure(Exception("Prázdná odpověď serveru při přihlášení."))
                }

                println("Repository: Parsuji XML odpověď přihlášení...")
                val loginData = XmlUtils.parseLoginResponseXml(loginBody)

                when (loginData.status) {
                    "OK" -> {
                        val token = loginData.token
                        if (token.isNullOrEmpty()) {
                            println("Repository: Přihlášení OK, ale token nebyl v odpovědi nalezen.")
                            return@withContext Result.failure(Exception("Přihlášení OK, ale token nebyl v odpovědi nalezen."))
                        }

                        // saveAuthToken(token) <- Toto zůstává!
                        saveAuthToken(token)


                        println("Repository: Přihlášení úspěšné! Token získán.")
                        Result.success(token) // Vrátí token
                    }
                    "ERROR", "FATAL" -> {
                        println("Repository: Webshare API chyba při přihlášení: ${loginData.message} (${loginData.code})")
                        Result.failure(Exception("Webshare API chyba při přihlášení: ${loginData.message} (${loginData.code})"))
                    }
                    else -> {
                        println("Repository: Neznámý status odpovědi přihlášení: ${loginData.status}")
                        Result.failure(Exception("Neznámý status odpovědi přihlášení: ${loginData.status}"))
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                println("Repository: Neočekávaná chyba v přihlašovací logice: ${e.message}")
                Result.failure(e)
            }
        }
    }


    // --- Implementace logiky vyhledávání souborů ---
    // Vrací Result<SearchResponse> při úspěchu, nebo chyba při selhání
    suspend fun searchFiles(query: String, sort: String? = null, limit: Int? = null, offset: Int? = null, category: String? = null): Result<SearchResponse> {
        return withContext(Dispatchers.IO) {
            val token = getAuthToken() // Získáme WST token ze SharedPreferences
            if (token.isNullOrEmpty()) {
                println("Repository: Vyhledávání selhalo, uživatel není přihlášen (token chybí).")
                return@withContext Result.failure(Exception("Uživatel není přihlášen. Prosím, nejprve se přihlaste."))
            }

            try {
                println("Repository: Volám API pro vyhledávání s dotazem: '$query' s tokenem.")
                val searchResponseRetrofit = apiService.searchFiles(token, query, sort, limit, offset, category)

                if (!searchResponseRetrofit.isSuccessful) {
                    val errorBody = searchResponseRetrofit.errorBody()?.string() ?: "Není k dispozici"
                    println("Repository: Chyba sítě při vyhledávání: ${searchResponseRetrofit.code()}, Tělo chyby: $errorBody")
                    if (searchResponseRetrofit.code() == 401 /* Unauthorized */) {
                        clearAuthToken() // Smaže token
                        return@withContext Result.failure(Exception("Token vypršel. Prosím, přihlaste se znovu."))
                    }
                    return@withContext Result.failure(Exception("Chyba sítě při vyhledávání: ${searchResponseRetrofit.code()}"))
                }

                val searchBody = searchResponseRetrofit.body()
                if (searchBody.isNullOrEmpty()) {
                    println("Repository: Prázdná odpověď serveru při vyhledávání.")
                    return@withContext Result.failure(Exception("Prázdná odpověď serveru při vyhledávání."))
                }

                println("Repository: Parsuji XML odpověď vyhledávání...")
                val searchData = XmlUtils.parseSearchResponseXml(searchBody)

                // Kontrola statusu odpovědi (z parsovaného XML)
                when (searchData.status) {
                    "OK" -> {
                        println("Repository: Vyhledávání úspěšné. Nalezeno ${searchData.total} souborů.")
                        Result.success(searchData)
                    }
                    "ERROR", "FATAL" -> {
                        println("Repository: Webshare API chyba při vyhledávání: ${searchData.message} (${searchData.code})")
                        if (searchData.code == 102 /* API kód pro neplatný token? */) {
                            clearAuthToken() // Smaže token
                            return@withContext Result.failure(Exception("Token vypršel nebo je neplatný. Prosím, přihlaste se znovu."))
                        }
                        Result.failure(Exception("Webshare API chyba při vyhledávání: ${searchData.message} (${searchData.code})"))
                    }
                    else -> {
                        println("Repository: Neznámý status odpovědi vyhledávání: ${searchData.status}")
                        Result.failure(Exception("Neznámý status odpovědi vyhledávání: ${searchData.status}"))
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                println("Repository: Neočekávaná chyba při vyhledávání: ${e.message}")
                Result.failure(e)
            }
        }
    }

    // --- Implementace logiky získání přímého odkazu na soubor pro přehrávání ---
    suspend fun getFileLink(fileId: String, filePassword: String? = null): Result<String> {
        return withContext(Dispatchers.IO) {
            val token = getAuthToken()
            if (token.isNullOrEmpty()) {
                println("Repository: Získání odkazu selhalo, uživatel není přihlášen.")
                return@withContext Result.failure(Exception("Uživatel není přihlášen. Prosím, nejprve se přihlaste."))
            }

            // TODO: Pokud filePassword != null, implementovat získání soli souboru (API file_password_salt),
            // hašování hesla souboru (pomocí HashingUtils) a předat hash do API volání fileLink.
            if (filePassword != null) {
                println("Repository: Získání odkazu pro soubor chráněný heslem - implementace chybí.")
                return@withContext Result.failure(NotImplementedError("Získání odkazu pro soubor chráněný heslem zatím není implementováno."))
            }


            try {
                println("Repository: Volám API pro získání odkazu pro File ID: $fileId s tokenem (v hlavičce i datech).")
                val fileLinkResponseRetrofit = apiService.getFileLink(
                    wstTokenHeader = token, // Předání tokenu do hlavičky
                    fileId = fileId,
                    wstTokenData = token, // <- PŘIDÁNO: Předání tokenu jako data parametru
                    passwordHash = null, // null pro nechráněné soubory
                    downloadType = "video_stream", // Explicitně si vyžádáme video stream
                    // TODO: Volitelně přidat informace o zařízení a rozlišení
                    // deviceUuid = getDeviceUuid(),
                    // deviceVendor = Build.MANUFACTURER,
                    // deviceModel = Build.MODEL,
                    // deviceResX = getScreenWidth(),
                    // deviceResY = getScreenHeight()
                )

                if (!fileLinkResponseRetrofit.isSuccessful) {
                    val errorBody = fileLinkResponseRetrofit.errorBody()?.string() ?: "Není k dispozici"
                    println("Repository: Chyba sítě při získání odkazu: ${fileLinkResponseRetrofit.code()}, Tělo chyby: $errorBody")
                    // TODO: Ošetřit chybu 401 Unauthorized nebo chybu neplatného tokenu (code 102?)
                    // TODO: Ošetřit chyby jako Soubor nenalezen (FILE_LINK_FATAL_1), Špatné heslo (FILE_LINK_FATAL_3), Soubor nedostupný atd. (FATAL chyby z API)
                    if (fileLinkResponseRetrofit.code() == 401 /* Unauthorized */) {
                        clearAuthToken() // Smaže token
                        return@withContext Result.failure(Exception("Token vypršel. Prosím, přihlaste se znovu."))
                    }
                    return@withContext Result.failure(Exception("Chyba sítě při získání odkazu: ${fileLinkResponseRetrofit.code()}"))
                }

                val fileLinkBody = fileLinkResponseRetrofit.body()
                if (fileLinkBody.isNullOrEmpty()) {
                    println("Repository: Prázdná odpověď serveru při získání odkazu.")
                    return@withContext Result.failure(Exception("Prázdná odpověď serveru při získání odkazu."))
                }

                println("Repository: Parsuji XML odpověď odkazu na soubor...")
                val fileLinkData = XmlUtils.parseFileLinkResponseXml(fileLinkBody)

                // Kontrola statusu odpovědi (z parsovaného XML)
                when (fileLinkData.status) {
                    "OK" -> {
                        val url = fileLinkData.link
                        if (url.isNullOrEmpty()) {
                            println("Repository: Získání odkazu OK, ale URL nebylo nalezeno.")
                            return@withContext Result.failure(Exception("Získání odkazu OK, ale URL nebylo nalezeno v odpovědi."))
                        }
                        println("Repository: Odkaz na soubor úspěšně získán.")
                        Result.success(url)
                    }
                    "ERROR", "FATAL" -> {
                        println("Repository: Webshare API chyba při získání odkazu: ${fileLinkData.message} (${fileLinkData.code})")
                        if (fileLinkData.code == 102 /* API kód pro neplatný token? */) {
                            clearAuthToken()
                            return@withContext Result.failure(Exception("Token vypršel nebo je neplatný. Prosím, přihlaste se znovu."))
                        }
                        Result.failure(Exception("Webshare API chyba při získání odkazu: ${fileLinkData.message} (${fileLinkData.code})"))
                    }
                    else -> {
                        println("Repository: Neznámý status odpovědi pro odkaz: ${fileLinkData.status}")
                        Result.failure(Exception("Neznámý status odpovědi pro odkaz: ${fileLinkData.status}"))
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                println("Repository: Neočekávaná chyba při získání odkazu: ${e.message}")
                Result.failure(e)
            }
        }
    }

    // **NOVÁ Metoda pro získání uživatelských dat**
    // Bude vyžadovat WST token
    // Vrací Result<UserDataResponse> při úspěchu, nebo chyba při selhání
    suspend fun getUserData(): Result<UserDataResponse> {
        return withContext(Dispatchers.IO) {
            val token = getAuthToken() // Získáme WST token ze SharedPreferences
            if (token.isNullOrEmpty()) {
                println("Repository: Získání uživatelských dat selhalo, uživatel není přihlášen.")
                return@withContext Result.failure(Exception("Uživatel není přihlášen. Prosím, nejprve se přihlaste."))
            }

            try {
                println("Repository: Volám API pro získání uživatelských dat s tokenem.")
                // Volání API pro získání uživatelských dat (předáme token v hlavičce I v těle)
                val userDataResponseRetrofit = apiService.getUserData(
                    wstTokenHeader = token, // Předáváme token do hlavičky
                    wstTokenData = token // **Přidáno: Předáváme token jako data parametr**
                )

                if (!userDataResponseRetrofit.isSuccessful) {
                    val errorBody = userDataResponseRetrofit.errorBody()?.string() ?: "Není k dispozici"
                    println("Repository: Chyba sítě při získání uživatelských dat: ${userDataResponseRetrofit.code()}, Tělo chyby: $errorBody")
                    if (userDataResponseRetrofit.code() == 401 /* Unauthorized */) {
                        clearAuthToken() // Smaže token
                        return@withContext Result.failure(Exception("Token vypršel. Prosím, přihlaste se znovu."))
                    }
                    return@withContext Result.failure(Exception("Chyba sítě při získání uživatelských dat: ${userDataResponseRetrofit.code()}"))
                }

                val userDataBody = userDataResponseRetrofit.body()
                if (userDataBody.isNullOrEmpty()) {
                    println("Repository: Prázdná odpověď serveru při získání uživatelských dat.")
                    return@withContext Result.failure(Exception("Prázdná odpověď serveru při získání uživatelských dat."))
                }

                println("Repository: Parsuji XML odpověď uživatelských dat...")
                val userData = XmlUtils.parseUserDataResponseXml(userDataBody) // <-- Volání naší parsovací funkce

                // Kontrola statusu odpovědi (z parsovaného XML)
                when (userData.status) {
                    "OK" -> {
                        println("Repository: Uživatelská data úspěšně zíksána.")
                        Result.success(userData) // Úspěch, vracíme UserDataResponse objekt
                    }
                    "ERROR", "FATAL" -> {
                        println("Repository: Webshare API chyba při získání uživatelských dat: ${userData.message} (${userData.code})")
                        if (userData.code == 102 /* API kód pro neplatný token? */) {
                            clearAuthToken() // Smaže token
                            return@withContext Result.failure(Exception("Token vypršel nebo je neplatný. Prosím, přihlaste se znovu."))
                        }
                        Result.failure(Exception("Webshare API chyba při získání uživatelských dat: ${userData.message} (${userData.code})"))
                    }
                    else -> {
                        println("Repository: Neznámý status odpovědi pro uživatelská data: ${userData.status}")
                        Result.failure(Exception("Neznámý status odpovědi pro uživatelská data: ${userData.status}"))
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                println("Repository: Neočekávaná chyba při získání uživatelských dat: ${e.message}")
                Result.failure(e)
            }
        }
    }


    // TODO: Implementovat metody pro získání soli pro heslo k souboru
    /*
     suspend fun getFilePasswordSalt(fileId: String): Result<String> {
         // ... (volání API file_password_salt) ...
         Result.failure(NotImplementedError("Získání soli pro heslo k souboru zatím není implementováno.")) // Placeholder
     }
    */

    // TODO: Implementovat metody pro získání info o zařízení a rozlišení
}