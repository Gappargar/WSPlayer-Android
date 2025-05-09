package com.example.wsplayer.ui.search // Váš balíček pro SearchViewModel - ZKONTROLUJTE

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope // Pro práci s coroutines ve ViewModelu

// **Import Repository**
import com.example.wsplayer.data.repository.WebshareRepository // ZKONTROLUJTE CESTU

// **Importy pro datové třídy a sealed classy pro stavy z vašeho data.models balíčku**
// ZKONTROLUJTE, že cesta odpovídá vašemu umístění DataModels.kt
import com.example.wsplayer.data.models.* // <-- Toto by mělo importovat FileModel, SearchState, FileLinkState, HistoryItem, HistoryResponse atd.


import kotlinx.coroutines.Dispatchers // Pro přepnutí kontextu (background vlákna)
import kotlinx.coroutines.launch // Pro spouštění coroutines
import kotlinx.coroutines.withContext // Pro přepnutí kontextu uvnitř coroutine

import android.util.Log // Logování

// ***** PŘIDÁN NOVÝ STAV PRO HISTORII *****
/**
 * Sealed class reprezentující stavy načítání historie.
 */
sealed class HistoryState {
    object Idle : HistoryState()
    object Loading : HistoryState()
    data class Success(val items: List<HistoryItem>) : HistoryState() // Přenáší seznam HistoryItem
    data class Error(val message: String) : HistoryState()
}
// *****************************************


// ViewModel pro obrazovku vyhledávání.
// Spravuje stavy UI, logiku vyhledávání a získávání odkazů na soubory.
// Přidána správa historie.
class SearchViewModel(private val repository: WebshareRepository) : ViewModel() {

    private val TAG = "SearchViewModel" // Logovací tag

    // --- Stavy pro UI (LiveData) ---

    // Stav vyhledávání
    private val _searchState = MutableLiveData<SearchState>(SearchState.Idle)
    val searchState: LiveData<SearchState> = _searchState

    // Stav získávání odkazu
    private val _fileLinkState = MutableLiveData<FileLinkState>(FileLinkState.Idle)
    val fileLinkState: LiveData<FileLinkState> = _fileLinkState

    // ***** PŘIDÁNO LiveData PRO HISTORII *****
    private val _historyState = MutableLiveData<HistoryState>(HistoryState.Idle)
    val historyState: LiveData<HistoryState> = _historyState
    // ****************************************

    // Indikátor načítání (pro vyhledávání) - může být potřeba i pro historii
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Seznam nalezených souborů (pro vyhledávání)
    private val _searchResults = MutableLiveData<List<FileModel>>(emptyList())
    val searchResults: LiveData<List<FileModel>> = _searchResults

    // Celkový počet výsledků vyhledávání
    private val _totalResults = MutableLiveData<Int>(0)
    val totalResults: LiveData<Int> = _totalResults

    // Stav přihlášení uživatele
    private val _isUserLoggedIn = MutableLiveData<Boolean>()
    val isUserLoggedIn: LiveData<Boolean> = _isUserLoggedIn


    // --- Proměnné pro správu stavu ---
    private var currentSearchQuery: String = ""
    private var currentSearchCategory: String? = null
    private var currentSortOrder: String? = null
    var currentPage: Int = 0 // Změněno na public pro přístup z fragmentu (pro Success state)
        private set // Setter zůstává privátní
    private val resultsPerPage = 50


