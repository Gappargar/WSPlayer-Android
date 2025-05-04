// app/src/main/java/com/example/wsplayer/data/api/WebshareApiService.kt
package com.example.wsplayer.data.api // Váš balíček - ZKONTROLUJTE

import retrofit2.Response // Import pro Retrofit Response
import retrofit2.http.Field // Import pro @Field anotaci
import retrofit2.http.FormUrlEncoded // Import pro @FormUrlEncoded anotaci
import retrofit2.http.POST // Import pro @POST anotaci
import retrofit2.http.Header // Import pro @Header anotaci

import retrofit2.Retrofit // Import pro Retrofit Builder
import retrofit2.converter.scalars.ScalarsConverterFactory // Import pro konvertor Stringů

// TODO: Nastavte správnou základní URL vašeho API
private const val BASE_URL = "https://webshare.cz/api/"

// API rozhraní pro komunikaci s Webshare.cz
// Definujte metody pro jednotlivé API endpointy
interface WebshareApiService {

    // Metoda pro získání soli pro hašování hesla
    // Vrací XML odpověď jako String
    @FormUrlEncoded // Označuje, že data budou odeslána jako form-urlencoded
    @POST("salt/") // Specifikuje relativní URL endpointu
    suspend fun getSalt(@Field("username_or_email") username: String): Response<String> // <-- Vrací Response s tělem typu String

    // Metoda pro přihlášení uživatele
    // Vrací XML odpověď s tokenem jako String
    @FormUrlEncoded
    @POST("login/")
    suspend fun login(
        @Field("username_or_email") username: String,
        @Field("password") passwordHash: String, // Očekává hašované heslo
        @Field("keep_logged_in") keepLoggedIn: Int // 1 pro zapamatování, 0 ne
    ): Response<String> // <-- Vrací Response s tělem typu String

    // Metoda pro odhlášení uživatele
    // Vrací XML odpověď (status OK/ERROR/FATAL) jako String
    @FormUrlEncoded
    @POST("logout/")
    suspend fun logout(@Field("wst") token: String): Response<String> // <-- Vrací Response s tělem typu String

    // Metoda pro vyhledávání souborů
    // Vrací XML odpověď se seznamem souborů jako String
    @FormUrlEncoded
    @POST("search/")
    suspend fun searchFiles(
        @Field("wst") token: String,
        @Field("query") query: String,
        @Field("sort") sort: String? = null, // Volitelný parametr pro řazení
        @Field("limit") limit: Int? = null, // Volitelný limit pro počet položek na stránku
        @Field("offset") offset: Int? = null, // Volitelný offset pro stránkování (page * limit)
        @Field("category") category: String? = null // Volitelný parametr pro ID kategorie
    ): Response<String> // <-- Vrací Response s tělem typu String

    // Metoda pro získání přímého odkazu na soubor
    // Vrací XML odpověď s přímým odkazem jako String
    @FormUrlEncoded
    @POST("file_link/")
    suspend fun getFileLink(
        @Header("Authorization") wstTokenHeader: String, // API může očekávat token v hlavičce
        @Field("ident") fileId: String,
        @Field("wst") wstTokenData: String, // API dokumentace ukazuje token i v datech pro file_link
        @Field("password") passwordHash: String? = null, // Hašované heslo pro soubory s heslem
        @Field("download_type") downloadType: String? = "video_stream", // Typ odkazu (stream, download)
        // TODO: Volitelně přidejte parametry zařízení, pokud to API podporuje a vyžaduje
        @Field("device_uuid") deviceUuid: String? = null,
        @Field("device_vendor") deviceVendor: String? = null,
        @Field("device_model") deviceModel: String? = null,
        @Field("device_res_x") deviceResX: Int? = null,
        @Field("device_res_y") deviceResY: Int? = null
    ): Response<String> // <-- Vrací Response s tělem typu String

    // Metoda pro získání uživatelských dat
    // Vrací XML odpověď s detaily o účtu jako String
    @FormUrlEncoded
    @POST("user_data/")
    suspend fun getUserData(
        @Header("Authorization") wstTokenHeader: String, // API může očekávat token v hlavičce
        @Field("wst") wstTokenData: String // API dokumentace ukazuje token i v datech pro user_data
    ): Response<String> // <-- Vrací Response s tělem typu String


    // TODO: Metoda pro získání soli pro heslo k souboru (/api/file_password_salt/)
    /*
    @FormUrlEncoded
    @POST("file_password_salt/")
    suspend fun getFilePasswordSalt(
        @Field("ident") fileId: String
        // TODO: Ostatní parametry dle API
    ): Response<String> // <-- Vrací Response<String>
    */


    // --- Objekt pro vytvoření instance Retrofitu a služby ---
    // Tento companion object vytvoří instanci Retrofitu a WebshareApiService
    companion object {
        // Použití ScalarsConverterFactory pro převod String <-> Body
        private val retrofit = Retrofit.Builder()
            .addConverterFactory(ScalarsConverterFactory.create()) // Konvertor pro String (XML)
            .baseUrl(BASE_URL) // Základní URL API
            .build()

        // Veřejná instance služby Retrofit API (lazy inicializace pro efektivitu)
        val apiService: WebshareApiService by lazy {
            retrofit.create(WebshareApiService::class.java)
        }

        // Metoda pro snadné získání instance API služby
        fun create(): WebshareApiService = apiService
    }
}