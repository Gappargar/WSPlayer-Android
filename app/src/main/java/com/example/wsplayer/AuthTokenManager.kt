// app/src/main/java/com/example/wsplayer/AuthTokenManager.kt
package com.example.wsplayer // Váš balíček - ZKONTROLUJTE

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

// Třísa pro správu autentizačního tokenu a přihlašovacích údajů uživatele v SharedPreferences
class AuthTokenManager(context: Context) {

    // SharedPreferences pro ukládání dat
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE) // MODE_PRIVATE pro bezpečnost

    // Klíče pro ukládání dat v SharedPreferences
    private val PREF_AUTH_TOKEN = "auth_token"
    private val PREF_USERNAME = "username"
    private val PREF_PASSWORD = "password" // Ukládáme plain heslo, zvažte šifrování pro produkční app

    private val TAG = "AuthTokenManager" // Logovací tag

    // --- Správa autentizačního tokenu ---

    // Uloží autentizační token
    fun saveToken(token: String) {
        Log.d(TAG, "Saving token")
        prefs.edit().putString(PREF_AUTH_TOKEN, token).apply()
    }

    // Načte autentizační token
    fun getAuthToken(): String? {
        val token = prefs.getString(PREF_AUTH_TOKEN, null)
        Log.d(TAG, "Token retrieved: ${if (token != null) "${token.length} characters" else "null"}")
        return token
    }

    // Smaže autentizační token
    fun clearToken() {
        Log.d(TAG, "Clearing token")
        prefs.edit().remove(PREF_AUTH_TOKEN).apply()
    }

    // --- Správa přihlašovacích údajů (Username/Password) ---

    // Uloží uživatelské jméno a heslo
    fun saveCredentials(username: String, passwordHash: String) { // Očekává HASH hesla
        Log.d(TAG, "Saving credentials")
        prefs.edit()
            .putString(PREF_USERNAME, username)
            .putString(PREF_PASSWORD, passwordHash) // Ukládá hash
            .apply()
    }

    // Načte uložené uživatelské jméno a heslo
    // Vrací Pair<username, passwordHash> nebo null, pokud nejsou údaje kompletní
    fun loadCredentials(): Pair<String, String>? {
        val username = prefs.getString(PREF_USERNAME, null)
        val passwordHash = prefs.getString(PREF_PASSWORD, null) // Načítá hash
        Log.d(TAG, "Credentials loaded: username=${username != null}, passwordHash=${passwordHash != null}")

        return if (username != null && passwordHash != null) {
            Pair(username, passwordHash)
        } else {
            null // Credentials nejsou kompletní nebo neexistují
        }
    }

    // Smaže uložené uživatelské jméno a heslo
    fun clearCredentials() {
        Log.d(TAG, "Clearing credentials")
        prefs.edit()
            .remove(PREF_USERNAME)
            .remove(PREF_PASSWORD)
            .apply()
    }

    // Pomocná metoda pro smazání VŠECH uložených dat (token i credentials)
    fun clearAll() {
        Log.d(TAG, "Clearing all authentication data")
        prefs.edit().clear().apply()
    }
}