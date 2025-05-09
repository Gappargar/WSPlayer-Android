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
    @FormUrlEncoded
    @POST("salt/")
    suspend fun getSalt(@Field("username_or_email") username: String): Response<String>

    // Metoda pro přihlášení uživatele (/api/login/)
    @FormUrlEncoded
    @POST("login/")
    suspend fun login(
        @Field("username_or_email") username: String,
        @Field("password") password: String,
        @Field("keep_logged_in") keepLoggedIn: Int
    ): Response<String>

    // Metoda pro odhlášení uživatele (/api/logout/)
    @FormUrlEncoded
    @POST("logout/")
    suspend fun logout(@Field("wst") token: String): Response<String>

    // Metoda pro vyhledávání souborů (/api/search/)
    @FormUrlEncoded
    @POST("search/")
    suspend fun searchFiles(
        @Field("wst") token: String,
        @Field("what") query: String,
        @Field("sort") sort: String? = null,
        @Field("limit") limit: Int? = null,
        @Field("offset") offset: Int? = null,
        @Field("category") category: String? = null
    ): Response<String>

    // Metoda pro získání přímého odkazu na soubor (/api/file_link/)
    @FormUrlEncoded
    @POST("file_link/")
    suspend fun getFileLink(
        @Header("Authorization") authHeader: String,
        @Field("ident") fileId: String,
        @Field("wst") tokenData: String,
        @Field("password") password: String? = null,
        @Field("download_type") download_type: String? = "video_stream",
        @Field("device_uuid") device_uuid: String? = null,
        @Field("device_vendor") device_vendor: String? = null,
        @Field("device_model") device_model: String? = null,
        @Field("device_res_x") device_res_x: Int? = null,
        @Field("device_res_y") device_res_y: Int? = null,
        @Field("force_https") force_https: Int? = null
    ): Response<String>

    // Metoda pro získání uživatelských dat (/api/user_data/)
    @FormUrlEncoded
    @POST("user_data/")
    suspend fun getUserData(
        @Header("Authorization") authHeader: String,
        @Field("wst") tokenData: String
    ): Response<String>

    // ***** PŘIDÁNA METODA PRO HISTORII *****
    /**
     * Získá historii stahování uživatele.
     * @param token WST token uživatele.
     * @param offset Posun v seznamu historie (pro stránkování).
     * @param limit Maximální počet položek k vrácení.
     * @return XML odpověď jako String.
     */
    @FormUrlEncoded
    @POST("history/")
    suspend fun getHistory(
        @Field("wst") token: String,
        @Field("offset") offset: Int,
        @Field("limit") limit: Int
    ): Response<String> // Vrací XML jako String


    // --- Objekt pro vytvoření instance Retrofitu a služby ---
    companion object {
        private val loggingInterceptor = HttpLoggingInterceptor().apply {
            setLevel(HttpLoggingInterceptor.Level.BODY)
        }

        private val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        private val retrofit = Retrofit.Builder()
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .baseUrl(BASE_URL)
            .build()

        val apiService: WebshareApiService by lazy {
            retrofit.create(WebshareApiService::class.java)
        }

        fun create(): WebshareApiService = apiService
    }
}
