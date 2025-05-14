package com.example.wsplayer.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wsplayer.data.repository.WebshareRepository
import com.example.wsplayer.data.models.*
import com.example.wsplayer.utils.SeriesFileParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log
import java.util.regex.Pattern

sealed class HistoryState {
    object Idle : HistoryState()
    object Loading : HistoryState()
    data class Success(val items: List<HistoryItem>) : HistoryState()
    data class Error(val message: String) : HistoryState()
}

sealed class SeriesOrganizationState {
    object Idle : SeriesOrganizationState()
    object Loading : SeriesOrganizationState()
    data class Success(
        val series: OrganizedSeries,
        val otherVideos: List<FileModel>
    ) : SeriesOrganizationState()
    data class Error(val message: String) : SeriesOrganizationState()
}

class SearchViewModel(private val repository: WebshareRepository) : ViewModel() {

    private val TAG = "SearchViewModel"

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

    private var currentSearchQuery: String = ""
    private var currentSearchCategory: String? = null
    private var currentSortOrder: String? = null
    var currentPage: Int = 0
        private set
    private val resultsPerPage = 50
    private val seriesSearchLimitPerPage = 150
    private val maxFilesPerQueryType = 450

    init {
        Log.d(TAG, "Init blok spuštěn. PID: ${android.os.Process.myPid()}")
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Probíhá kontrola tokenu v init bloku.")
            val token = repository.getAuthToken()
            val isLoggedIn = (token != null && token.isNotEmpty())
            _isUserLoggedIn.postValue(isLoggedIn)
            Log.d(TAG, "Kontrola tokenu dokončena. isUserLoggedIn: $isLoggedIn")
        }
    }

    fun search(query: String, category: String?, sort: String? = null) {
        Log.d(TAG, "search() voláno s dotazem: '$query', kategorií: '$category', řazením: '$sort', limit: $resultsPerPage")
        if (query.isEmpty()) {
            _searchState.postValue(SearchState.Idle); _searchResults.postValue(emptyList()); _totalResults.postValue(0)
            Log.d(TAG, "Prázdný dotaz, vracím se do Idle."); return
        }
        if (isUserLoggedIn.value != true) {
            Log.e(TAG, "Pokus o vyhledávání bez platného tokenu."); _searchState.postValue(SearchState.Error("Pro vyhledávání je vyžadováno přihlášení."))
            _searchResults.postValue(emptyList()); _totalResults.postValue(0); return
        }
        if (_isLoading.value == true && _searchState.value is SearchState.Loading) {
            Log.d(TAG, "Již probíhá načítání (první stránka), přeskakuji nové vyhledávání."); return
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
                val errorMsg = result.exceptionOrNull()?.message ?: "Neznámá chyba"; _searchState.postValue(SearchState.Error(errorMsg))
                _searchResults.postValue(emptyList()); _totalResults.postValue(0)
            }
        }
    }

    fun loadNextPage() {
        Log.d(TAG, "loadNextPage() voláno.")
        if (isUserLoggedIn.value != true || currentSearchQuery.isEmpty() || _isLoading.value == true) {
            Log.d(TAG, "Přeskakuji loadNextPage."); return
        }
        val currentCount = _searchResults.value?.size ?: 0; val total = _totalResults.value ?: 0
        if (currentCount >= total && total > 0) { Log.d(TAG, "Žádné další stránky."); return }
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
                val errorMsg = result.exceptionOrNull()?.message ?: "Neznámá chyba při načítávání"; _searchState.postValue(SearchState.Error(errorMsg))
            }
        }
    }

    fun fetchHistory(limit: Int = 20) {
        Log.d(TAG, "fetchHistory() called with limit: $limit")
        if (isUserLoggedIn.value != true) {
            _historyState.postValue(HistoryState.Error("Pro zobrazení historie je vyžadováno přihlášení.")); return
        }
        if (_historyState.value is HistoryState.Loading) { Log.d(TAG, "History is already loading."); return }
        _historyState.postValue(HistoryState.Loading)
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.getHistory(offset = 0, limit = limit)
            if (result.isSuccess) {
                val response = result.getOrThrow()
                _historyState.postValue(HistoryState.Success(response.historyItems))
            } else {
                _historyState.postValue(HistoryState.Error(result.exceptionOrNull()?.message ?: "Neznámá chyba"))
            }
        }
    }

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
            val normalizedOriginalQueryForFilter = originalQuery.lowercase()
                .replace(Regex("""\s*\(\d{4}\)\s*"""), " ")
                .replace(Regex("""\b\d{4}\b"""), " ")
                .replace(Regex("[-._\\[\\]()]"), " ")
                .replace(Regex("\\s+"), " ").trim()
            Log.d(TAG, "Normalized original query for filtering: '$normalizedOriginalQueryForFilter'")

            val searchApiQueries = mutableListOf<String>()
            searchApiQueries.add(originalQuery)
            if (originalQuery.contains(" ")) searchApiQueries.add(originalQuery.replace(" ", "."))
            if (originalQuery.contains(".")) searchApiQueries.add(originalQuery.replace(".", " "))
            val yearPattern = Pattern.compile("""\s*\(\d{4}\)\s*|\s+\d{4}$""")
            val baseNameWithoutYear = yearPattern.matcher(originalQuery).replaceAll("").trim()
            if (baseNameWithoutYear.isNotBlank() && baseNameWithoutYear != originalQuery) {
                searchApiQueries.add(baseNameWithoutYear)
                if (baseNameWithoutYear.contains(" ")) searchApiQueries.add(baseNameWithoutYear.replace(" ", "."))
                if (baseNameWithoutYear.contains(".")) searchApiQueries.add(baseNameWithoutYear.replace(".", " "))
            }
            val targetForGenericQueries = if (baseNameWithoutYear.isNotBlank()) baseNameWithoutYear else originalQuery
            if (targetForGenericQueries.isNotBlank()) {
                if (!targetForGenericQueries.lowercase().contains("season") && !targetForGenericQueries.lowercase().contains("série")) {
                    searchApiQueries.add("$targetForGenericQueries season")
                    searchApiQueries.add("$targetForGenericQueries séria")
                }
            }

            val distinctSearchApiQueries = searchApiQueries.distinct().filter { it.isNotBlank() }
            Log.d(TAG, "Generated API search queries: $distinctSearchApiQueries")

            for (apiQuery in distinctSearchApiQueries) {
                Log.d(TAG, "Searching API with query: '$apiQuery'")
                var currentOffset = 0; var hasMoreResults = true; var accumulatedForThisApiQuery = 0
                while (hasMoreResults && accumulatedForThisApiQuery < maxFilesPerQueryType) {
                    val searchResult = repository.searchFiles(query = apiQuery, category = "video", sort = null, limit = seriesSearchLimitPerPage, offset = currentOffset)
                    if (searchResult.isSuccess) {
                        val response = searchResult.getOrNull()
                        response?.files?.let { files ->
                            if (files.isNotEmpty()) {
                                val relevantFiles = files.filter { file ->
                                    val normalizedFileName = file.name.lowercase().replace(".", " ").replace(Regex("[^a-zA-Z0-9\\s]"), "").replace(Regex("\\s+"), " ").trim()
                                    normalizedFileName.contains(normalizedOriginalQueryForFilter)
                                }
                                allFoundFilesSet.addAll(relevantFiles)
                                accumulatedForThisApiQuery += files.size; currentOffset += seriesSearchLimitPerPage
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
                _seriesOrganizationState.postValue(SeriesOrganizationState.Success(OrganizedSeries(title = originalQuery), emptyList())); return@launch
            }
            Log.d(TAG, "Found ${allFoundFilesSet.size} total unique potential files for series '$originalQuery'. Starting parsing...")
            val organizedSeries = OrganizedSeries(title = originalQuery)
            val unclassifiedVideos = mutableListOf<FileModel>()
            allFoundFilesSet.forEach { fileModel ->
                val parsedInfo: ParsedEpisodeInfo? = SeriesFileParser.parseEpisodeInfo(fileModel.name, originalQuery)
                if (parsedInfo != null) {
                    val updatedFileModel = fileModel.copy(seriesName=originalQuery, seasonNumber=parsedInfo.seasonNumber, episodeNumber=parsedInfo.episodeNumber, videoQuality=parsedInfo.quality, videoLanguage=parsedInfo.language, episodeTitle=parsedInfo.remainingName)
                    val season = organizedSeries.seasons.getOrPut(parsedInfo.seasonNumber) { SeriesSeason(parsedInfo.seasonNumber) }
                    season.addEpisodeFile(parsedInfo, updatedFileModel, originalQuery)
                } else {
                    val genericParsedInfo = SeriesFileParser.parseEpisodeInfo(fileModel.name, "")
                    unclassifiedVideos.add(fileModel.copy(videoQuality = genericParsedInfo?.quality, videoLanguage = genericParsedInfo?.language))
                }
            }
            val distinctEpisodesCount = organizedSeries.seasons.values.sumOf { it.episodes.size }
            if (distinctEpisodesCount > 0 || unclassifiedVideos.isNotEmpty()) {
                _seriesOrganizationState.postValue(SeriesOrganizationState.Success(organizedSeries, unclassifiedVideos))
            } else {
                _seriesOrganizationState.postValue(SeriesOrganizationState.Success(OrganizedSeries(title = originalQuery), emptyList()))
            }
        }
    }

    fun getFileLinkForFile(fileItem: FileModel) {
        // ***** PŘIDÁNO LOGOVÁNÍ NA ZAČÁTEK METODY *****
        Log.d(TAG, "getFileLinkForFile() called in ViewModel for ${fileItem.name} (ident: ${fileItem.ident}). Current FileLinkState: ${fileLinkState.value}")
        // *********************************************
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

        _fileLinkState.postValue(FileLinkState.LoadingLink) // Nastavení stavu načítání

        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Spouštím API volání pro získání odkazu pro soubor s ID: ${fileItem.ident}")
            val result = repository.getFileLink(fileItem.ident, filePassword = null)

            if (result.isSuccess) {
                val fileUrl = result.getOrThrow()
                Log.d(TAG, "API volání získání odkazu úspěšné. Odkaz získaný: $fileUrl")
                _fileLinkState.postValue(FileLinkState.LinkSuccess(fileUrl))
            } else {
                Log.e(TAG, "API volání získání odkazu zlyhalo: ${result.exceptionOrNull()?.message}")
                val errorMessage = result.exceptionOrNull()?.message ?: "Neznámá chyba při získávání odkazu"
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
        // ... (kód pro odhlášení - bez zmeny)
    }
}
