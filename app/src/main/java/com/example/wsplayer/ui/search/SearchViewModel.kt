package com.example.wsplayer.ui.search // Váš balíček pre SearchViewModel - ZKONTROLUJTE

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope // Pre prácu s coroutines vo ViewModeli

// **Import Repository**
import com.example.wsplayer.data.repository.WebshareRepository // ZKONTROLUJTE CESTU

// **Importy pre dátové triedy a sealed classy z vášho data.models balíčku**
import com.example.wsplayer.data.models.*
import com.example.wsplayer.data.models.ParsedEpisodeInfo
import com.example.wsplayer.utils.SeriesFileParser // <-- Import vášho parsera

import kotlinx.coroutines.Dispatchers // Pre prepnutie kontextu (background vlákna)
import kotlinx.coroutines.launch // Pre spúšťanie coroutines
import kotlinx.coroutines.withContext // Pre prepnutie kontextu vnútri coroutine

import android.util.Log // Logovanie

/**
 * Sealed class reprezentujúca stavy načítavania histórie.
 */
sealed class HistoryState {
    object Idle : HistoryState()
    object Loading : HistoryState()
    data class Success(val items: List<HistoryItem>) : HistoryState()
    data class Error(val message: String) : HistoryState()
}

/**
 * Sealed class reprezentujúca stavy procesu vyhľadávania a organizácie seriálu.
 */
sealed class SeriesOrganizationState {
    object Idle : SeriesOrganizationState()
    object Loading : SeriesOrganizationState()
    data class Success(
        val series: OrganizedSeries,
        val otherVideos: List<FileModel>
    ) : SeriesOrganizationState()
    data class Error(val message: String) : SeriesOrganizationState()
    object NoEpisodesFound : SeriesOrganizationState()
}


class SearchViewModel(private val repository: WebshareRepository) : ViewModel() {

    private val TAG = "SearchViewModel"

    // --- Stavy pre UI (LiveData) ---
    private val _searchState = MutableLiveData<SearchState>(SearchState.Idle)
    val searchState: LiveData<SearchState> = _searchState

    private val _fileLinkState = MutableLiveData<FileLinkState>(FileLinkState.Idle)
    val fileLinkState: LiveData<FileLinkState> = _fileLinkState

    private val _historyState = MutableLiveData<HistoryState>(HistoryState.Idle)
    val historyState: LiveData<HistoryState> = _historyState

    private val _seriesOrganizationState = MutableLiveData<SeriesOrganizationState>(SeriesOrganizationState.Idle)
    val seriesOrganizationState: LiveData<SeriesOrganizationState> = _seriesOrganizationState

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _searchResults = MutableLiveData<List<FileModel>>(emptyList())
    val searchResults: LiveData<List<FileModel>> = _searchResults

    private val _totalResults = MutableLiveData<Int>(0)
    val totalResults: LiveData<Int> = _totalResults

    private val _isUserLoggedIn = MutableLiveData<Boolean>()
    val isUserLoggedIn: LiveData<Boolean> = _isUserLoggedIn


    // --- Premenné pre správu stavu ---
    private var currentSearchQuery: String = ""
    private var currentSearchCategory: String? = null
    private var currentSortOrder: String? = null
    var currentPage: Int = 0
        private set
    private val resultsPerPage = 50
    private val seriesSearchLimitPerPage = 150 // Limit pre jeden dopyt pri hľadaní seriálu


