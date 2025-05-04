package com.example.wsplayer.ui.auth // Váš balíček + .ui.auth - ZKONTROLUJTE

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.wsplayer.AuthTokenManager // Import AuthTokenManager
import com.example.wsplayer.data.api.WebshareApiService // Import WebshareApiService
import com.example.wsplayer.data.repository.WebshareRepository // Import WebshareRepository

// Factory pro vytváření LoginViewModelu s jeho závislostmi
// Factory přijímá Context (applicationContext) a WebshareApiService
class LoginViewModelFactory(
    private val appContext: Context, // Potřebujeme applicationContext pro AuthTokenManager
    private val apiService: WebshareApiService // Potřebujeme ApiService pro Repository
) : ViewModelProvider.Factory { // Dědíme od ViewModelProvider.Factory

    // Tato metoda je volána Android frameworkem k vytvoření instance ViewModelu
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // Zkontrolujeme, zda požadovaná třída ViewModelu je LoginViewModel
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {

            // --- Vytvoření závislostí UVNITŘ Factory ---
            // Factory si sama vytvoří AuthTokenManager, protože potřebuje Context (který dostala v konstruktoru)
            val authTokenManager = AuthTokenManager(appContext)

            // Factory si sama vytvoří Repository, protože potřebuje ApiService (kterou dostala v konstruktoru)
            // a nově vytvořený AuthTokenManager
            val repository = WebshareRepository(apiService, authTokenManager)

            // Vytvoření a vrácení instance LoginViewModelu s nově vytvořenou Repository
            @Suppress("UNCHECKED_CAST") // Potlačíme varování, protože víme, že typ T je LoginViewModel
            return LoginViewModel(repository) as T
        }
        // Pokud se požaduje jiný ViewModel než LoginViewModel, vyvoláme chybu
        throw IllegalArgumentException("Neznámá ViewModel třída: ${modelClass.name}")
    }
}