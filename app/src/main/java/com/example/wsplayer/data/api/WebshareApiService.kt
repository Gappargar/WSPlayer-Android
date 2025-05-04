package com.example.wsplayer.data.api // Váš balíček + .data.api

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Header
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

// Importy datových tříd, které API Service používá (pro signatury metod)
import com.example.wsplayer.data.models.SaltResponse
import com.example.wsplayer.data.models.LoginResponse
import com.example.wsplayer.data.models.SearchResponse
import com.example.wsplayer.data.models.FileLinkResponse
import com.example.wsplayer.data.models.UserDataResponse


// Retrofit rozhraní definující API endpointy Webshare.cz
interface WebshareApiService {

    @FormUrlEncoded
    @POST("api/salt/")
    suspend fun getSalt(@Field("username_or_email") usernameOrEmail: String): Response<String>

    @FormUrlEncoded
    @POST("api/login/")
    suspend fun login(
        @Field("username_or_email") usernameOrEmail: String,
        @Field("password") passwordHash: String,
        @Field("keep_logged_in") keepLoggedIn: Int
    ): Response<String>

    @FormUrlEncoded
    @POST("api/search/")
    suspend fun searchFiles(
        @Header("X-WST") wstToken: String,
        @Field("what") what: String,
        @Field("sort") sort: String? = null,
        @Field("limit") limit: Int? = null,
        @Field("offset") offset: Int? = null,
        @Field("category") category: String? = null
    ): Response<String>


    // Metoda pro získání přímého odkazu na soubor pro přehrávání
    // Vyžaduje WST token v hlavičce A jako data parametr (podle PC programu)
    @FormUrlEncoded
    @POST("api/file_link/")
    suspend fun getFileLink(
        @Header("X-WST") wstTokenHeader: String, // <- TENTO PARAMETR JE ZDE (v hlavičce)
        @Field("ident") fileId: String,
        @Field("wst") wstTokenData: String, // <- A TENTO PARAMETR JE ZDE (jako data parametr)
        @Field("password") passwordHash: String? = null,
        @Field("download_type") downloadType: String = "video_stream",
        // ... (ostatní volitelné parametry) ...
        @Field("force_https") forceHttps: Int = 1 // Vynutit HTTPS (default 1)
    ): Response<String>

    // Metoda pro odhlášení uživatele
    @POST("api/logout/") // Endpoint pro odhlášení
    suspend fun logout(@Header("X-WST") wstToken: String): Response<String> // Očekáváme odpověď jako String (XML)

    // **NOVÁ Metoda pro získání uživatelských dat**
    // Dokumentace: POST /api/user_data/, **vyžaduje WST token v těle (wst) a hlavičce (X-WST)**
    @FormUrlEncoded // **Přidána anotace**, protože teď posíláme data parametry v těle
    @POST("api/user_data/") // Endpoint pro uživatelská data
    suspend fun getUserData(
        @Header("X-WST") wstTokenHeader: String, // Posílá token v hlavičce (doporučeno)
        @Field("wst") wstTokenData: String // **Přidán parametr**, posílá token v těle
    ): Response<String> // Očekáváme odpověď jako String (XML)


    // TODO: Přidat metodu pro získání soli pro heslo k souboru
    /*
    @FormUrlEncoded
    @POST("api/file_password_salt/")
    suspend fun getFilePasswordSalt(
         @Header("X-WST") wstToken: String,
         @Field("ident") fileId: String
    ): Response<String>
    */


    // --- Companion Object pro vytvoření instance Retrofit Service ---
    companion object {
        private const val BASE_URL = "https://webshare.cz/"

        fun create(): WebshareApiService {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build()
            return retrofit.create(WebshareApiService::class.java)
        }
    }
}