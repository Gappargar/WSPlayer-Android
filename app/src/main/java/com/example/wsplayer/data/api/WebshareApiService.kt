// app/src/main/java/com/example/wsplayer/data/api/WebshareApiService.kt
package com.example.wsplayer.data.api // Váš balíček - ZKONTROLUJTE

import retrofit2.Response // Import pro Retrofit Response
import retrofit2.http.Field // Import pro @Field anotaci
import retrofit2.http.FormUrlEncoded // Import pro @FormUrlEncoded anotaci
import retrofit2.http.POST // Import pro @POST anotaci
import retrofit2.http.Header // Import pro @Header anotaci

import retrofit2.Retrofit // Import pro Retrofit Builder
import retrofit2.converter.scalars.ScalarsConverterFactory // Import pro konvertor Stringů

// Import pro OkHttpClient a LoggingInterceptor (doporučeno pro ladění API volání)
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit


// TODO: Nastavte správnou základní URL vašeho API
private const val BASE_URL = "https://webshare.cz/api/"

// API rozhraní pro komunikaci s Webshare.cz
// Definujte metody pro jednotlivé API endpointy
interface WebshareApiService {

    // Metoda pro získání soli pro hašování hesla (/api/salt/)
    // Vrací XML jako String
    @FormUrlEncoded // Označuje, že data budou odeslána jako form-urlencoded
    @POST("salt/") // Specifikuje relativní URL endpointu
    suspend fun getSalt(@Field("username_or_email") username: String): Response<String> // <-- Vrací Response<String>

    // Metoda pro přihlášení uživatele (/api/login/)
    // Vrací XML s tokenem jako String
    @FormUrlEncoded
    @POST("login/")
    suspend fun login(
        @Field("username_or_email") username: String,
        @Field("password") password: String, // Očekává hašované heslo
        @Field("keep_logged_in") keepLoggedIn: Int // 1 pro zapamatování, 0 ne
    ): Response<String> // <-- Vrací Response<String>

    // Metoda pro odhlášení uživatele (/api/logout/)
    // Vrací XML (status OK/ERROR/FATAL) jako String
    @FormUrlEncoded
    @POST("logout/")
    suspend fun logout(@Field("wst") token: String): Response<String> // <-- Vrací Response<String>

    // Metoda pro vyhledávání souborů (/api/search/)
    // Vrací XML se seznamem souborů jako String
    @FormUrlEncoded
    @POST("search/")
    suspend fun searchFiles(
        @Field("wst") token: String,
        @Field("what") query: String, // <-- @Field musí odpovídat API dokumentaci (what)
        @Field("sort") sort: String? = null, // Volitelný parametr pro řazení
        @Field("limit") limit: Int? = null, // Volitelný limit pro počet položek na stránku
        @Field("offset") offset: Int? = null, // Volitelný offset pro stránkování (page * limit)
        @Field("category") category: String? = null // Volitelný parametr pro ID kategorie
        // TODO: Další parametry, pokud API podporuje a vyžaduje (např. device info)
    ): Response<String> // <-- Vrací Response<String>

    // Metoda pro získání přímého odkazu na soubor (/api/file_link/)
    // Vrací XML s přímým odkazem jako String
    @FormUrlEncoded
    @POST("file_link/")
    suspend fun getFileLink(
        @Header("Authorization") authHeader: String, // API očekává token v hlavičce "Authorization: WST <token>"
        @Field("ident") fileId: String, // Identifikátor souboru
        @Field("wst") tokenData: String, // API dokumentace ukazuje token i v datech pro file_link
        @Field("password") password: String? = null, // Hašované heslo pro soubory s heslem
        @Field("download_type") download_type: String? = "video_stream", // <-- @Field musí odpovídat API dokumentaci (download_type)
        // TODO: Volitelně přidejte parametry zařízení, pokud to API podporuje a vyžaduje
        @Field("device_uuid") device_uuid: String? = null,
        @Field("device_vendor") device_vendor: String? = null,
        @Field("device_model") device_model: String? = null,
        @Field("device_res_x") device_res_x: Int? = null,
        @Field("device_res_y") device_res_y: Int? = null,
        @Field("force_https") force_https: Int? = null
    ): Response<String> // <-- Vrací Response<String>

    // Metoda pro získání uživatelských dat (/api/user_data/)
    // Vrací XML s detaily o účtu jako String
    @FormUrlEncoded
    @POST("user_data/")
    suspend fun getUserData(
        @Header("Authorization") authHeader: String, // API očekává token v hlavičce "Authorization: WST <token>"
        @Field("wst") tokenData: String // API dokumentace ukazuje token i v datech pro user_data
        // TODO: Volitelně přidejte parametry zařízení
    ): Response<String> // <-- Vrací Response<String>


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
        // Vytvoření Logging Interceptoru pro zobrazení API požadavků/odpovědí v Logcatu
        private val loggingInterceptor = HttpLoggingInterceptor().apply {
            setLevel(HttpLoggingInterceptor.Level.BODY) // Logovat tělo požadavků/odpovědí
        }

        // Vytvoření OkHttpClientu s interceptorem
        private val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor) // Přidání logovacího interceptoru
            // Volitelně nastavit timeouty
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()


        // Vytvoření instance Retrofitu
        private val retrofit = Retrofit.Builder()
            .client(okHttpClient) // Použití vlastního OkHttpClientu
            .addConverterFactory(ScalarsConverterFactory.create()) // Použít ScalarsConverterFactory pro XML/String
            // Pokud byste parsoval(a) JSON, použijte Moshi/Gson konvertor:
            // .addConverterFactory(MoshiConverterFactory.create(moshi))
            .baseUrl(BASE_URL) // Základní URL API
            .build()

        // Veřejná instance služby Retrofit API, ke které mohou komponenty přistupovat
        val apiService: WebshareApiService by lazy {
            retrofit.create(WebshareApiService::class.java)
        }

        // Metoda pro snadné získání instance API služby
        fun create(): WebshareApiService = apiService
    }
}