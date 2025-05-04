package com.example.wsplayer // Váš hlavní balíček

import android.content.Context // Import pro Context
import android.content.SharedPreferences // Import pro SharedPreferences
import android.util.Log // Import pro Logování

// Volitelně pro EncryptedSharedPreferences (pokud se rozhodnete použít a přidáte závislost)
// import androidx.security.crypto.EncryptedSharedPreferences
// import androidx.security.crypto.MasterKeys


// Třída pro správu (ukládání/načítání) autentizačního tokenu a přihlašovacích údajů pomocí SharedPreferences
// Přijímá Context (nejlépe applicationContext) v konstruktoru pro přístup k SharedPreferences
class AuthTokenManager(context: Context) {

    // Název SharedPreferences souboru
    private val PREFS_NAME = "WebsharePrefs"
    // Klíč pro uložení tokenu
    private val TOKEN_KEY = "auth_token"
    // Klíče pro uložení přihlašovacích údajů
    private val USERNAME_KEY = "saved_username"
    private val PASSWORD_KEY = "saved_password" // **POZOR: Ukládání hesla do SharedPreferences není zcela bezpečné!**


    // Instance SharedPreferences
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Správa autentizačního tokenu ---

    // Uloží autentizační token (používá apply - asynchronní)
    fun saveToken(token: String) {
        sharedPreferences.edit().putString(TOKEN_KEY, token).apply()
        Log.d("AuthTokenManager", "Token saved") // Použití Log.d pro logování
    }

    // Načte autentizační token
    fun getAuthToken(): String? {
        val token = sharedPreferences.getString(TOKEN_KEY, null)
        Log.d("AuthTokenManager", "Token retrieved: ${token?.length ?: 0} characters") // Loguje délku, ne celý token
        return token
    }

    // Smaže autentizační token (používá commit - synchronní)
    fun clearToken() {
        sharedPreferences.edit().remove(TOKEN_KEY).commit() // <-- Změněno z .apply() na .commit() pro okamžité smazání
        Log.d("AuthTokenManager", "Token cleared")
    }

    // --- Správa přihlašovacích údajů (Credentials) ---

    // **NOVÁ METODA: Uloží uživatelské jméno a heslo do SharedPreferences** (používá apply - asynchronní)
    // **POZOR: Zvažte použití EncryptedSharedPreferences nebo jiné formy šifrování pro vyšší bezpečnost!**
    fun saveCredentials(username: String, password: String) {
        sharedPreferences.edit()
            .putString(USERNAME_KEY, username)
            .putString(PASSWORD_KEY, password) // **Ukládání hesla - POUŽÍVEJTE S ROZVAHOU!**
            .apply() // <-- Používáme .apply() pro ukládání (nemusí být okamžité)
        Log.d("AuthTokenManager", "Credentials saved")
    }

    // **NOVÁ METODA: Načte uložené uživatelské jméno a heslo**
    // Vrací Pair<username, password> nebo null, pokud nejsou uloženy nebo jsou nekompletní
    fun loadCredentials(): Pair<String, String>? {
        // Při načítání použijeme get, což je synchronní, přečte aktuálně uložené hodnoty
        val username = sharedPreferences.getString(USERNAME_KEY, null)
        val password = sharedPreferences.getString(PASSWORD_KEY, null) // **Načítání hesla**

        // Kontrola, zda jsou oba údaje přítomny a neprázdné
        return if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
            Log.d("AuthTokenManager", "Credentials loaded")
            Pair(username, password)
        } else {
            Log.d("AuthTokenManager", "No credentials found or incomplete")
            null // V případě chybějících nebo nekompletních údajů vrátíme null
        }
    }

    // **NOVÁ METODA: Smaže uložené přihlašovací údaje** (používá commit - synchronní)
    fun clearCredentials() {
        sharedPreferences.edit()
            .remove(USERNAME_KEY)
            .remove(PASSWORD_KEY)
            .apply() // <-- Změněno z .commit() zpět na .apply()
        Log.d("AuthTokenManager", "Credentials cleared")
    }

    // Volitelně: Zkontrolovat, zda existuje token
    fun hasToken(): Boolean {
        return getAuthToken() != null
    }

    // Volitelně: Zkontrolovat, zda existují uložené credentials
    fun hasCredentials(): Boolean {
        return loadCredentials() != null
    }
}