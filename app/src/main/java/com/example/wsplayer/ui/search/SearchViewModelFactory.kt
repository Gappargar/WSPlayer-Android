package com.example.wsplayer.ui.search // Váš balíček

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.wsplayer.data.repository.WebshareRepository
import com.example.wsplayer.AuthTokenManager // Import AuthTokenManager
import com.example.wsplayer.data.api.WebshareApiService // Import API Service
import android.content.Context // Import Context


// Factory pro vytvoření SearchViewModelu s WebshareRepository
class SearchViewModelFactory(
    // Změna: Factory bude přijímat Context a ApiService
    private val context: Context,
    private val apiService: WebshareApiService
) : ViewModelProvider.Factory {

    // **Přidejte konstruktor, který přijme applicationContext**
    constructor(applicationContext: Context) : this(applicationContext, WebshareApiService.create()) // Vytvoří ApiService zde


    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // Vytvoříme AuthTokenManager pomocí Contextu, který Factory dostala
            val authTokenManager = AuthTokenManager(context)

            // Vytvoříme Repository pomocí ApiService a AuthTokenManageru
            val repository = WebshareRepository(apiService, authTokenManager)

            // Vytvoříme ViewModel a předáme mu Repository
            @Suppress("UNCHECKED_CAST")
            return SearchViewModel(repository) as T
        }
        throw IllegalArgumentException("Neznámá ViewModel třída")
    }
}