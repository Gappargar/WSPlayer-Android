package com.example.wsplayer.ui.search // Váš balíček pro SearchViewModel - ZKONTROLUJTE

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.example.wsplayer.data.repository.WebshareRepository

// **Importujte vaše modelové třídy z vašeho data.models balíčku**
// ZKONTROLUJTE, že cesta odpovídá vašemu umístění DataModels.kt
import com.example.wsplayer.data.models.* // Toto by mělo importovat VŠECHNY třídy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class SearchViewModel(private val repository: WebshareRepository) : ViewModel() {

    // --- Stavy pro UI (LiveData) ---
    private val _searchState = MutableLiveData<SearchState>(SearchState.Idle)
    val searchState: LiveData<SearchState> = _searchState

    // **Zkontrolovat, že používáte FileLinkState s LinkError**
    private val _fileLinkState = MutableLiveData<FileLinkState>(FileLinkState.Idle) // Používá FileLinkState z models
    val fileLinkState: LiveData<FileLinkState> = _fileLinkState

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _searchResults = MutableLiveData<List<FileModel>>(emptyList()) // Seznam typu FileModel
    val searchResults: LiveData<List<FileModel>> = _searchResults

    private val _totalResults = MutableLiveData<Int>(0)
    val totalResults: LiveData<Int> = _totalResults

    private val _isUserLoggedIn = MutableLiveData<Boolean>()
    val isUserLoggedIn: LiveData<Boolean> = _isUserLoggedIn


    // --- Proměnné pro správu stavu ---
    private var currentSearchQuery: String = ""
    private var currentSearchCategory: String = ""
    private var currentPage: Int = 0
    private val resultsPerPage = 20


    init {
        println("SearchViewModel: Init blok spuštěn. PID: ${android.os.Process.myPid()}")

        viewModelScope.launch(Dispatchers.IO) {
            println("SearchViewModel: Provádím kontrolu tokenu v init bloku.")
            val token = repository.getAuthToken()
            val isLoggedIn = (token != null && token.isNotEmpty())
            _isUserLoggedIn.postValue(isLoggedIn)
            println("SearchViewModel: Kontrola tokenu dokončena. isUserLoggedIn: ${isUserLoggedIn.value}")

            if (isLoggedIn) {
                // TODO: Volitelně spustit prvotní načtení dat
            } else {
                println("SearchViewModel: Token nenalezen při startu ViewModelu - Activity by se měla přesměrovat.")
            }
        }
    }

    // --- Metody ---

    fun search(query: String, category: String = "") {
        println("SearchViewModel: search() volán s dotazem: '$query', kategorií: '$category'")
        if (query.isEmpty()) {
            _searchState.postValue(SearchState.Idle)
            _searchResults.postValue(emptyList())
            _totalResults.postValue(0)
            println("SearchViewModel: Prázdný dotaz, vracím se do Idle.")
            return
        }

        if (isUserLoggedIn.value != true) {
            println("SearchViewModel: Pokus o vyhledávání bez platného tokenu. Nastavuji Error stav.")
            _searchState.postValue(SearchState.Error("Pro vyhledávání je vyžadováno přihlášení.")) // Používá SearchState.Error
            _searchResults.postValue(emptyList())
            _totalResults.postValue(0)
            return
        }

        if (_isLoading.value == true) {
            println("SearchViewModel: Již probíhá načítání, přeskakuji nové vyhledávání.")
            return
        }

        currentSearchQuery = query
        currentSearchCategory = category
        currentPage = 0
        _searchResults.postValue(emptyList())
        _totalResults.postValue(0)

        _isLoading.postValue(true)
        _searchState.postValue(SearchState.Loading)

        viewModelScope.launch(Dispatchers.IO) {
            println("SearchViewModel: Spouštím API volání pro vyhledávání - stránka $currentPage pro dotaz '$currentSearchQuery'.")
            val result = repository.searchFiles(currentSearchQuery, currentSearchCategory, currentPage, resultsPerPage)

            _isLoading.postValue(false)

            if (result.isSuccess) {
                val searchResponse = result.getOrThrow() // Typ SearchResponse
                val files = searchResponse.files // Typ List<FileModel>?
                val total = searchResponse.total // Typ Int

                _totalResults.postValue(total)

                if (files != null && files.isNotEmpty()) {
                    println("SearchViewModel: API volání vyhledávání úspěšné. Nalezeno ${files.size} na stránce, celkem $total.")
                    // **Potvrdit, že files není null**
                    _searchResults.postValue(files!!) // Použít !! nebo as List<FileModel>
                    _searchState.postValue(SearchState.Success(files!!, total)) // Použít !!
                } else {
                    println("SearchViewModel: API volání vyhledávání úspěšné, ale vráceny 0 souborů.")
                    _searchState.postValue(SearchState.EmptyResults)
                    _searchResults.postValue(emptyList())
                    _totalResults.postValue(0)
                }
            } else {
                println("SearchViewModel: API volání vyhledávání selhalo.")
                val errorMessage = result.exceptionOrNull()?.message ?: "Neznámá chyba"
                _searchState.postValue(SearchState.Error(errorMessage)) // Používá SearchState.Error
                _searchResults.postValue(emptyList())
                _totalResults.postValue(0)
            }
        }
    }

    fun loadNextPage() {
        println("SearchViewModel: loadNextPage() volán.")
        if (isUserLoggedIn.value != true || currentSearchQuery.isEmpty() || _isLoading.value == true) {
            println("SearchViewModel: Přeskakuji loadNextPage - není přihlášen, není dotaz nebo již načítám.")
            return
        }

        val currentResultsCount = _searchResults.value?.size ?: 0
        val total = _totalResults.value ?: 0

        if (currentResultsCount >= total || total == 0) {
            println("SearchViewModel: Žádné další stránky k načtení (current=$currentResultsCount, total=$total).")
            return
        }

        currentPage++
        _isLoading.postValue(true)
        _searchState.postValue(SearchState.LoadingMore) // Používá SearchState.LoadingMore

        viewModelScope.launch(Dispatchers.IO) {
            println("SearchViewModel: Spouštím API volání pro vyhledávání - stránka $currentPage (načítání další).")
            val result = repository.searchFiles(currentSearchQuery, currentSearchCategory, currentPage, resultsPerPage)

            _isLoading.postValue(false)

            if (result.isSuccess) {
                val searchResponse = result.getOrThrow() // Typ SearchResponse
                val files = searchResponse.files // Typ List<FileModel>?

                if (files != null && files.isNotEmpty()) {
                    println("SearchViewModel: API volání načítání další stránky úspěšné. Přidávám ${files.size} souborů.")
                    val currentList = _searchResults.value ?: emptyList()
                    // **Potvrdit, že files není null**
                    _searchResults.postValue(currentList + files!!) // Použít !!
                    _searchState.postValue(SearchState.Success(currentList + files!!, _totalResults.value ?: (currentList.size + files.size))) // Použít !!
                } else {
                    println("SearchViewModel: API volání načítání další stránky vráceny 0 souborů.")
                    _searchState.postValue(SearchState.Success(_searchResults.value ?: emptyList(), _totalResults.value ?: 0)) // Aktualizovat stav s nezměněným seznamem
                }
            } else {
                println("SearchViewModel: API volání načítání další stránky selhalo.")
                val errorMessage = result.exceptionOrNull()?.message ?: "Neznámá chyba při načítání dalších výsledků"
                _searchState.postValue(SearchState.Error(errorMessage)) // Používá SearchState.Error
            }
        }
    }

    fun getFileLinkForFile(fileItem: FileModel) { // Přijímá FileModel
        println("SearchViewModel: getFileLinkForFile() volán pro ${fileItem.name}.")
        if (isUserLoggedIn.value != true) {
            println("SearchViewModel: Pokus o získání odkazu bez platného tokenu. Nastavuji Error stav.")
            _fileLinkState.postValue(FileLinkState.LinkError("Pro získání odkazu je vyžadováno přihlášení.")) // Používá FileLinkState.LinkError
            return
        }

        if (fileItem.password == 1) {
            println("SearchViewModel: Soubor chráněn heslem - implementace chybí.")
            _fileLinkState.postValue(FileLinkState.LinkError("Soubor je chráněn heslem. Podpora zatím není implementována.")) // POZOR: Tady byl původně Error - pokud v DataModels.kt máte LinkError, použijte LinkError i zde. Zkontrolujte DataModels.kt
            return
        }


        _fileLinkState.postValue(FileLinkState.LoadingLink) // Používá FileLinkState.LoadingLink

        viewModelScope.launch(Dispatchers.IO) {
            println("SearchViewModel: Spouštím API volání pro získání odkazu pro soubor s ID: ${fileItem.ident}")
            val result = repository.getFileLink(fileItem.ident, filePassword = null) // Používá ident

            if (result.isSuccess) {
                val fileUrl = result.getOrThrow() // Výsledek je String (URL odkazu)
                println("SearchViewModel: API volání získání odkazu úspěšné. Odkaz získán.")
                _fileLinkState.postValue(FileLinkState.LinkSuccess(fileUrl)) // Používá FileLinkState.LinkSuccess
            } else {
                println("SearchViewModel: API volání získání odkazu selhalo.")
                val errorMessage = result.exceptionOrNull()?.message ?: "Neznámá chyba při získání odkazu"
                _fileLinkState.postValue(FileLinkState.LinkError(errorMessage)) // Používá FileLinkState.LinkError
            }
        }
    }

    fun logout() {
        println("SearchViewModel: logout() volán.")
        if (isUserLoggedIn.value != true) {
            println("SearchViewModel: Již odhlášen, přeskakuji logout().")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            println("SearchViewModel: Spouštím mazání tokenu a credentials v Repository.")
            repository.clearAuthToken() // <- Tuto metodu musí mít Repository A MUSÍ BÝT PUBLIC
            repository.clearCredentials() // <- Tuto metodu musí mít Repository A MUSÍ BÝT PUBLIC

            _isUserLoggedIn.postValue(false)

            println("SearchViewModel: Token a credentials smazány, isUserLoggedIn nastaven na false.")
        }
    }

    // TODO: Volitelné metody loadDefaultSearch(), loadUserData(), atd.
}