    init {
        Log.d(TAG, "Init blok spustený. PID: ${android.os.Process.myPid()}")
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Prebieha kontrola tokenu v init bloku.")
            val token = repository.getAuthToken()
            val isLoggedIn = (token != null && token.isNotEmpty())
            _isUserLoggedIn.postValue(isLoggedIn)
            Log.d(TAG, "Kontrola tokenu dokončená. isUserLoggedIn: $isLoggedIn")
        }
    }

    fun search(query: String, category: String?, sort: String? = null) {
        // ... (kód pre bežné vyhľadávanie - bez zmeny)
        Log.d(TAG, "search() volaný s dopytom: '$query', kategóriou: '$category', radením: '$sort', limit: $resultsPerPage")
        if (query.isEmpty()) {
            _searchState.postValue(SearchState.Idle)
            _searchResults.postValue(emptyList())
            _totalResults.postValue(0)
            Log.d(TAG, "Prázdny dopyt, vraciam sa do Idle.")
            return
        }

        if (isUserLoggedIn.value != true) {
            Log.e(TAG, "Pokus o vyhľadávanie bez platného tokenu. Nastavujem Error stav.")
            _searchState.postValue(SearchState.Error("Pre vyhľadávanie je vyžadované prihlásenie."))
            _searchResults.postValue(emptyList())
            _totalResults.postValue(0)
            return
        }

        if (_isLoading.value == true && _searchState.value is SearchState.Loading) {
            Log.d(TAG, "Už prebieha načítavanie (prvá stránka), preskakujem nové vyhľadávanie.")
            return
        }

        currentSearchQuery = query
        currentSearchCategory = category
        currentSortOrder = sort
        currentPage = 0
        _searchResults.postValue(emptyList())
        _totalResults.postValue(0)

        _isLoading.postValue(true)
        _searchState.postValue(SearchState.Loading)

        viewModelScope.launch(Dispatchers.IO) {
            val calculatedOffset = currentPage * resultsPerPage
            Log.d(TAG, "Spúšťam API volanie cez repository pre vyhľadávanie - query '$currentSearchQuery', category '$currentSearchCategory', sort '$currentSortOrder', limit '$resultsPerPage', offset '$calculatedOffset'.")

            val result = repository.searchFiles(
                query = currentSearchQuery,
                category = currentSearchCategory,
                sort = currentSortOrder,
                limit = resultsPerPage,
                offset = calculatedOffset
            )

            _isLoading.postValue(false)

            if (result.isSuccess) {
                val searchResponse = result.getOrThrow()
                val files = searchResponse.files
                val total = searchResponse.total

                _totalResults.postValue(total)

                if (files != null && files.isNotEmpty()) {
                    Log.d(TAG, "API volanie vyhľadávania úspešné. Nájdených ${files.size} na stránke, celkom $total.")
                    _searchResults.postValue(files)
                    _searchState.postValue(SearchState.Success(files, total))
                } else {
                    Log.d(TAG, "API volanie vyhľadávania úspešné, ale vrátených 0 súborov.")
                    _searchState.postValue(SearchState.EmptyResults)
                    _searchResults.postValue(emptyList())
                    _totalResults.postValue(0)
                }
            } else {
                Log.e(TAG, "API volanie vyhľadávania zlyhalo: ${result.exceptionOrNull()?.message}")
                val errorMessage = result.exceptionOrNull()?.message ?: "Neznáma chyba"
                _searchState.postValue(SearchState.Error(errorMessage))
                _searchResults.postValue(emptyList())
                _totalResults.postValue(0)
            }
        }
    }

    fun loadNextPage() {
        // ... (kód pre načítanie ďalšej stránky - bez zmeny)
        Log.d(TAG, "loadNextPage() volaný.")
        if (isUserLoggedIn.value != true || currentSearchQuery.isEmpty() || _isLoading.value == true) {
            Log.d(TAG, "Preskakujem loadNextPage - nie je prihlásený, nie je dopyt alebo už načítavam.")
            return
        }

        val currentResultsCount = _searchResults.value?.size ?: 0
        val total = _totalResults.value ?: 0

        if (currentResultsCount >= total && total > 0) {
            Log.d(TAG, "Žiadne ďalšie stránky na načítanie (current=$currentResultsCount, total=$total).")
            return
        }

        currentPage++
        _isLoading.postValue(true)
        _searchState.postValue(SearchState.LoadingMore)

        viewModelScope.launch(Dispatchers.IO) {
            val calculatedOffset = currentPage * resultsPerPage
            Log.d(TAG, "Spúšťam API volanie cez repository (ďalšia stránka) - query '$currentSearchQuery', sort '$currentSortOrder', limit '$resultsPerPage', offset '$calculatedOffset'.")

            val result = repository.searchFiles(
                query = currentSearchQuery,
                category = currentSearchCategory,
                sort = currentSortOrder,
                limit = resultsPerPage,
                offset = calculatedOffset
            )

            _isLoading.postValue(false)

            if (result.isSuccess) {
                val searchResponse = result.getOrThrow()
                val newFiles = searchResponse.files

                if (newFiles != null && newFiles.isNotEmpty()) {
                    Log.d(TAG, "API volanie načítania ďalšej stránky úspešné. Pridávam ${newFiles.size} súborov.")
                    val currentList = _searchResults.value ?: emptyList()
                    val updatedList = currentList + newFiles
                    _searchResults.postValue(updatedList)
                    _searchState.postValue(SearchState.Success(updatedList, _totalResults.value ?: updatedList.size))
                } else {
                    Log.d(TAG, "API volanie načítania ďalšej stránky vrátených 0 nových súborov.")
                    _searchState.postValue(SearchState.Success(_searchResults.value ?: emptyList(), _totalResults.value ?: 0))
                }
            } else {
                Log.e(TAG, "API volanie načítania ďalšej stránky zlyhalo: ${result.exceptionOrNull()?.message}")
                val errorMessage = result.exceptionOrNull()?.message ?: "Neznáma chyba pri načítavaní ďalších výsledkov"
                _searchState.postValue(SearchState.Error(errorMessage))
            }
        }
    }

    fun fetchHistory(limit: Int = 20) {
        // ... (kód pre načítanie histórie - bez zmeny)
        Log.d(TAG, "fetchHistory() called with limit: $limit")
        if (isUserLoggedIn.value != true) {
            Log.e(TAG, "Cannot fetch history, user not logged in.")
            _historyState.postValue(HistoryState.Error("Pre zobrazenie histórie je vyžadované prihlásenie."))
            return
        }
        if (_historyState.value is HistoryState.Loading) {
            Log.d(TAG, "History is already loading.")
            return
        }
        _historyState.postValue(HistoryState.Loading)
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.getHistory(offset = 0, limit = limit)
            if (result.isSuccess) {
                val historyResponse = result.getOrThrow()
                if (historyResponse.historyItems.isNotEmpty()) {
                    Log.d(TAG, "History fetched successfully. Items: ${historyResponse.historyItems.size}")
                    _historyState.postValue(HistoryState.Success(historyResponse.historyItems))
                } else {
                    Log.d(TAG, "History fetched, but it's empty.")
                    _historyState.postValue(HistoryState.Success(emptyList()))
                }
            } else {
                val errorMessage = result.exceptionOrNull()?.message ?: "Neznáma chyba pri načítavaní histórie"
                Log.e(TAG, "Failed to fetch history: $errorMessage")
                _historyState.postValue(HistoryState.Error(errorMessage))
            }
        }
    }

    // ***** UPRAVENÁ METÓDA PRE VYHĽADÁVANIE A ORGANIZÁCIU SERIÁLOV *****
    fun searchAndOrganizeSeries(seriesNameQuery: String) {
        Log.d(TAG, "searchAndOrganizeSeries called for: '$seriesNameQuery'")
        if (seriesNameQuery.isBlank()) {
            _seriesOrganizationState.postValue(SeriesOrganizationState.Error("Názov seriálu nemôže byť prázdny."))
            return
        }
        if (isUserLoggedIn.value != true) {
            _seriesOrganizationState.postValue(SeriesOrganizationState.Error("Pre vyhľadávanie seriálov je vyžadované prihlásenie."))
            return
        }
        if (_seriesOrganizationState.value is SeriesOrganizationState.Loading) {
            Log.d(TAG, "Series organization is already in progress.")
            return
        }

        _seriesOrganizationState.postValue(SeriesOrganizationState.Loading)

        viewModelScope.launch(Dispatchers.IO) {
            val allFoundFilesSet = mutableSetOf<FileModel>()

            val originalQuery = seriesNameQuery.trim()
            val normalizedOriginalQuery = originalQuery.lowercase().replace(".", " ")
            val queryKeywords = normalizedOriginalQuery.split(" ").filter { it.length > 1 }


            // 1. Generovanie a vykonanie viacerých vyhľadávacích dopytov
            val searchQueries = mutableListOf<String>()
            searchQueries.add(originalQuery) // Presný názov ako zadal používateľ
            if (originalQuery.contains(" ")) {
                searchQueries.add(originalQuery.replace(" ", ".")) // Varianta s bodkami
            }
            if (originalQuery.contains(".")) {
                searchQueries.add(originalQuery.replace(".", " ")) // Varianta s medzerami (ak pôvodne boli bodky)
            }
            // Pridanie všeobecnejších dopytov, ak pôvodný dopyt neobsahuje špecifické kľúčové slová
            if (!originalQuery.lowercase().contains("season") && !originalQuery.lowercase().contains("séria")) {
                searchQueries.add("$originalQuery season")
                searchQueries.add("$originalQuery séria")
            }
            if (!originalQuery.lowercase().matches(Regex(".*s\\d{1,2}.*"))) { // Ak neobsahuje Sxx
                searchQueries.add("$originalQuery S01")
            }

            val distinctSearchQueries = searchQueries.distinct()
            Log.d(TAG, "Generated search queries for series: $distinctSearchQueries")

            for (query in distinctSearchQueries) {
                Log.d(TAG, "Searching for series with query: '$query'")
                var currentOffset = 0
                var hasMoreResults = true
                var accumulatedResultsForQuery = 0 // Počet načítaných pre TENTO dopyt

                // Zvýšime limit pre jeden API request, ale obmedzíme celkový počet načítaných súborov na dopyt
                val querySpecificLimit = 100 // Napr. 100 na stránku pre tento dopyt
                val maxFilesPerQueryType = 300 // Max 300 súborov na jeden typ dopytu

                while (hasMoreResults && accumulatedResultsForQuery < maxFilesPerQueryType) {
                    val searchResult = repository.searchFiles(
                        query = query,
                        category = "video",
                        // ***** ZMENA TRIEDENIA *****
                        sort = "rating", // Skúsime "rating", alebo null pre API default (relevancia)
                        limit = querySpecificLimit,
                        offset = currentOffset
                    )

                    if (searchResult.isSuccess) {
                        val response = searchResult.getOrNull()
                        response?.files?.let { files ->
                            if (files.isNotEmpty()) {
                                // Predbežné filtrovanie: súbor musí obsahovať VŠETKY kľúčové slová z PÔVODNÉHO normalizovaného dopytu
                                val relevantFiles = files.filter { file ->
                                    val normalizedFileName = file.name.lowercase().replace(".", " ")
                                    queryKeywords.all { keyword -> normalizedFileName.contains(keyword) }
                                }
                                allFoundFilesSet.addAll(relevantFiles)
                                accumulatedResultsForQuery += files.size // Počítame všetky vrátené API, nie len relevantné
                                currentOffset += querySpecificLimit
                                hasMoreResults = files.size == querySpecificLimit && response.total > accumulatedResultsForQuery
                                Log.d(TAG, "Query '$query', offset $currentOffset: API returned ${files.size} (total API: ${response.total}). Filtered to ${relevantFiles.size}. Total unique so far: ${allFoundFilesSet.size}")
                            } else {
                                hasMoreResults = false
                            }
                        } ?: run { hasMoreResults = false }
                    } else {
                        Log.w(TAG, "Search query '$query' failed: ${searchResult.exceptionOrNull()?.message}")
                        hasMoreResults = false
                    }
                    if (!hasMoreResults) break
                }
            }

            if (allFoundFilesSet.isEmpty()) {
                Log.d(TAG, "No files found on Webshare for any series query variations of: '$originalQuery'")
                _seriesOrganizationState.postValue(SeriesOrganizationState.Success(OrganizedSeries(title = originalQuery), emptyList()))
                return@launch
            }

            Log.d(TAG, "Found ${allFoundFilesSet.size} total unique potential files for series '$originalQuery'. Starting parsing...")

            val organizedSeries = OrganizedSeries(title = originalQuery)
            val unclassifiedVideos = mutableListOf<FileModel>()
            var episodesFoundCount = 0

            allFoundFilesSet.forEach { fileModel ->
                // Pre parsovanie použijeme pôvodný dopyt používateľa ako referenčný názov seriálu
                val parsedInfo: ParsedEpisodeInfo? = SeriesFileParser.parseEpisodeInfo(fileModel.name, originalQuery)
                if (parsedInfo != null) {
                    val seriesEpisode = SeriesEpisode(
                        fileModel = fileModel.copy(
                            seriesName = originalQuery,
                            seasonNumber = parsedInfo.seasonNumber,
                            episodeNumber = parsedInfo.episodeNumber,
                            videoQuality = parsedInfo.quality,
                            episodeTitle = parsedInfo.remainingName
                        ),
                        seasonNumber = parsedInfo.seasonNumber,
                        episodeNumber = parsedInfo.episodeNumber,
                        quality = parsedInfo.quality,
                        extractedEpisodeTitle = parsedInfo.remainingName
                    )
                    val season = organizedSeries.seasons.getOrPut(parsedInfo.seasonNumber) {
                        SeriesSeason(parsedInfo.seasonNumber)
                    }
                    season.addAndSortEpisode(seriesEpisode)
                    episodesFoundCount++
                } else {
                    unclassifiedVideos.add(fileModel)
                    Log.d(TAG, "Added to unclassified videos (could not parse S/E): ${fileModel.name}")
                }
            }

            if (episodesFoundCount > 0 || unclassifiedVideos.isNotEmpty()) {
                Log.d(TAG, "Successfully processed files for '$originalQuery'. Episodes: $episodesFoundCount, Other Videos: ${unclassifiedVideos.size}.")
                _seriesOrganizationState.postValue(SeriesOrganizationState.Success(organizedSeries, unclassifiedVideos))
            } else {
                Log.d(TAG, "No episodes or other videos could be reliably parsed for '$originalQuery'.")
                _seriesOrganizationState.postValue(SeriesOrganizationState.Success(OrganizedSeries(title = originalQuery), emptyList()))
            }
        }
    }
    // *****************************************************************


    fun getFileLinkForFile(fileItem: FileModel) {
        // ... (kód pre získanie odkazu - bez zmeny)
        Log.d(TAG, "getFileLinkForFile() volaný pre ${fileItem.name}.")
        if (isUserLoggedIn.value != true) {
            Log.e(TAG, "Pokus o získanie odkazu bez platného tokenu. Nastavujem Error stav.")
            _fileLinkState.postValue(FileLinkState.Error("Pre získanie odkazu je vyžadované prihlásenie."))
            return
        }

        if (fileItem.password == 1) {
            Log.w(TAG, "Súbor ${fileItem.name} je chránený heslom - implementácia chýba.")
            _fileLinkState.postValue(FileLinkState.Error("Súbor je chránený heslom. Podpora zatiaľ nie je implementovaná."))
            return
        }

        _fileLinkState.postValue(FileLinkState.LoadingLink)

        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Spúšťam API volanie pre získanie odkazu pre súbor s ID: ${fileItem.ident}")
            val result = repository.getFileLink(fileItem.ident, filePassword = null)

            if (result.isSuccess) {
                val fileUrl = result.getOrThrow()
                Log.d(TAG, "API volanie získania odkazu úspešné. Odkaz získaný: $fileUrl")
                _fileLinkState.postValue(FileLinkState.LinkSuccess(fileUrl))
            } else {
                Log.e(TAG, "API volanie získania odkazu zlyhalo: ${result.exceptionOrNull()?.message}")
                val errorMessage = result.exceptionOrNull()?.message ?: "Neznáma chyba pri získavaní odkazu"
                _fileLinkState.postValue(FileLinkState.Error(errorMessage))
            }
        }
    }

    fun resetFileLinkState() {
        if (_fileLinkState.value != FileLinkState.Idle) {
            _fileLinkState.postValue(FileLinkState.Idle)
            Log.d(TAG, "FileLinkState resetovaný na Idle.")
        }
    }


    fun logout() {
        // ... (kód pre odhlásenie - bez zmeny)
        Log.d(TAG, "logout() volaný.")
        if (isUserLoggedIn.value != true) {
            Log.d(TAG, "Už odhlásený, preskakujem logout().")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Spúšťam mazanie tokenu a credentials v Repository.")
            repository.clearAuthToken()
            repository.clearCredentials()
            _isUserLoggedIn.postValue(false)
            Log.d(TAG, "Token a credentials zmazané, isUserLoggedIn nastavený na false.")
        }
    }
}
