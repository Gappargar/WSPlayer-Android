// app/src/main/java/com/example/wsplayer/ui/search/SearchViewModel.kt
package com.example.wsplayer.ui.search // Váš balíček pro SearchViewModel - ZKONTROLUJTE

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope // Pro práci s coroutines ve ViewModelu

// **Import Repository**
import com.example.wsplayer.data.repository.WebshareRepository

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

    // Stav aktuálního vyhledávání (Idle, Loading, Success, Error, Empty, LoadingMore)
    private val _searchState = MutableLiveData<SearchState>(SearchState.Idle)
    val searchState: LiveData<SearchState> = _searchState // Activity sleduje tento LiveData

    // Stav získávání odkazu na soubor (Idle, LoadingLink, LinkSuccess, Error)
    // Předpokládáme, že ve vašem DataModels.kt máte FileLinkState.Error (pokud LinkError, použijte LinkError)
    private val _fileLinkState = MutableLiveData<FileLinkState>(FileLinkState.Idle) // Používá FileLinkState z models
    val fileLinkState: LiveData<FileLinkState> = _fileLinkState // Activity sleduje tento LiveData

    // Indikátor načítání (pro vyhledávání a stránkování)
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading // Activity sleduje tento LiveData (ProgressBar)

    // Seznam nalezených souborů pro zobrazení v RecyclerView
    private val _searchResults = MutableLiveData<List<FileModel>>(emptyList()) // Seznam typu FileModel
    val searchResults: LiveData<List<FileModel>> = _searchResults // Activity sleduje tento LiveData (Adapter)

    // Celkový počet nalezených výsledků (pro informaci o stránkování)
    private val _totalResults = MutableLiveData<Int>(0)
    val totalResults: LiveData<Int> = _totalResults // Activity může zobrazit celkový počet

    // **Stav přihlášení uživatele - KLÍČOVÉ pro SearchActivity**
    // Tato LiveData signalizuje Activity, zda je uživatel přihlášen (má platný token)
    private val _isUserLoggedIn = MutableLiveData<Boolean>()
    val isUserLoggedIn: LiveData<Boolean> = _isUserLoggedIn // Activity sleduje tuto LiveData pro rozhodnutí o navigaci


    // --- Proměnné pro správu stavu ---
    private var currentSearchQuery: String = ""
    private var currentSearchCategory: String = ""
    private var currentPage: Int = 0 // Aktuální načtená stránka výsledků
    private val resultsPerPage = 20 // Počet výsledků na stránku (ověřte dle API)


    // Inicializační blok - spustí se při prvním vytvoření ViewModelu
    // Toto se stane, když se poprvé spustí SearchActivity v novém Tasku
    init {
        Log.d(TAG, "Init blok spuštěn. PID: ${android.os.Process.myPid()}")

        // **Při vytvoření ViewModelu zkontrolujte, zda je dostupný TOKEN**
        // Toto je správný způsob, jak ověřit, zda je uživatel "technicky" přihlášen (má token)
        viewModelScope.launch(Dispatchers.IO) { // Spustit kontrolu v background vlákně
            Log.d(TAG, "Provádím kontrolu tokenu v init bloku.")
            val token = repository.getAuthToken() // Získat token z Repository

            // Nastavit stav přihlášení na základě existence tokenu
            val isLoggedIn = (token != null && token.isNotEmpty()) // Zkontrolovat, zda je token ne-null a neprázdný
            _isUserLoggedIn.postValue(isLoggedIn) // postValue je bezpečné z background vlákna

            Log.d(TAG, "Kontrola tokenu dokončena. isUserLoggedIn: ${isUserLoggedIn.value}")

            // Pokud je uživatel přihlášen (má token), můžete zde spustit prvotní načtení dat,
            // např. načtení posledních souborů nebo info o uživateli.
            if (isLoggedIn) {
                // TODO: Volitelně spustit prvotní načtení dat po úspěšné kontrole tokenu
                // napr. loadDefaultSearch()
                // loadUserData() // Příklad: načíst info o uživateli hned po startu SearchActivity
            } else {
                // Pokud token neexistuje při startu SearchViewModelu, Activity se přesměruje observerem
                Log.d(TAG, "Token nenalezen při startu ViewModelu - Activity by se měla přesměrovat.")
            }
        }
    }

    // --- Metody pro logiku obrazovky ---

    // Metoda pro spuštění nového vyhledávání
    fun search(query: String, category: String = "") {
        Log.d(TAG, "search() volán s dotazem: '$query', kategorií: '$category'")
        if (query.isEmpty()) {
            _searchState.postValue(SearchState.Idle)
            _searchResults.postValue(emptyList())
            _totalResults.postValue(0)
            Log.d(TAG, "Prázdný dotaz, vracím se do Idle.")
            return
        }

        // **Přidat kontrolu přihlášení před spuštěním vyhledávání**
        // Vyhledávání má smysl pouze pokud je uživatel přihlášen (má token)
        if (isUserLoggedIn.value != true) {
            Log.e(TAG, "Pokus o vyhledávání bez platného tokenu. Nastavuji Error stav.")
            _searchState.postValue(SearchState.Error("Pro vyhledávání je vyžadováno přihlášení.")) // Používá SearchState.Error
            _searchResults.postValue(emptyList())
            _totalResults.postValue(0)
            return // Nepokračovat ve vyhledávání bez přihlášení
        }

        // Pokud už už načítáme, nezačínat nové vyhledávání (dokud se to nedokončí)
        if (_isLoading.value == true) {
            Log.d(TAG, "Již probíhá načítání, přeskakuji nové vyhledávání.")
            return
        }


        currentSearchQuery = query
        currentSearchCategory = category
        currentPage = 0 // Při novém vyhledávání začínáme vždy od první stránky
        _searchResults.postValue(emptyList()) // Vyčistit předchozí výsledky
        _totalResults.postValue(0) // Resetovat celkový počet

        _isLoading.postValue(true) // Začínáme načítat
        _searchState.postValue(SearchState.Loading) // Nastavit stav načítání (první stránka)

        viewModelScope.launch(Dispatchers.IO) { // Spustit API volání v background vlákně
            Log.d(TAG, "Spouštím API volání pro vyhledávání - stránka $currentPage pro dotaz '$currentSearchQuery'.")
            val result = repository.searchFiles(currentSearchQuery, currentSearchCategory, currentPage, resultsPerPage)

            _isLoading.postValue(false) // Načítání dokončeno (nezávisle na úspěchu API volání)

            if (result.isSuccess) {
                val searchResponse = result.getOrThrow() // Typ SearchResponse
                val files = searchResponse.files // Typ List<FileModel>?
                val total = searchResponse.total // Celkový počet

                _totalResults.postValue(total)

                if (files != null && files.isNotEmpty()) {
                    Log.d(TAG, "API volání vyhledávání úspěšné. Nalezeno ${files.size} na stránce, celkem $total.")
                    _searchResults.postValue(files!!) // Použít !! zde po kontrole null
                    _searchState.postValue(SearchState.Success(files!!, total)) // Použít !!
                } else {
                    Log.d(TAG, "API volání vyhledávání úspěšné, ale vráceny 0 souborů.")
                    _searchState.postValue(SearchState.EmptyResults)
                    _searchResults.postValue(emptyList())
                    _totalResults.postValue(0)
                }
            } else {
                Log.e(TAG, "API volání vyhledávání selhalo.")
                val errorMessage = result.exceptionOrNull()?.message ?: "Neznámá chyba"
                _searchState.postValue(SearchState.Error(errorMessage)) // Používá SearchState.Error
                _searchResults.postValue(emptyList())
                _totalResults.postValue(0)
            }
        }
    }

    // Metoda pro načtení další stránky výsledků (pro stránkování)
    fun loadNextPage() {
        Log.d(TAG, "loadNextPage() volán.")
        if (isUserLoggedIn.value != true || currentSearchQuery.isEmpty() || _isLoading.value == true) {
            Log.d(TAG, "Přeskakuji loadNextPage - není přihlášen, není dotaz nebo již načítám.")
            return
        }

        val currentResultsCount = _searchResults.value?.size ?: 0
        val total = _totalResults.value ?: 0

        if (currentResultsCount >= total || total == 0) {
            Log.d(TAG, "Žádné další stránky k načtení (current=$currentResultsCount, total=$total).")
            return
        }

        currentPage++ // Přepnout na další stránku
        _isLoading.postValue(true)
        _searchState.postValue(SearchState.LoadingMore) // Používá SearchState.LoadingMore

        viewModelScope.launch(Dispatchers.IO) { // Spustit API volání v background vlákně
            Log.d(TAG, "Spouštím API volání pro vyhledávání - stránka $currentPage (načítání další).")
            val result = repository.searchFiles(currentSearchQuery, currentSearchCategory, currentPage, resultsPerPage)

            _isLoading.postValue(false) // Načítání dokončeno

            if (result.isSuccess) {
                val searchResponse = result.getOrThrow() // Typ SearchResponse
                val files = searchResponse.files // Typ List<FileModel>?

                if (files != null && files.isNotEmpty()) {
                    Log.d(TAG, "API volání načítání další stránky úspěšné. Přidávám ${files.size} souborů.")
                    val currentList = _searchResults.value ?: emptyList()
                    _searchResults.postValue(currentList + files!!) // Použít !! zde po kontrole null
                    // Aktualizovat SearchState zpět na Success
                    _searchState.postValue(SearchState.Success(currentList + files!!, _totalResults.value ?: (currentList.size + files.size))) // Použít !!
                } else {
                    Log.d(TAG, "API volání načítání další stránky vráceny 0 souborů.")
                    _searchState.postValue(SearchState.Success(_searchResults.value ?: emptyList(), _totalResults.value ?: 0)) // Aktualizovat stav s nezměněným seznamem
                }
            } else {
                Log.e(TAG, "API volání načítání další stránky selhalo.")
                val errorMessage = result.exceptionOrNull()?.message ?: "Neznámá chyba při načítání dalších výsledků"
                _searchState.postValue(SearchState.Error(errorMessage)) // Používá SearchState.Error
            }
        }
    }

    // Metoda pro získání přímého odkazu na soubor pro přehrávání
    fun getFileLinkForFile(fileItem: FileModel) { // Přijímá FileModel
        Log.d(TAG, "getFileLinkForFile() volán pro ${fileItem.name}.")
        if (isUserLoggedIn.value != true) {
            Log.e(TAG, "Pokus o získání odkazu bez platného tokenu. Nastavuji Error stav.")
            // Předpokládáme, že FileLinkState.Error se jmenuje Error v DataModels (pokud LinkError, použijte LinkError)
            _fileLinkState.postValue(FileLinkState.Error("Pro získání odkazu je vyžadováno přihlášení.")) // <- ZKONTROLUJTE NÁZEV STAVU V DataModels.kt
            return
        }

        // TODO: Pokud soubor vyžaduje heslo (fileItem.password == 1), zde by se měla spustit logika pro zadání hesla
        if (fileItem.password == 1) {
            Log.e(TAG, "Soubor chráněn heslem - implementace chybí.")
            // Předpokládáme, že FileLinkState.Error se jmenuje Error v DataModels
            _fileLinkState.postValue(FileLinkState.Error("Soubor je chráněn heslem. Podpora zatím není implementována.")) // <- ZKONTROLUJTE NÁZEV STAVU ZDE
            return
        }


        _fileLinkState.postValue(FileLinkState.LoadingLink) // Používá FileLinkState.LoadingLink

        viewModelScope.launch(Dispatchers.IO) { // Spustit API volání v background vlákně
            Log.d(TAG, "Spouštím API volání pro získání odkazu pro soubor s ID: ${fileItem.ident}")
            // Předání hesla souboru, pokud je k dispozici (nyní null)
            val result = repository.getFileLink(fileItem.ident, filePassword = null) // Používá ident z FileModel

            if (result.isSuccess) {
                val fileUrl = result.getOrThrow() // Výsledek je String (URL odkazu)
                Log.d(TAG, "API volání získání odkazu úspěšné. Odkaz získán.")
                _fileLinkState.postValue(FileLinkState.LinkSuccess(fileUrl)) // Používá FileLinkState.LinkSuccess
            } else {
                Log.e(TAG, "API volání získání odkazu selhalo.")
                val errorMessage = result.exceptionOrNull()?.message ?: "Neznámá chyba při získání odkazu"
                // Předpokládáme, že FileLinkState.Error se jmenuje Error
                _fileLinkState.postValue(FileLinkState.Error(errorMessage)) // <- ZKONTROLUJTE NÁZEV STAVU ZDE
            }
        }
    }

    // Metoda pro spuštění odhlášení
    fun logout() {
        Log.d(TAG, "logout() volán.")
        if (isUserLoggedIn.value != true) {
            Log.d(TAG, "Již odhlášen, přeskakuji logout().")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Spouštím mazání tokenu a credentials v Repository.")
            repository.clearAuthToken() // <-- Volá public metodu Repository
            repository.clearCredentials() // <-- Volá public metodu Repository

            _isUserLoggedIn.postValue(false) // Signalizovat Activity, že uživatel NENÍ přihlášen

            Log.d(TAG, "Token a credentials smazány, isUserLoggedIn nastaven na false.")
        }
    }

    // TODO: Volitelné metody jako loadDefaultSearch(), loadUserData(), atd.
}