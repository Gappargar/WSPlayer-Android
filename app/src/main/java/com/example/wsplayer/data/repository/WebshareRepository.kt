package com.example.wsplayer.data.repository // Váš balíček - ZKONTROLUJTE

import android.os.Build // Potřeba pro Build info (pokud se používá v getFileLink)
import android.util.Log // Logování

import com.example.wsplayer.data.api.WebshareApiService // Import Retrofit API rozhraní (ZKONTROLUJTE CESTU)
// **Importujte vaše modelové třídy z vašeho data.models balíčku**
// ZKONTROLUJTE, že cesta odpovídá vašemu umístění DataModels.kt
import com.example.wsplayer.data.models.* // Import datových tříd (* import pro všechny třídy v models)

import com.example.wsplayer.utils.HashingUtils // Import utility pro hašování (používá se ve login/getSalt metodách ViewModelu)
import com.example.wsplayer.utils.XmlUtils // Import utility pro parsování XML (ZKONTROLUJTE CESTU)

import kotlinx.coroutines.Dispatchers // Pro background vlákna
import kotlinx.coroutines.withContext // Pro přepnutí kontextu

import retrofit2.Response // <-- Import pro Retrofit Response
import kotlin.Result // Import třídy Result
import com.example.wsplayer.AuthTokenManager // Import AuthTokenManager (ZKONTROLUJTE CESTU)

import kotlin.Pair // Import třídy Pair, pokud není implicitní