    init {
        Log.d(TAG, "Init blok spuštěn. PID: ${android.os.Process.myPid()}")
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Provádím kontrolu tokenu v init bloku.")
            val token = repository.getAuthToken()
            val isLoggedIn = (token != null && token.isNotEmpty())
            _isUserLoggedIn.postValue(isLoggedIn)
            Log.d(TAG, "Kontrola tokenu dokončena. isUserLoggedIn: $isLoggedIn")
            // Pokud je přihlášen, můžeme rovnou načíst historii
            // if (isLoggedIn) {
            //     fetchHistory() // Volitelně načíst historii hned po startu
            // }
        }
    }

    // --- Metody pro logiku obrazovky ---

    fun search(query: String, category: String?, sort: String? = null) {
        Log.d(TAG, "search() volán s dotazem: '$query', kategorií: '$category', řazením: '$sort', limit: $resultsPerPage")
        if (query.isEmpty()) {
            _searchState.postValue(SearchState.Idle)
            _searchResults.postValue(emptyList())
            _totalResults.postValue(0)
            Log.d(TAG, "Prázdný dotaz, vracím se do Idle.")
            return
        }

        if (isUserLoggedIn.value != true) {
            Log.e(TAG, "Pokus o vyhledávání bez platného tokenu. Nastavuji Error stav.")
            _searchState.postValue(SearchState.Error("Pro vyhledávání je vyžadováno přihlášení."))
            _searchResults.postValue(emptyList())
            _totalResults.postValue(0)
            return
        }

        if (_isLoading.value == true && _searchState.value is SearchState.Loading) { // Kontrola i stavu
            Log.d(TAG, "Již probíhá načítání (první stránka), přeskakuji nové vyhledávání.")
            return
        }

        currentSearchQuery = query
        currentSearchCategory = category
        currentSortOrder = sort
        currentPage = 0 // Při novém vyhledávání vždy začínáme od první stránky
        _searchResults.postValue(emptyList()) // Vyčistit předchozí výsledky
        _totalResults.postValue(0) // Resetovat celkový počet

        _isLoading.postValue(true)
        _searchState.postValue(SearchState.Loading)

        viewModelScope.launch(Dispatchers.IO) {
            val calculatedOffset = currentPage * resultsPerPage
            Log.d(TAG, "Spouštím API volání přes repository pro vyhledávání - query '$currentSearchQuery', category '$currentSearchCategory', sort '$currentSortOrder', limit '$resultsPerPage', offset '$calculatedOffset'.")

            val result = repository.searchFiles(
                query = currentSearchQuery,
                category = currentSearchCategory,
                sort = currentSortOrder,
                limit = resultsPerPage,
                offset = calculatedOffset
            )

            _isLoading.postValue(false) // Načítání dokončeno

            if (result.isSuccess) {
                val searchResponse = result.getOrThrow()
                val files = searchResponse.files
                val total = searchResponse.total

                _totalResults.postValue(total)

                if (files != null && files.isNotEmpty()) {
                    Log.d(TAG, "API volání vyhledávání úspěšné. Nalezeno ${files.size} na stránce, celkem $total.")
                    _searchResults.postValue(files)
                    _searchState.postValue(SearchState.Success(files, total))
                } else {
                    Log.d(TAG, "API volání vyhledávání úspěšné, ale vráceny 0 souborů.")
                    _searchState.postValue(SearchState.EmptyResults)
                    _searchResults.postValue(emptyList())
                    _totalResults.postValue(0)
                }
            } else {
                Log.e(TAG, "API volání vyhledávání selhalo: ${result.exceptionOrNull()?.message}")
                val errorMessage = result.exceptionOrNull()?.message ?: "Neznámá chyba"
                _searchState.postValue(SearchState.Error(errorMessage))
                _searchResults.postValue(emptyList())
                _totalResults.postValue(0)
            }
        }
    }

    fun loadNextPage() {
        Log.d(TAG, "loadNextPage() volán.")
        if (isUserLoggedIn.value != true || currentSearchQuery.isEmpty() || _isLoading.value == true) {
            Log.d(TAG, "Přeskakuji loadNextPage - není přihlášen, není dotaz nebo již načítám.")
            return
        }

        val currentResultsCount = _searchResults.value?.size ?: 0
        val total = _totalResults.value ?: 0

        if (currentResultsCount >= total && total > 0) {
            Log.d(TAG, "Žádné další stránky k načtení (current=$currentResultsCount, total=$total).")
            return
        }

        currentPage++ // Zvýšit číslo stránky pro výpočet dalšího offsetu
        _isLoading.postValue(true)
        _searchState.postValue(SearchState.LoadingMore)

        viewModelScope.launch(Dispatchers.IO) {
            val calculatedOffset = currentPage * resultsPerPage
            Log.d(TAG, "Spouštím API volání přes repository (další stránka) - query '$currentSearchQuery', sort '$currentSortOrder', limit '$resultsPerPage', offset '$calculatedOffset'.")

            val result = repository.searchFiles(
                query = currentSearchQuery,
                category = currentSearchCategory,
                sort = currentSortOrder,
                limit = resultsPerPage,
                offset = calculatedOffset
            )

            _isLoading.postValue(false) // Načítání dokončeno

            if (result.isSuccess) {
                val searchResponse = result.getOrThrow()
                val newFiles = searchResponse.files

                if (newFiles != null && newFiles.isNotEmpty()) {
                    Log.d(TAG, "API volání načítání další stránky úspěšné. Přidávám ${newFiles.size} souborů.")
                    val currentList = _searchResults.value ?: emptyList()
                    val updatedList = currentList + newFiles
                    _searchResults.postValue(updatedList)
                    // Celkový počet se nemění, použijeme uložený
                    _searchState.postValue(SearchState.Success(updatedList, _totalResults.value ?: updatedList.size))
                } else {
                    Log.d(TAG, "API volání načítání další stránky vráceny 0 nových souborů.")
                    _searchState.postValue(SearchState.Success(_searchResults.value ?: emptyList(), _totalResults.value ?: 0))
                }
            } else {
                Log.e(TAG, "API volání načítání další stránky selhalo: ${result.exceptionOrNull()?.message}")
                val errorMessage = result.exceptionOrNull()?.message ?: "Neznámá chyba při načítání dalších výsledků"
                _searchState.postValue(SearchState.Error(errorMessage))
                // Vrátit currentPage zpět, pokud načítání selhalo? Záleží na UX.
                // currentPage--
            }
        }
    }

    // ***** PŘIDÁNA METODA PRO NAČTENÍ HISTORIE *****
    /**
     * Načte historii stahování uživatele.
     * @param limit Maximální počet položek k načtení.
     */
    fun fetchHistory(limit: Int = 20) { // Výchozí limit 20
        Log.d(TAG, "fetchHistory() called with limit: $limit")
        if (isUserLoggedIn.value != true) {
            Log.e(TAG, "Cannot fetch history, user not logged in.")
            _historyState.postValue(HistoryState.Error("Pro zobrazení historie je vyžadováno přihlášení."))
            return
        }
        // Zkontrolovat, zda už nenačítáme historii
        if (_historyState.value is HistoryState.Loading) {
            Log.d(TAG, "History is already loading.")
            return
        }

        _historyState.postValue(HistoryState.Loading) // Nastavit stav načítání

        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.getHistory(offset = 0, limit = limit) // Začínáme od offsetu 0

            if (result.isSuccess) {
                val historyResponse = result.getOrThrow()
                if (historyResponse.historyItems.isNotEmpty()) {
                    Log.d(TAG, "History fetched successfully. Items: ${historyResponse.historyItems.size}")
                    _historyState.postValue(HistoryState.Success(historyResponse.historyItems))
                } else {
                    Log.d(TAG, "History fetched, but it's empty.")
                    _historyState.postValue(HistoryState.Success(emptyList())) // Úspěch, ale prázdný seznam
                }
            } else {
                val errorMessage = result.exceptionOrNull()?.message ?: "Neznámá chyba při načítání historie"
                Log.e(TAG, "Failed to fetch history: $errorMessage")
                _historyState.postValue(HistoryState.Error(errorMessage))
            }
        }
    }
    // ***********************************************


    fun getFileLinkForFile(fileItem: FileModel) {
        Log.d(TAG, "getFileLinkForFile() volán pro ${fileItem.name}.")
        if (isUserLoggedIn.value != true) {
            Log.e(TAG, "Pokus o získání odkazu bez platného tokenu. Nastavuji Error stav.")
            _fileLinkState.postValue(FileLinkState.Error("Pro získání odkazu je vyžadováno přihlášení."))
            return
        }

        if (fileItem.password == 1) {
            Log.w(TAG, "Soubor ${fileItem.name} je chráněn heslem - implementace chybí.")
            _fileLinkState.postValue(FileLinkState.Error("Soubor je chráněn heslem. Podpora zatím není implementována."))
            return
        }

        _fileLinkState.postValue(FileLinkState.LoadingLink)

        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Spouštím API volání pro získání odkazu pro soubor s ID: ${fileItem.ident}")
            val result = repository.getFileLink(fileItem.ident, filePassword = null)

            if (result.isSuccess) {
                val fileUrl = result.getOrThrow()
                Log.d(TAG, "API volání získání odkazu úspěšné. Odkaz získán: $fileUrl")
                _fileLinkState.postValue(FileLinkState.LinkSuccess(fileUrl))
            } else {
                Log.e(TAG, "API volání získání odkazu selhalo: ${result.exceptionOrNull()?.message}")
                val errorMessage = result.exceptionOrNull()?.message ?: "Neznámá chyba při získání odkazu"
                _fileLinkState.postValue(FileLinkState.Error(errorMessage))
            }
        }
    }

    fun resetFileLinkState() {
        if (_fileLinkState.value != FileLinkState.Idle) {
            _fileLinkState.postValue(FileLinkState.Idle)
            Log.d(TAG, "FileLinkState resetován na Idle.")
        }
    }


    fun logout() {
        Log.d(TAG, "logout() volán.")
        if (isUserLoggedIn.value != true) {
            Log.d(TAG, "Již odhlášen, přeskakuji logout().")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Spouštím mazání tokenu a credentials v Repository.")
            repository.clearAuthToken()
            repository.clearCredentials()
            _isUserLoggedIn.postValue(false)
            Log.d(TAG, "Token a credentials smazány, isUserLoggedIn nastaven na false.")
        }
    }
}
