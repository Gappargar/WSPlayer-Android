package com.example.wsplayer // Váš hlavní balíček

import android.content.Context // Import pro Context
import android.content.SharedPreferences // Import pro SharedPreferences
// Volitelně pro EncryptedSharedPreferences (vyžaduje závislost)
// import androidx.security.crypto.EncryptedSharedPreferences
// import androidx.security.crypto.MasterKeys


// Třída pro správu (ukládání/načítání) autentizačního tokenu pomocí SharedPreferences
class AuthTokenManager(private val context: Context) {

    // Název souboru SharedPreferences
    private val PREFS_NAME = "WebsharePlayerPrefs"
    // Klíč pro uložení tokenu
    private val AUTH_TOKEN_KEY = "wst_token"

    // Získání instance SharedPreferences (nebo EncryptedSharedPreferences)
    private fun getSharedPreferences(): SharedPreferences {
        // TODO: Pro lepší bezpečnost zvažte EncryptedSharedPreferences
        // Pokud používáte EncryptedSharedPreferences, potřebujete závislost
        // implementation("androidx.security:security-crypto:1.1.0-alpha06")
        // a kód pro vytvoření hlavního klíče:
        // val masterKeyAlias = MasterKeys.get                 (MasterKeys.AES256_GCM_SPEC)
        // return EncryptedSharedPreferences.create(
        //    PREFS_NAME,
        //    masterKeyAlias,
        //    context,
        //    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        //    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        // )

        // Prozatím použijeme standardní SharedPreferences
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Uložení tokenu do SharedPreferences
    fun saveToken(token: String) {
        val editor = getSharedPreferences().edit()
        editor.putString(AUTH_TOKEN_KEY, token)
        editor.apply() // Uložení změn asynchronně
        println("AuthTokenManager: Token uložen do SharedPreferences.") // Logovací zpráva
    }

    // Načtení tokenu ze SharedPreferences
    fun getToken(): String? {
        val token = getSharedPreferences().getString(AUTH_TOKEN_KEY, null) // null je defaultní hodnota, pokud klíč neexistuje
        println("AuthTokenManager: Token načten ze SharedPreferences: ${token?.take(5)}...") // Logovací zpráva (z logu neposílat celý token)
        return token
    }

    // Smazání tokenu ze SharedPreferences (při odhlášení)
    fun clearToken() {
        val editor = getSharedPreferences().edit()
        editor.remove(AUTH_TOKEN_KEY)
        editor.apply() // Uložení změn
        println("AuthTokenManager: Token smazán ze SharedPreferences.") // Logovací zpráva
    }

    // Volitelně: Zkontrolovat, zda existuje token
    fun hasToken(): Boolean {
        return getToken() != null
    }
}