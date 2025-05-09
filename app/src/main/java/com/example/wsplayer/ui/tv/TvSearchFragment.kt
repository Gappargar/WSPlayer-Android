package com.example.wsplayer.ui.tv // Ujistěte se, že balíček odpovídá

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.ViewModelProvider
import com.example.wsplayer.R // Váš R soubor
import com.example.wsplayer.data.api.WebshareApiService
import com.example.wsplayer.data.models.FileLinkState
import com.example.wsplayer.data.models.FileModel
import com.example.wsplayer.data.models.LoadMoreAction // <-- Import pro LoadMoreAction
import com.example.wsplayer.data.models.SearchState
// import com.example.wsplayer.ui.player.PlayerActivity // Už není potřeba
import com.example.wsplayer.ui.search.SearchViewModel
import com.example.wsplayer.ui.search.SearchViewModelFactory
import com.example.wsplayer.ui.tv.presenters.CardPresenter // Váš CardPresenter
import com.example.wsplayer.ui.tv.presenters.LoadMorePresenter // <-- Import pro LoadMorePresenter

/**
 * Fragment pro vyhledávání na Android TV pomocí Leanback SearchSupportFragment.
 */
class TvSearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val TAG = "TvSearchFragment"
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private val viewModel: SearchViewModel by lazy {
        val factory = SearchViewModelFactory(
            requireActivity().application,
            WebshareApiService.create()
        )
        ViewModelProvider(this, factory)[SearchViewModel::class.java]
    }

    // Handler a Runnable pro zpožděné vyhledávání (volitelné)
    private val handler = Handler(Looper.getMainLooper())
    private var searchQueryRunnable: Runnable? = null
    private val SEARCH_DELAY_MS = 500L

    // Adapter specificky pro řádek s výsledky (pro snadnější aktualizaci)
    private var resultsListRowAdapter: ArrayObjectAdapter? = null
    private var loadMoreRow: ListRow? = null // Reference na řádek "Načíst další"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        setupUI()
        setupResultsAdapter()
        observeViewModel()
        setSearchResultProvider(this) // Nastaví tento fragment jako poskytovatele výsledků
        setupEventListeners() // Nastavení listeneru pro kliknutí
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")
        // Observe viewmodel se volá zde, ale data se načtou až po akci uživatele
    }


    private fun setupUI() {
        // Můžete nastavit title nebo badge, pokud chcete
        // title = "Hledat videa"
        // badgeDrawable = ContextCompat.getDrawable(requireContext(), R.mipmap.ic_launcher_tv)
    }

    private fun setupResultsAdapter() {
        // Hlavní adapter pro všechny řádky
        val listRowPresenter = ListRowPresenter(FocusHighlight.ZOOM_FACTOR_MEDIUM)
        rowsAdapter = ArrayObjectAdapter(listRowPresenter)
        // Adapter se nastavuje implementací SearchResultProvider a getResultsAdapter()
    }

    private fun observeViewModel() {
        Log.d(TAG, "Setting up observers for ViewModel.")

        // Pozorování stavu vyhledávání
        viewModel.searchState.observe(viewLifecycleOwner) { state ->
            // Zobrazit/skrýt progress bar nebo jiný indikátor načítání
            // progressBar?.visibility = if (state is SearchState.Loading) View.VISIBLE else View.GONE

            when (state) {
                is SearchState.Loading -> {
                    Log.d(TAG, "Search state: Loading")
                }
                is SearchState.LoadingMore -> {
                    Log.d(TAG, "Search state: LoadingMore")
                }
                is SearchState.Success -> {
                    Log.d(TAG, "Search state: Success - ${state.results.size} files loaded, total: ${state.totalResults}")
                    // Rozlišit, zda jde o první načtení nebo přidání další stránky
                    if (resultsListRowAdapter == null) { // Předpokládáme, že null znamená první načtení/nové hledání
                        // První načtení nebo nové vyhledávání
                        displaySearchResults(state.results, state.totalResults)
                    } else {
                        // Přidání dalších výsledků k existujícím
                        appendSearchResults(state.results, state.totalResults)
                    }
                }
                is SearchState.Error -> {
                    Log.e(TAG, "Search state: Error - ${state.message}")
                    if (!state.message.contains("přihlášení", ignoreCase = true)) {
                        Toast.makeText(activity, getString(R.string.search_error_toast, state.message), Toast.LENGTH_LONG).show()
                    }
                    displaySearchResults(emptyList(), 0) // Zobrazit prázdný stav/chybu
                }
                is SearchState.EmptyResults -> {
                    Log.d(TAG, "Search state: EmptyResults")
                    displaySearchResults(emptyList(), 0) // Zobrazit zprávu "žádné výsledky"
                }
                is SearchState.Idle -> {
                    Log.d(TAG, "Search state: Idle")
                    // Můžeme vyčistit výsledky nebo zobrazit úvodní zprávu
                    displaySearchResults(emptyList(), 0)
                }
            }
        }

        // Pozorování stavu odkazu pro přehrávání (beze změny)
        viewModel.fileLinkState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is FileLinkState.LoadingLink -> {
                    Log.d(TAG, "FileLinkState: LoadingLink")
                    Toast.makeText(activity, getString(R.string.getting_link_toast), Toast.LENGTH_SHORT).show()
                }
                is FileLinkState.LinkSuccess -> {
                    Log.d(TAG, "FileLinkState: LinkSuccess - URL: ${state.fileUrl}")
                    if (state.fileUrl.isNotEmpty()) {
                        val playIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(state.fileUrl), "video/*")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        Log.d(TAG, "Attempting to start player with ACTION_VIEW for URL: ${state.fileUrl}")
                        if (playIntent.resolveActivity(requireActivity().packageManager) != null) {
                            startActivity(playIntent)
                        } else {
                            Log.e(TAG, "No activity found to handle ACTION_VIEW for video.")
                            Toast.makeText(activity, getString(R.string.no_video_player_app_toast), Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Log.e(TAG, "FileLinkState: LinkSuccess but URL is empty")
                        Toast.makeText(activity, getString(R.string.empty_link_error_toast), Toast.LENGTH_LONG).show()
                    }
                    viewModel.resetFileLinkState()
                }
                is FileLinkState.Error -> {
                    Log.e(TAG, "FileLinkState: Error - ${state.message}")
                    if (!state.message.contains("přihlášení", ignoreCase = true)) {
                        Toast.makeText(activity, getString(R.string.link_error_toast, state.message), Toast.LENGTH_LONG).show()
                    }
                    viewModel.resetFileLinkState()
                }
                is FileLinkState.Idle -> {
                    Log.d(TAG, "FileLinkState: Idle")
                }
            }
        }
    }

    /**
     * Zobrazí první sadu výsledků vyhledávání.
     */
    private fun displaySearchResults(results: List<FileModel>, totalResults: Int) {
        rowsAdapter.clear() // Vyčistit všechny předchozí řádky
        resultsListRowAdapter = null // Resetovat adapter pro výsledky
        loadMoreRow = null // Resetovat řádek pro načtení další

        if (results.isEmpty()) {
            // Zobrazit zprávu, pokud nejsou výsledky
            val header = HeaderItem(0, getString(R.string.search_results_header))
            val messagePresenter = TvBrowseFragment.SingleTextViewPresenter()
            val messageAdapter = ArrayObjectAdapter(messagePresenter)
            messageAdapter.add(getString(R.string.search_no_results_for_query))
            rowsAdapter.add(ListRow(header, messageAdapter))
            Log.d(TAG, "Displayed 'no results' message.")
        } else {
            // Zobrazit výsledky v jednom řádku
            val cardPresenter = CardPresenter()
            resultsListRowAdapter = ArrayObjectAdapter(cardPresenter) // Uložit si referenci
            results.forEach { resultsListRowAdapter?.add(it) }
            val header = HeaderItem(0, getString(R.string.search_results_header))
            rowsAdapter.add(ListRow(header, resultsListRowAdapter))
            Log.d(TAG, "Displayed ${results.size} search results.")

            // Zkontrolovat, zda přidat řádek "Načíst další"
            checkAndAddLoadMoreRow(results.size, totalResults)
        }
    }

    /**
     * Přidá další výsledky k existujícímu řádku.
     */
    private fun appendSearchResults(newResults: List<FileModel>, totalResults: Int) {
        if (resultsListRowAdapter == null) {
            Log.w(TAG, "resultsListRowAdapter is null in appendSearchResults, displaying all results again.")
            displaySearchResults(newResults, totalResults)
            return
        }
        // Přidat nové položky do existujícího adapteru
        newResults.forEach { resultsListRowAdapter?.add(it) }
        Log.d(TAG, "Appended ${newResults.size} search results. Total items now: ${resultsListRowAdapter?.size()}")

        // Odstranit starý řádek "Načíst další", pokud existoval
        // ***** OPRAVA ZDE: Explicitní non-null proměnná *****
        loadMoreRow?.let { rowToRemove ->
            rowsAdapter.remove(rowToRemove) // Použití non-null proměnné
        }
        loadMoreRow = null

        // Zkontrolovat, zda přidat nový řádek "Načíst další"
        checkAndAddLoadMoreRow(resultsListRowAdapter?.size() ?: 0, totalResults)
    }

    /**
     * Zkontroluje, zda je potřeba zobrazit řádek "Načíst další" a přidá ho.
     */
    private fun checkAndAddLoadMoreRow(currentCount: Int, totalCount: Int) {
        if (currentCount > 0 && currentCount < totalCount) {
            Log.d(TAG, "Adding 'Load More' row. Current: $currentCount, Total: $totalCount")
            val loadMoreAdapter = ArrayObjectAdapter(LoadMorePresenter())
            loadMoreAdapter.add(LoadMoreAction)
            val loadMoreHeader = HeaderItem(-1L, null)
            loadMoreRow = ListRow(loadMoreHeader, loadMoreAdapter)
            rowsAdapter.add(loadMoreRow)
        } else {
            Log.d(TAG, "Not adding 'Load More' row. Current: $currentCount, Total: $totalCount")
        }
    }


    // --- SearchResultProvider implementace --- (beze změny)
    override fun onQueryTextChange(newQuery: String?): Boolean {
        Log.d(TAG, "onQueryTextChange: $newQuery")
        searchQueryRunnable?.let { handler.removeCallbacks(it) }

        val query = newQuery?.trim() ?: ""
        if (query.length < 2 && query.isNotEmpty()) {
            rowsAdapter.clear()
            resultsListRowAdapter = null
            loadMoreRow = null
            return true
        }

        if (query.isEmpty()) {
            rowsAdapter.clear()
            resultsListRowAdapter = null
            loadMoreRow = null
            return true
        }

        searchQueryRunnable = Runnable { performSearch(query) }
        handler.postDelayed(searchQueryRunnable!!, SEARCH_DELAY_MS)

        return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        Log.d(TAG, "onQueryTextSubmit: $query")
        searchQueryRunnable?.let { handler.removeCallbacks(it) }

        val finalQuery = query?.trim() ?: ""
        if (finalQuery.isNotEmpty()) {
            performSearch(finalQuery)
        } else {
            rowsAdapter.clear()
            resultsListRowAdapter = null
            loadMoreRow = null
        }
        return true
    }

    private fun performSearch(query: String) {
        Log.d(TAG, "Performing search for: $query")
        resultsListRowAdapter = null // Resetovat adapter před novým hledáním
        loadMoreRow = null
        viewModel.search(query, "video", null)
    }


    override fun getResultsAdapter(): ObjectAdapter {
        Log.d(TAG, "getResultsAdapter called")
        return rowsAdapter
    }

    // --- Listener pro kliknutí na výsledek vyhledávání ---
    private fun setupEventListeners() {
        setOnItemViewClickedListener { itemViewHolder, item, rowViewHolder, row ->
            when (item) {
                is FileModel -> { // Kliknuto na soubor
                    Log.d(TAG, "Search result clicked: ${item.name}")
                    viewModel.getFileLinkForFile(item)
                }
                is LoadMoreAction -> { // Kliknuto na "Načíst další"
                    Log.d(TAG, "'Load More' clicked.")
                    // Odstranit řádek "Načíst další", zobrazit loading a zavolat ViewModel
                    // ***** OPRAVA ZDE: Explicitní non-null proměnná *****
                    loadMoreRow?.let { rowToRemove ->
                        rowsAdapter.remove(rowToRemove) // Použití non-null proměnné
                        loadMoreRow = null // Zabráníme dvojitému kliknutí
                        // Zde můžete zobrazit indikátor načítání
                        Toast.makeText(activity, "Načítám další...", Toast.LENGTH_SHORT).show()
                        viewModel.loadNextPage()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        searchQueryRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroyView()
        Log.d(TAG, "onDestroyView")
    }
}
