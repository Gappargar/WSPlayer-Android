package com.example.wsplayer.ui.search // Váš balíček pro SearchViewModel - ZKONTROLUJTE

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope // Pro práci s coroutines ve ViewModelu

// **Import Repository**
import com.example.wsplayer.data.repository.WebshareRepository // ZKONTROLUJTE CESTU

// **Importy pro datové třídy a sealed classy pro stavy z vašeho data.models balíčku**
// ZKONTROLUJTE, že cesta odpovídá vašemu umístění DataModels.kt
import com.example.wsplayer.data.models.* // <-- Toto by mělo importovat FileModel, SearchState, FileLinkState, SearchResponse, UserDataResponse atd.


import kotlinx.coroutines.Dispatchers // Pro přepnutí kontextu (background vlákna)
import kotlinx.coroutines.launch // Pro spouštění coroutines
import kotlinx.coroutines.withContext // Pro přepnutí kontextu uvnitř coroutine

import android.util.Log // Logování


// ViewModel pro obrazovku vyhledávání.
// Spravuje stavy UI, logiku vyhledávání a získávání odkazů na soubory.
// Přijímá instanci WebshareRepository v konstruktoru (dodá SearchViewModelFactory).
class SearchViewModel(private val repository: WebshareRepository) : ViewModel() {

    private val TAG = "SearchViewModel" // Logovací tag

    // --- Stavy pro UI (LiveData) ---

    private val _searchState = MutableLiveData<SearchState>(SearchState.Idle)
    val searchState: LiveData<SearchState> = _searchState

    private val _fileLinkState = MutableLiveData<FileLinkState>(FileLinkState.Idle)
    val fileLinkState: LiveData<FileLinkState> = _fileLinkState

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _searchResults = MutableLiveData<List<FileModel>>(emptyList())
    val searchResults: LiveData<List<FileModel>> = _searchResults

    private val _totalResults = MutableLiveData<Int>(0)
    val totalResults: LiveData<Int> = _totalResults

    private val _isUserLoggedIn = MutableLiveData<Boolean>()
    val isUserLoggedIn: LiveData<Boolean> = _isUserLoggedIn


    // --- Proměnné pro správu stavu ---
    private var currentSearchQuery: String = ""
    private var currentSearchCategory: String? = null
    private var currentSortOrder: String? = null
    private var currentPage: Int = 0 // Stránky jsou obvykle indexovány od 0 pro výpočet offsetu
    private val resultsPerPage = 20 // Toto je náš 'limit'


    init {
        Log.d(TAG, "Init blok spuštěn. PID: ${android.os.Process.myPid()}")
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Provádím kontrolu tokenu v init bloku.")
            val token = repository.getAuthToken()
            val isLoggedIn = (token != null && token.isNotEmpty())
            _isUserLoggedIn.postValue(isLoggedIn)
            Log.d(TAG, "Kontrola tokenu dokončena. isUserLoggedIn: $isLoggedIn")
            if (isLoggedIn) {
                // loadUserData()
            } else {
                Log.d(TAG, "Token nenalezen při startu ViewModelu - Activity by se měla přesměrovat.")
            }
        }
    }

    fun search(query: String, category: String?, sort: String? = null) {
        Log.d(TAG, "search() volán s dotazem: '$query', kategorií: '$category', řazením: '$sort'")
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

        if (_isLoading.value == true) {
            Log.d(TAG, "Již probíhá načítání, přeskakuji nové vyhledávání.")
            return
        }

        currentSearchQuery = query
        currentSearchCategory = category
        currentSortOrder = sort
        currentPage = 0 // Při novém vyhledávání vždy začínáme od první stránky (pro výpočet offsetu)
        _searchResults.postValue(emptyList())
        _totalResults.postValue(0)

        _isLoading.postValue(true)
        _searchState.postValue(SearchState.Loading)

        viewModelScope.launch(Dispatchers.IO) {
            val calculatedOffset = currentPage * resultsPerPage // Výpočet offsetu
            Log.d(TAG, "Spouštím API volání přes repository pro vyhledávání - query '$currentSearchQuery', category '$currentSearchCategory', sort '$currentSortOrder', limit '$resultsPerPage', offset '$calculatedOffset'.")

            // Předání 'sort', 'limit' a 'offset' do repository metody
            // UJISTĚTE SE, ŽE VAŠE WebshareRepository.searchFiles() TYTO PARAMETRY PŘIJÍMÁ!
            val result = repository.searchFiles(
                query = currentSearchQuery,
                category = currentSearchCategory,
                sort = currentSortOrder,    // Předání parametru sort
                limit = resultsPerPage,   // Předání parametru limit
                offset = calculatedOffset // Předání parametru offset
            )

            _isLoading.postValue(false)

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
            val calculatedOffset = currentPage * resultsPerPage // Výpočet offsetu pro další stránku
            Log.d(TAG, "Spouštím API volání přes repository (další stránka) - query '$currentSearchQuery', sort '$currentSortOrder', limit '$resultsPerPage', offset '$calculatedOffset'.")

            // Předání 'sort', 'limit' a 'offset' i zde
            // UJISTĚTE SE, ŽE VAŠE WebshareRepository.searchFiles() TYTO PARAMETRY PŘIJÍMÁ!
            val result = repository.searchFiles(
                query = currentSearchQuery,
                category = currentSearchCategory,
                sort = currentSortOrder,    // Předání parametru sort
                limit = resultsPerPage,   // Předání parametru limit
                offset = calculatedOffset // Předání parametru offset
            )

            _isLoading.postValue(false)

            if (result.isSuccess) {
                val searchResponse = result.getOrThrow()
                val newFiles = searchResponse.files

                if (newFiles != null && newFiles.isNotEmpty()) {
                    Log.d(TAG, "API volání načítání další stránky úspěšné. Přidávám ${newFiles.size} souborů.")
                    val currentList = _searchResults.value ?: emptyList()
                    val updatedList = currentList + newFiles
                    _searchResults.postValue(updatedList)
                    _searchState.postValue(SearchState.Success(updatedList, _totalResults.value ?: updatedList.size))
                } else {
                    Log.d(TAG, "API volání načítání další stránky vráceny 0 nových souborů.")
                    _searchState.postValue(SearchState.Success(_searchResults.value ?: emptyList(), _totalResults.value ?: 0))
                }
            } else {
                Log.e(TAG, "API volání načítání další stránky selhalo: ${result.exceptionOrNull()?.message}")
                val errorMessage = result.exceptionOrNull()?.message ?: "Neznámá chyba při načítání dalších výsledků"
                _searchState.postValue(SearchState.Error(errorMessage))
            }
        }
    }

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

    // ***** PŘIDÁNA METODA *****
    // Metoda pro resetování stavu odkazu (volat z UI po zpracování LinkSuccess nebo Error)
    fun resetFileLinkState() {
        // Zkontrolujeme, zda aktuální stav není už Idle, abychom zbytečně neaktualizovali
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