// Repository pro komunikaci s Webshare.cz API
// Slouží jako jediný zdroj dat pro ViewModely.
// Zapouzdřuje logiku volání API, zpracování dat a správu lokálního stavu přes AuthTokenManager.
class WebshareRepository(
    private val apiService: WebshareApiService, // Přijímá instanci API služby
    private val authTokenManager: AuthTokenManager // Přijímá instanci AuthTokenManageru
) {

    private val TAG = "WebshareRepository" // Logovací tag pro Repository


    // --- Správa autentizačního tokenu a credentials (Deleguje na AuthTokenManager) ---

    private fun saveAuthToken(token: String) {
        authTokenManager.saveToken(token)
        Log.d(TAG, "Token uložen pomocí AuthTokenManageru.")
    }

    fun getAuthToken(): String? {
        Log.d(TAG, "Získávám token z AuthTokenManageru...")
        return authTokenManager.getAuthToken()
    }

    fun clearAuthToken() {
        Log.d(TAG, "Volám clearAuthToken() na AuthTokenManageru.")
        authTokenManager.clearToken()
    }

    fun loadCredentials(): Pair<String, String>? {
        Log.d(TAG, "Načítám uložené credentials přes AuthTokenManager...")
        return authTokenManager.loadCredentials()
    }

    fun saveCredentials(username: String, passwordHash: String) {
        Log.d(TAG, "Ukládám credentials přes AuthTokenManager.")
        authTokenManager.saveCredentials(username, passwordHash)
    }

    fun clearCredentials() {
        Log.d(TAG, "Mažu uložené credentials přes AuthTokenManager.")
        authTokenManager.clearCredentials()
    }

    suspend fun getUserData(): Result<UserDataResponse> {
        Log.d(TAG, "getUserData() volán.")

        return withContext(Dispatchers.IO) {
            val token = getAuthToken()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Získání uživatelských dat selhalo, uživatel není přihlášen.")
                return@withContext Result.failure(Exception("Uživatel není přihlášen. Prosím, nejprve se přihlaste."))
            }

            try {
                Log.d(TAG, "Volám API pro získání uživatelských dat s tokenem.")
                val userDataResponseRetrofit: Response<String> = apiService.getUserData(
                    authHeader = token, // Opraveno z token!! na token, null check je výše
                    tokenData = token    // Opraveno z token!! na token
                )

                if (!userDataResponseRetrofit.isSuccessful) {
                    val errorBody = userDataResponseRetrofit.errorBody()?.string() ?: "Není k dispozici"
                    Log.e(TAG, "Chyba sítě při získání uživatelských dat: ${userDataResponseRetrofit.code()}, Tělo chyby: $errorBody")
                    if (userDataResponseRetrofit.code() == 401) {
                        Log.e(TAG, "Chyba 401 Unauthorized při získání uživatelských dat - mažu token.")
                        clearAuthToken()
                        return@withContext Result.failure(Exception("Token vypršel. Prosím, přihlaste se znovu."))
                    }
                    return@withContext Result.failure(Exception("Chyba sítě při získání uživatelských dat: ${userDataResponseRetrofit.code()}"))
                }

                val userDataBody: String? = userDataResponseRetrofit.body()
                if (userDataBody.isNullOrEmpty()) {
                    Log.e(TAG, "Prázdná odpověď serveru při získání uživatelských dat.")
                    return@withContext Result.failure(Exception("Prázdná odpověď serveru při získání uživatelských dat."))
                }

                Log.d(TAG, "Parsuji XML odpověď uživatelských dat...")
                val userData: UserDataResponse = XmlUtils.parseUserDataResponseXml(userDataBody)

                when (userData.status) {
                    "OK" -> {
                        Log.d(TAG, "Uživatelská data úspěšně zíksána.")
                        Result.success(userData)
                    }
                    "ERROR", "FATAL" -> {
                        Log.e(TAG, "Webshare API chyba při získání uživatelských dat: ${userData.message} (${userData.code})")
                        if (userData.code == 102) { // Příklad kódu pro neplatný token
                            Log.e(TAG, "API kód ${userData.code} při získání uživatelských dat - mažu token.")
                            clearAuthToken()
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
                Log.e(TAG, "Neočekávaná chyba při získání uživatelských dat: ${e.message}", e) // Přidáno e do logu
                Result.failure(e)
            }
        }
    }

    suspend fun login(username: String, passwordHash: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Odesílám přihlašovací požadavek na API pro uživatele '$username' s hashem.")
                val loginResponseRetrofit: Response<String> = apiService.login(
                    username = username,
                    password = passwordHash,
                    keepLoggedIn = 1
                )

                if (!loginResponseRetrofit.isSuccessful) {
                    val errorBody = loginResponseRetrofit.errorBody()?.string() ?: "Není k dispozici"
                    Log.e(TAG, "Chyba sítě při přihlášení: ${loginResponseRetrofit.code()}, Tělo chyby: $errorBody")
                    return@withContext Result.failure(Exception("Chyba sítě při přihlášení: ${loginResponseRetrofit.code()}"))
                }

                val loginBody: String? = loginResponseRetrofit.body()
                if (loginBody.isNullOrEmpty()) {
                    Log.e(TAG, "Prázdná odpověď serveru při přihlášení.")
                    return@withContext Result.failure(Exception("Prázdná odpověď serveru při přihlášení."))
                }

                Log.d(TAG, "Parsuji XML odpověď přihlášení...")
                val loginData: LoginResponse = XmlUtils.parseLoginResponseXml(loginBody)

                when (loginData.status) {
                    "OK" -> {
                        val token = loginData.token
                        if (token.isNullOrEmpty()) {
                            Log.e(TAG, "Přihlášení OK, ale token nebyl v odpovědi nalezen.")
                            return@withContext Result.failure(Exception("Přihlášení OK, ale token nebyl v odpovědi nalezen."))
                        }
                        saveAuthToken(token)
                        Log.d(TAG, "Přihlášení úspěšné! Token získán.")
                        Result.success(token)
                    }
                    "ERROR", "FATAL" -> {
                        Log.e(TAG, "Webshare API chyba při přihlášení: ${loginData.message} (${loginData.code})")
                        Result.failure(Exception("Webshare API chyba při přihlášení: ${loginData.message} (${loginData.code})"))
                    }
                    else -> {
                        Log.e(TAG, "Neznámý status odpovědi přihlášení: ${loginData.status}")
                        Result.failure(Exception("Neznámý status odpovědi přihlášení: ${loginData.status}"))
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Neočekávaná chyba v přihlašovací logice: ${e.message}", e) // Přidáno e do logu
                Result.failure(e)
            }
        }
    }

    suspend fun getSalt(username: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Volám API pro získání soli pro uživatele '$username'.")
                val saltResponseRetrofit: Response<String> = apiService.getSalt(username = username)

                if (!saltResponseRetrofit.isSuccessful) {
                    val errorBody = saltResponseRetrofit.errorBody()?.string() ?: "Není k dispozici"
                    Log.e(TAG, "Chyba sítě při získávání soli: ${saltResponseRetrofit.code()}, Tělo chyby: $errorBody")
                    return@withContext Result.failure(Exception("Chyba sítě při získávání soli: ${saltResponseRetrofit.code()}"))
                }

                val saltBody: String? = saltResponseRetrofit.body()
                if (saltBody.isNullOrEmpty()) {
                    Log.e(TAG, "Prázdná odpověď serveru při získávání soli.")
                    return@withContext Result.failure(Exception("Prázdná odpověď serveru při získávání soli."))
                }

                Log.d(TAG, "Parsuji XML odpověď soli...")
                val saltData: SaltResponse = XmlUtils.parseSaltResponseXml(saltBody)

                if (saltData.status != "OK") {
                    Log.e(TAG, "Webshare API chyba při získávání soli: ${saltData.message} (${saltData.code})")
                    return@withContext Result.failure(Exception("Webshare API chyba při získávání soli: ${saltData.message} (${saltData.code})"))
                }

                val salt = saltData.salt
                if (salt.isNullOrEmpty()) {
                    Log.e(TAG, "Sůl nebyla v odpovědi API nalezena, ačkoli status byl OK.")
                    return@withContext Result.failure(Exception("Sůl nebyla v odpovědi API nalezena, ačkoli status byl OK."))
                }

                Log.d(TAG, "Sůl úspěšně získána: '$salt'.")
                Result.success(salt)
            } catch (e: Exception) {
                Log.e(TAG, "Neočekávaná chyba při získávání soli: ${e.message}", e) // Přidáno e do logu
                Result.failure(e)
            }
        }
    }

    suspend fun logout(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val token = getAuthToken()

            if (!token.isNullOrEmpty()) {
                try {
                    Log.d(TAG, "Volám API pro odhlášení s tokenem...")
                    val logoutResponseRetrofit = apiService.logout(token)

                    if (logoutResponseRetrofit.isSuccessful) {
                        val logoutBody = logoutResponseRetrofit.body()
                        if (!logoutBody.isNullOrEmpty()) {
                            val status = XmlUtils.parseLogoutResponseXml(logoutBody)
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
                    Log.e(TAG, "Neočekávaná chyba při API odhlášení: ${e.message}", e) // Přidáno e do logu
                    // Lokální smazání proběhne i tak
                }
            } else {
                Log.d(TAG, "Žádný token pro odhlášení, API logout nevolám, lokální smazání proběhne.")
            }

            clearAuthToken()
            clearCredentials()
            Result.success(Unit)
        }
    }

    // --- Implementace logiky vyhledávání souborů (pro SearchViewModel) ---
    suspend fun searchFiles(
        query: String,
        category: String?, // Ponecháno jako nullable
        sort: String?,     // Přidán parametr sort
        limit: Int?,      // Přidán parametr limit
        offset: Int?      // Přidán parametr offset
    ): Result<SearchResponse> {
        Log.d(TAG, "searchFiles() volán s query: '$query', category: '$category', sort: '$sort', limit: $limit, offset: $offset")

        return withContext(Dispatchers.IO) {
            val token = getAuthToken()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Vyhledávání selhalo, uživatel není přihlášen (token chybí).")
                return@withContext Result.failure(Exception("Uživatel není přihlášen. Prosím, nejprve se přihlaste."))
            }

            try {
                Log.d(TAG, "Volám API pro vyhledávání s query: '$query', category: '$category', sort: '$sort', limit: $limit, offset: $offset.")
                val searchResponseRetrofit: Response<String> = apiService.searchFiles(
                    token = token,
                    query = query,
                    category = category,
                    sort = sort,
                    limit = limit,
                    offset = offset
                )

                if (!searchResponseRetrofit.isSuccessful) {
                    val errorBody = searchResponseRetrofit.errorBody()?.string() ?: "Není k dispozici"
                    Log.e(TAG, "Chyba sítě při vyhledávání: ${searchResponseRetrofit.code()}, Tělo chyby: $errorBody")
                    if (searchResponseRetrofit.code() == 401) {
                        Log.e(TAG, "Chyba 401 Unauthorized při vyhledávání - mažu token.")
                        clearAuthToken()
                        return@withContext Result.failure(Exception("Token vypršel. Prosím, přihlaste se znovu."))
                    }
                    return@withContext Result.failure(Exception("Chyba sítě při vyhledávání: ${searchResponseRetrofit.code()}"))
                }

                val searchBody: String? = searchResponseRetrofit.body()
                if (searchBody.isNullOrEmpty()) {
                    Log.e(TAG, "Prázdná odpověď serveru při vyhledávání.")
                    return@withContext Result.failure(Exception("Prázdná odpověď serveru při vyhledávání."))
                }

                Log.d(TAG, "Parsuji XML odpověď vyhledávání...")
                val searchData: SearchResponse = XmlUtils.parseSearchResponseXml(searchBody)

                when (searchData.status) {
                    "OK" -> {
                        Log.d(TAG, "Vyhledávání API status OK. Nalezeno ${searchData.total} souborů celkem.")
                        Result.success(searchData)
                    }
                    "ERROR", "FATAL" -> {
                        Log.e(TAG, "Webshare API chyba při vyhledávání: ${searchData.message} (${searchData.code})")
                        if (searchData.code == 102) { // Příklad kódu pro neplatný token
                            Log.e(TAG, "API kód ${searchData.code} při vyhledávání - mažu token.")
                            clearAuthToken()
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
                Log.e(TAG, "Neočekávaná chyba při vyhledávání: ${e.message}", e) // Přidáno e do logu
                Result.failure(e)
            }
        }
    }


    suspend fun getFileLink(fileId: String, filePassword: String? = null): Result<String> {
        Log.d(TAG, "getFileLink() volán pro File ID: $fileId.")

        return withContext(Dispatchers.IO) {
            val token = getAuthToken()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Získání odkazu selhalo, uživatel není přihlášen (token chybí).")
                return@withContext Result.failure(Exception("Uživatel není přihlášen. Prosím, nejprve se přihlaste."))
            }

            if (filePassword != null) {
                Log.w(TAG, "Získání odkazu pro soubor chráněný heslem - implementace chybí.") // Změněno na warning
                return@withContext Result.failure(NotImplementedError("Získání odkazu pro soubor chráněný heslem zatím není implementováno."))
            }

            val passwordHashToSend = filePassword

            try {
                Log.d(TAG, "Volám API pro získání odkazu pro File ID: $fileId s tokenem.")
                val fileLinkResponseRetrofit: Response<String> = apiService.getFileLink(
                    authHeader = token, // Opraveno z token!!
                    fileId = fileId,
                    tokenData = token, // Opraveno z token!!
                    password = passwordHashToSend,
                    download_type = "video_stream"
                )

                if (!fileLinkResponseRetrofit.isSuccessful) {
                    val errorBody = fileLinkResponseRetrofit.errorBody()?.string() ?: "Není k dispozici"
                    Log.e(TAG, "Chyba sítě při získání odkazu: ${fileLinkResponseRetrofit.code()}, Tělo chyby: $errorBody")
                    if (fileLinkResponseRetrofit.code() == 401) {
                        Log.e(TAG, "Chyba 401 Unauthorized při získání odkazu - mažu token.")
                        clearAuthToken()
                        return@withContext Result.failure(Exception("Token vypršel. Prosím, přihlaste se znovu."))
                    }
                    return@withContext Result.failure(Exception("Chyba sítě při získání odkazu: ${fileLinkResponseRetrofit.code()}"))
                }

                val fileLinkBody: String? = fileLinkResponseRetrofit.body()
                if (fileLinkBody.isNullOrEmpty()) {
                    Log.e(TAG, "Prázdná odpověď serveru při získání odkazu.")
                    return@withContext Result.failure(Exception("Prázdná odpověď serveru při získání odkazu."))
                }

                Log.d(TAG, "Parsuji XML odpověď odkazu na soubor...")
                val fileLinkData: FileLinkResponse = XmlUtils.parseFileLinkResponseXml(fileLinkBody)

                when (fileLinkData.status) {
                    "OK" -> {
                        val url = fileLinkData.link
                        if (url.isNullOrEmpty()) {
                            Log.e(TAG, "Získání odkazu OK, ale URL nebylo nalezeno.")
                            return@withContext Result.failure(Exception("Získání odkazu OK, ale URL nebylo nalezeno v odpovědi."))
                        }
                        Log.d(TAG, "Odkaz na soubor úspěšně získán.")
                        Result.success(url)
                    }
                    "ERROR", "FATAL" -> {
                        Log.e(TAG, "Webshare API chyba při získání odkazu: ${fileLinkData.message} (${fileLinkData.code})")
                        if (fileLinkData.code == 102) { // Příklad kódu pro neplatný token
                            Log.e(TAG, "API kód ${fileLinkData.code} při získání odkazu - mažu token.")
                            clearAuthToken()
                            return@withContext Result.failure(Exception("Token vypršel nebo je neplatný. Prosím, přihlaste se znovu."))
                        }
                        Result.failure(Exception("Webshare API chyba při získání odkazu: ${fileLinkData.message} (${fileLinkData.code})"))
                    }
                    else -> {
                        Log.e(TAG, "Neznámý status odpovědi pro odkaz: ${fileLinkData.status}")
                        Result.failure(Exception("Neznámý status odpovědi pro odkaz: ${fileLinkData.status}"))
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Neočekávaná chyba při získání odkazu: ${e.message}", e) // Přidáno e do logu
                Result.failure(e)
            }
        }
    }

    // ***** PŘIDÁNA METODA PRO HISTORII *****
    /**
     * Získá historii stahování pro přihlášeného uživatele.
     * @param offset Posun v seznamu (pro stránkování).
     * @param limit Maximální počet položek k načtení.
     * @return Result obsahující HistoryResponse při úspěchu, nebo Exception při chybě.
     */
    suspend fun getHistory(offset: Int, limit: Int): Result<HistoryResponse> {
        Log.d(TAG, "getHistory() volán s offset: $offset, limit: $limit")

        return withContext(Dispatchers.IO) {
            val token = getAuthToken()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Získání historie selhalo, uživatel není přihlášen (token chybí).")
                return@withContext Result.failure(Exception("Uživatel není přihlášen."))
            }

            try {
                Log.d(TAG, "Volám API pro získání historie s offset: $offset, limit: $limit.")
                val historyResponseRetrofit: Response<String> = apiService.getHistory(
                    token = token,
                    offset = offset,
                    limit = limit
                )

                if (!historyResponseRetrofit.isSuccessful) {
                    val errorBody = historyResponseRetrofit.errorBody()?.string() ?: "Není k dispozici"
                    Log.e(TAG, "Chyba sítě při získání historie: ${historyResponseRetrofit.code()}, Tělo chyby: $errorBody")
                    if (historyResponseRetrofit.code() == 401) {
                        Log.e(TAG, "Chyba 401 Unauthorized při získání historie - mažu token.")
                        clearAuthToken()
                        return@withContext Result.failure(Exception("Token vypršel. Prosím, přihlaste se znovu."))
                    }
                    return@withContext Result.failure(Exception("Chyba sítě při získání historie: ${historyResponseRetrofit.code()}"))
                }

                val historyBody: String? = historyResponseRetrofit.body()
                if (historyBody.isNullOrEmpty()) {
                    Log.e(TAG, "Prázdná odpověď serveru při získání historie.")
                    return@withContext Result.failure(Exception("Prázdná odpověď serveru při získání historie."))
                }

                Log.d(TAG, "Parsuji XML odpověď historie...")
                // Použití vaší parsovací funkce z XmlUtils
                val historyData: HistoryResponse = XmlUtils.parseHistoryResponseXml(historyBody)

                when (historyData.status) {
                    "OK" -> {
                        Log.d(TAG, "Historie API status OK. Nalezeno ${historyData.total} položek celkem.")
                        Result.success(historyData) // Vrátí naparsovaný HistoryResponse objekt
                    }
                    "ERROR", "FATAL" -> {
                        Log.e(TAG, "Webshare API chyba při získání historie: ${historyData.message} (${historyData.code})")
                        if (historyData.code == 102 /* Kód pro neplatný token */) {
                            Log.e(TAG, "API kód ${historyData.code} při získání historie - mažu token.")
                            clearAuthToken()
                            return@withContext Result.failure(Exception("Token vypršel nebo je neplatný."))
                        }
                        Result.failure(Exception("Webshare API chyba: ${historyData.message} (${historyData.code})"))
                    }
                    else -> {
                        Log.e(TAG, "Neznámý status odpovědi historie: ${historyData.status}")
                        Result.failure(Exception("Neznámý status odpovědi historie: ${historyData.status}"))
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Neočekávaná chyba při získání historie: ${e.message}", e)
                Result.failure(e)
            }
        }
    }
    // *******************************************

}
