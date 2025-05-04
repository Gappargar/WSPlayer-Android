// app/src/main/java/com/example/wsplayer/AuthTokenManager.kt
package com.example.wsplayer // Váš balíček - ZKONTROLUJTE

import android.content.Context
import android.content.SharedPreferences
import android.util.Log // Pro logování

// Třída pro správu autentizačního tokenu a přihlašovacích údajů uživatele v SharedPreferences
class AuthTokenManager(context: Context) {

    // SharedPreferences pro ukládání dat
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    // Klíče pro ukládání dat v SharedPreferences
    private val PREF_AUTH_TOKEN = "auth_token"
    private val PREF_USERNAME = "username"
    private val PREF_PASSWORD = "password" // Ukládáme plain heslo, zvažte šifrování pro produkční app

    // --- Správa autentizačního tokenu ---

    // Uloží autentizační token
    fun saveToken(token: String) {
        Log.d("AuthTokenManager", "Saving token") // Log
        prefs.edit().putString(PREF_AUTH_TOKEN, token).apply()
    }

    // Načte autentizační token
    fun getAuthToken(): String? {
        val token = prefs.getString(PREF_AUTH_TOKEN, null)
        Log.d("AuthTokenManager", "Token retrieved: ${if (token != null) "${token.length} characters" else "null"}") // Log
        return token
    }

    // **Smaže autentizační token** - Toto je metoda, kterou volá Repository.clearAuthToken()
    fun clearToken() {
        Log.d("AuthTokenManager", "Clearing token") // Log
        prefs.edit().remove(PREF_AUTH_TOKEN).apply()
    }

    // --- Správa přihlašovacích údajů (Username/Password) ---

    // Uloží uživatelské jméno a heslo
    fun saveCredentials(username: String, password: String) {
        Log.d("AuthTokenManager", "Saving credentials") // Log
        prefs.edit()
            .putString(PREF_USERNAME, username)
            .putString(PREF_PASSWORD, password)
            .apply()
    }

    // Načte uložené uživatelské jméno a heslo
    // Vrací Pair<username, password> nebo null, pokud nejsou údaje kompletní
    fun loadCredentials(): Pair<String, String>? {
        val username = prefs.getString(PREF_USERNAME, null)
        val password = prefs.getString(PREF_PASSWORD, null)
        Log.d("AuthTokenManager", "Credentials loaded: username=${username != null}, password=${password != null}") // Log

        return if (username != null && password != null) {
            Pair(username, password)
        } else {
            null // Credentials nejsou kompletní nebo neexistují
        }
    }

    // **Smaže uložené uživatelské jméno a heslo**
    fun clearCredentials() {
        Log.d("AuthTokenManager", "Clearing credentials") // Log
        prefs.edit()
            .remove(PREF_USERNAME)
            .remove(PREF_PASSWORD)
            .apply()
    }

    // Pomocná metoda pro smazání VŠECH uložených dat (token i credentials)
    fun clearAll() {
        Log.d("AuthTokenManager", "Clearing all authentication data") // Log
        prefs.edit().clear().apply()
    }
}