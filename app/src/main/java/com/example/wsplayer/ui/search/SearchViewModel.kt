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
    private val seriesSearchLimitPerPage = 150 // Limit pre jeden dopyt pri hľadaní seriálu
    private val maxFilesPerQueryType = 450 // Zvýšený limit pre viac stránok na jeden typ dopytu


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
    }

    fun loadNextPage() {
        // ... (kód pre načítanie ďalšej stránky - bez zmeny)
    }

    fun fetchHistory(limit: Int = 20) {
        // ... (kód pre načítanie histórie - bez zmeny)
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

            // Normalizovaný pôvodný dopyt pre PREDbežné filtrovanie
            // Odstránime len rok a špeciálne znaky, ktoré nie sú súčasťou bežného názvu
            val normalizedOriginalQueryForFilter = originalQuery.lowercase()
                .replace(Regex("""\s*\(\d{4}\)\s*"""), " ") // Odstrániť rok v zátvorkách
                .replace(Regex("""\b\d{4}\b"""), " ")      // Odstrániť samostatný rok
                .replace(Regex("[-._\\[\\]()]"), " ")    // Nahradiť bežné oddeľovače medzerou
                .replace(Regex("\\s+"), " ").trim() // Normalizovať viacnásobné medzery
            Log.d(TAG, "Normalized original query for filtering: '$normalizedOriginalQueryForFilter'")


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
                // Tento dopyt môže byť príliš všeobecný, zvážiť jeho úpravu alebo odstránenie
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
                        sort = null, // ***** ZMENA TRIEDENIA NA null (API default - relevancia) *****
                        limit = seriesSearchLimitPerPage,
                        offset = currentOffset
                    )

                    if (searchResult.isSuccess) {
                        val response = searchResult.getOrNull()
                        response?.files?.let { files ->
                            if (files.isNotEmpty()) {
                                val relevantFiles = files.filter { file ->
                                    val normalizedFileNameForFilter = file.name.lowercase()
                                        .replace(Regex("[-._\\[\\]()]"), " ")
                                        .replace(Regex("\\s+"), " ").trim()
                                    // Súbor je relevantný, ak jeho normalizovaný názov obsahuje
                                    // normalizovaný PÔVODNÝ dopyt (po základnom očistení)
                                    val passes = normalizedFileNameForFilter.contains(normalizedOriginalQueryForFilter)
                                    // if (!passes) {
                                    //     Log.v(TAG, "File DISCARDED by preliminary filter: ${file.name} (normalized: $normalizedFileNameForFilter vs query: $normalizedOriginalQueryForFilter)")
                                    // }
                                    passes
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
    }

    fun resetFileLinkState() {
        // ... (kód pre reset - bez zmeny)
    }

    fun logout() {
        // ... (kód pre odhlásenie - bez zmeny)
    }
}
