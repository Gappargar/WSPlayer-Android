// app/src/main/java/com/example/wsplayer/ui/search/SearchViewModelFactory.kt
package com.example.wsplayer.ui.search // Váš balíček pro SearchViewModelFactory - ZKONTROLUJTE

import android.content.Context // Potřebujeme Context pro Factory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

import com.example.wsplayer.AuthTokenManager // Import AuthTokenManager (potřeba pro Factory k vytvoření Repository)
import com.example.wsplayer.data.api.WebshareApiService // Import WebshareApiService (potřeba pro Factory k vytvoření Repository)
import com.example.wsplayer.data.repository.WebshareRepository // Import WebshareRepository (závislost SearchViewModelu)
import com.example.wsplayer.ui.search.SearchViewModel // Import SearchViewModel


// Factory pro vytváření SearchViewModelu s jeho závislostmi
// Tato Factory přijímá applicationContext a WebshareApiService v konstruktoru,
// aby si uvnitř metody create mohla sama vytvořit AuthTokenManager a WebshareRepository.
class SearchViewModelFactory(
    private val appContext: Context, // Potřebujeme applicationContext pro AuthTokenManager
    private val apiService: WebshareApiService // Potřebujeme ApiService pro Repository
) : ViewModelProvider.Factory { // Factory musí dědit od ViewModelProvider.Factory

    // Tato metoda je volána Android frameworkem, když je potřeba vytvořit instanci ViewModelu
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // Zkontrolujeme, zda požadovaná třída ViewModelu je SearchViewModel
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {

            // --- Vytvoření závislostí UVNITŘ Factory ---
            // Factory si sama vytvoří AuthTokenManager, protože potřebuje Context (který dostala v konstruktoru)
            val authTokenManager = AuthTokenManager(appContext)

            // Factory si sama vytvoří Repository, protože potřebuje ApiService (kterou dostala v konstruktoru)
            // a nově vytvořený AuthTokenManager
            val repository = WebshareRepository(apiService, authTokenManager)

            // **Vytvoření a vrácení instance SearchViewModelu s hotovou Repository**
            @Suppress("UNCHECKED_CAST") // Potlačíme varování, protože víme, že typ T je SearchViewModel
            return SearchViewModel(repository) as T
        }
        // Pokud je požadována jiná třída ViewModelu, vyvoláme chybu
        throw IllegalArgumentException("Neznámá ViewModel třída: ${modelClass.name}")
    }
}