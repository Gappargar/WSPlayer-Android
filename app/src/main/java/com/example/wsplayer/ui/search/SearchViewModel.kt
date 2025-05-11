package com.example.wsplayer.ui.search // Váš balíček pre SearchViewModel - ZKONTROLUJTE

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope // Pre prácu s coroutines vo ViewModeli

// **Import Repository**
import com.example.wsplayer.data.repository.WebshareRepository // ZKONTROLUJTE CESTU

// **Importy pre dátové triedy a sealed classy z vášho data.models balíčku**
import com.example.wsplayer.data.models.*
import com.example.wsplayer.data.models.ParsedEpisodeInfo // Explicitný import pre istotu
import com.example.wsplayer.utils.SeriesFileParser // <-- Import vášho parsera

import kotlinx.coroutines.Dispatchers // Pre prepnutie kontextu (background vlákna)
import kotlinx.coroutines.launch // Pre spúšťanie coroutines
import kotlinx.coroutines.withContext // Pre prepnutie kontextu vnútri coroutine

import android.util.Log // Logovanie
import java.util.regex.Pattern // Pre prácu s regulárnymi výrazmi

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
        val otherVideos: List<FileModel> // Zoznam filmov a iných nezaradených videí
    ) : SeriesOrganizationState()
    data class Error(val message: String) : SeriesOrganizationState()
    // NoEpisodesFound sa už nepoužíva, Success s prázdnymi zoznamami ho nahrádza
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

    private val _isLoading = MutableLiveData<Boolean>(false) // Pre bežné vyhľadávanie
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
    private val seriesSearchLimitPerPage = 150
    private val maxFilesPerQueryType = 450


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
        Log.d(TAG, "search() volaný s dopytom: '$query', kategóriou: '$category', radením: '$sort', limit: $resultsPerPage")
        if (query.isEmpty()) {
            _searchState.postValue(SearchState.Idle); _searchResults.postValue(emptyList()); _totalResults.postValue(0)
            Log.d(TAG, "Prázdny dopyt, vraciam sa do Idle."); return
        }
        if (isUserLoggedIn.value != true) {
            Log.e(TAG, "Pokus o vyhľadávanie bez platného tokenu."); _searchState.postValue(SearchState.Error("Pre vyhľadávanie je vyžadované prihlásenie."))
            _searchResults.postValue(emptyList()); _totalResults.postValue(0); return
        }
        if (_isLoading.value == true && _searchState.value is SearchState.Loading) {
            Log.d(TAG, "Už prebieha načítavanie (prvá stránka), preskakujem nové vyhľadávanie."); return
        }
        currentSearchQuery = query; currentSearchCategory = category; currentSortOrder = sort; currentPage = 0
        _searchResults.postValue(emptyList()); _totalResults.postValue(0); _isLoading.postValue(true); _searchState.postValue(SearchState.Loading)
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.searchFiles(query=currentSearchQuery, category=currentSearchCategory, sort=currentSortOrder, limit=resultsPerPage, offset=(currentPage*resultsPerPage))
            _isLoading.postValue(false)
            if (result.isSuccess) {
                val response = result.getOrThrow(); _totalResults.postValue(response.total)
                if (!response.files.isNullOrEmpty()) {
                    _searchResults.postValue(response.files); _searchState.postValue(SearchState.Success(response.files, response.total))
                } else {
                    _searchState.postValue(SearchState.EmptyResults); _searchResults.postValue(emptyList())
                }
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Neznáma chyba"; _searchState.postValue(SearchState.Error(errorMsg))
                _searchResults.postValue(emptyList()); _totalResults.postValue(0)
            }
        }
    }

    fun loadNextPage() {
        Log.d(TAG, "loadNextPage() volaný.")
        if (isUserLoggedIn.value != true || currentSearchQuery.isEmpty() || _isLoading.value == true) {
            Log.d(TAG, "Preskakujem loadNextPage."); return
        }
        val currentCount = _searchResults.value?.size ?: 0; val total = _totalResults.value ?: 0
        if (currentCount >= total && total > 0) { Log.d(TAG, "Žiadne ďalšie stránky."); return }
        currentPage++; _isLoading.postValue(true); _searchState.postValue(SearchState.LoadingMore)
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.searchFiles(query=currentSearchQuery, category=currentSearchCategory, sort=currentSortOrder, limit=resultsPerPage, offset=(currentPage*resultsPerPage))
            _isLoading.postValue(false)
            if (result.isSuccess) {
                val response = result.getOrThrow()
                if (!response.files.isNullOrEmpty()) {
                    val currentList = _searchResults.value ?: emptyList(); val updatedList = currentList + response.files
                    _searchResults.postValue(updatedList); _searchState.postValue(SearchState.Success(updatedList, _totalResults.value ?: updatedList.size))
                } else {
                    _searchState.postValue(SearchState.Success(_searchResults.value ?: emptyList(), _totalResults.value ?: 0))
                }
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Neznáma chyba pri načítavaní"; _searchState.postValue(SearchState.Error(errorMsg))
            }
        }
    }

    fun fetchHistory(limit: Int = 20) {
        Log.d(TAG, "fetchHistory() called with limit: $limit")
        if (isUserLoggedIn.value != true) {
            _historyState.postValue(HistoryState.Error("Pre zobrazenie histórie je vyžadované prihlásenie.")); return
        }
        if (_historyState.value is HistoryState.Loading) { Log.d(TAG, "History is already loading."); return }
        _historyState.postValue(HistoryState.Loading)
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.getHistory(offset = 0, limit = limit)
            if (result.isSuccess) {
                val response = result.getOrThrow()
                _historyState.postValue(HistoryState.Success(response.historyItems))
            } else {
                _historyState.postValue(HistoryState.Error(result.exceptionOrNull()?.message ?: "Neznáma chyba"))
            }
        }
    }

    // ***** UPRAVENÁ METÓDA PRE VYHĽADÁVANIE A ORGANIZÁCIU SERIÁLOV *****
    fun searchAndOrganizeSeries(seriesNameQuery: String) {
        Log.d(TAG, "searchAndOrganizeSeries called for: '$seriesNameQuery'")
        if (seriesNameQuery.isBlank()) {
            _seriesOrganizationState.postValue(SeriesOrganizationState.Error("Název seriálu nemůže být prázdný."))
            return
        }
        if (isUserLoggedIn.value != true) {
            _seriesOrganizationState.postValue(SeriesOrganizationState.Error("Pro vyhledávání seriálů je vyžadováno přihlášení."))
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

            // Normalizovaný pôvodný dopyt pre filtrovanie - odstránime len rok a špeciálne znaky, jazyk ponecháme
            val normalizedQueryForFiltering = originalQuery.lowercase()
                .replace(Regex("""\s*\(\d{4}\)\s*"""), " ") // Odstrániť rok v zátvorkách
                .replace(Regex("""\b\d{4}\b"""), " ")      // Odstrániť samostatný rok
                .replace(Regex("[^a-zA-Z0-9\\s\\.]"), " ") // Ponechať bodky pre názvy ako "star.trek"
                .replace(Regex("\\s+"), " ").trim()
            Log.d(TAG, "Normalized query for filtering: '$normalizedQueryForFiltering'")


            // 1. Generovanie a vykonanie viacerých vyhľadávacích dopytov
            val searchApiQueries = mutableListOf<String>()
            searchApiQueries.add(originalQuery) // Presný názov ako zadal používateľ
            if (originalQuery.contains(" ")) searchApiQueries.add(originalQuery.replace(" ", "."))
            if (originalQuery.contains(".")) searchApiQueries.add(originalQuery.replace(".", " "))

            val baseNameWithoutYear = originalQuery.replace(Regex("""\s*\(\d{4}\)\s*|\s+\d{4}$"""), "").trim()
            if (baseNameWithoutYear.isNotBlank() && baseNameWithoutYear != originalQuery) {
                searchApiQueries.add(baseNameWithoutYear)
                if (baseNameWithoutYear.contains(" ")) searchApiQueries.add(baseNameWithoutYear.replace(" ", "."))
                if (baseNameWithoutYear.contains(".")) searchApiQueries.add(baseNameWithoutYear.replace(".", " "))
            }
            val targetForGenericQueries = if (baseNameWithoutYear.isNotBlank()) baseNameWithoutYear else originalQuery
            if (targetForGenericQueries.isNotBlank()) {
                if (!targetForGenericQueries.lowercase().contains("season") && !targetForGenericQueries.lowercase().contains("séria")) {
                    searchApiQueries.add("$targetForGenericQueries season")
                    searchApiQueries.add("$targetForGenericQueries séria")
                }
                // Tento dopyt môže byť príliš všeobecný, zvážiť jeho odstránenie alebo úpravu
                // if (!targetForGenericQueries.lowercase().matches(Regex(".*s\\d{1,2}.*"))) {
                //     searchApiQueries.add("$targetForGenericQueries S01")
                // }
            }

            val distinctSearchApiQueries = searchApiQueries.distinct().filter { it.isNotBlank() }
            Log.d(TAG, "Generated API search queries: $distinctSearchApiQueries")

            for (apiQuery in distinctSearchApiQueries) {
                Log.d(TAG, "Searching API with query: '$apiQuery'")
                var currentOffset = 0
                var hasMoreResults = true
                var accumulatedForThisApiQuery = 0

                while (hasMoreResults && accumulatedForThisApiQuery < maxFilesPerQueryType) {
                    val searchResult = repository.searchFiles(
                        query = apiQuery,
                        category = "video",
                        // ***** ZMENA TRIEDENIA NA null (API default - relevancia) *****
                        sort = null,
                        limit = seriesSearchLimitPerPage,
                        offset = currentOffset
                    )

                    if (searchResult.isSuccess) {
                        val response = searchResult.getOrNull()
                        response?.files?.let { files ->
                            if (files.isNotEmpty()) {
                                // ***** UPRAVENÉ PREDbežné FILTROVANIE *****
                                val relevantFiles = files.filter { file ->
                                    val normalizedFileName = file.name.lowercase()
                                        .replace(".", " ")
                                        .replace(Regex("[^a-zA-Z0-9\\s]"), "")
                                        .replace(Regex("\\s+"), " ").trim()
                                    // Súbor je relevantný, ak jeho normalizovaný názov obsahuje
                                    // normalizovaný PÔVODNÝ dopyt (po základnom očistení)
                                    normalizedFileName.contains(normalizedQueryForFiltering)
                                }
                                allFoundFilesSet.addAll(relevantFiles)
                                accumulatedForThisApiQuery += files.size
                                currentOffset += seriesSearchLimitPerPage
                                hasMoreResults = files.size == seriesSearchLimitPerPage && response.total > accumulatedForThisApiQuery
                                Log.d(TAG, "API Query '$apiQuery', offset $currentOffset: API returned ${files.size} (total API: ${response.total}). Filtered to ${relevantFiles.size}. Total unique so far: ${allFoundFilesSet.size}")
                            } else { hasMoreResults = false }
                        } ?: run { hasMoreResults = false }
                    } else {
                        Log.w(TAG, "API Search query '$apiQuery' failed: ${searchResult.exceptionOrNull()?.message}")
                        hasMoreResults = false
                    }
                    if (!hasMoreResults) break
                }
            }

            if (allFoundFilesSet.isEmpty()) {
                Log.d(TAG, "No files passed preliminary filter for any series query variations of: '$originalQuery'")
                _seriesOrganizationState.postValue(SeriesOrganizationState.Success(OrganizedSeries(title = originalQuery), emptyList()))
                return@launch
            }

            Log.d(TAG, "Found ${allFoundFilesSet.size} total unique potential files for series '$originalQuery'. Starting parsing with SeriesFileParser...")

            val organizedSeries = OrganizedSeries(title = originalQuery)
            val unclassifiedVideos = mutableListOf<FileModel>()

            allFoundFilesSet.forEach { fileModel ->
                val parsedInfo: ParsedEpisodeInfo? = SeriesFileParser.parseEpisodeInfo(fileModel.name, originalQuery)
                if (parsedInfo != null) {
                    val updatedFileModel = fileModel.copy(
                        seriesName = originalQuery,
                        seasonNumber = parsedInfo.seasonNumber,
                        episodeNumber = parsedInfo.episodeNumber,
                        videoQuality = parsedInfo.quality,
                        videoLanguage = parsedInfo.language,
                        episodeTitle = parsedInfo.remainingName
                    )
                    val season = organizedSeries.seasons.getOrPut(parsedInfo.seasonNumber) {
                        SeriesSeason(parsedInfo.seasonNumber)
                    }
                    season.addEpisodeFile(parsedInfo, updatedFileModel, originalQuery)
                } else {
                    val genericParsedInfo = SeriesFileParser.parseEpisodeInfo(fileModel.name, "")
                    unclassifiedVideos.add(fileModel.copy(
                        videoQuality = genericParsedInfo?.quality,
                        videoLanguage = genericParsedInfo?.language
                    ))
                    Log.d(TAG, "Added to unclassified videos (could not parse S/E): ${fileModel.name} (Q: ${genericParsedInfo?.quality}, L: ${genericParsedInfo?.language})")
                }
            }
            val distinctEpisodesCount = organizedSeries.seasons.values.sumOf { it.episodes.size }

            if (distinctEpisodesCount > 0 || unclassifiedVideos.isNotEmpty()) {
                Log.d(TAG, "Successfully processed files for '$originalQuery'. Distinct Episodes: $distinctEpisodesCount, Other Videos: ${unclassifiedVideos.size}.")
                _seriesOrganizationState.postValue(SeriesOrganizationState.Success(organizedSeries, unclassifiedVideos))
            } else {
                Log.d(TAG, "No episodes or other videos could be reliably parsed for '$originalQuery'.")
                _seriesOrganizationState.postValue(SeriesOrganizationState.Success(OrganizedSeries(title = originalQuery), emptyList()))
            }
        }
    }


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
