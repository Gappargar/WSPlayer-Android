package com.example.wsplayer.ui.tv // Ujistěte se, že balíček odpovídá

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View // <-- Přidán import pro View
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
import com.example.wsplayer.data.models.SearchState
import com.example.wsplayer.ui.player.PlayerActivity
import com.example.wsplayer.ui.search.SearchViewModel
import com.example.wsplayer.ui.search.SearchViewModelFactory
import com.example.wsplayer.ui.tv.presenters.CardPresenter // Váš CardPresenter

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
    private val SEARCH_DELAY_MS = 500L // Zpoždění v ms před spuštěním hledání po změně textu

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        setupUI()
        setupResultsAdapter()
        // NEVOLAT observeViewModel() ZDE
        setSearchResultProvider(this) // Nastaví tento fragment jako poskytovatele výsledků
        setupEventListeners() // Nastavení listeneru pro kliknutí
    }

    // ***** Přesunuto pozorování ViewModelu do onViewCreated *****
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")
        observeViewModel() // Volání observerů až zde
    }


    private fun setupUI() {
        // Můžete nastavit title nebo badge, pokud chcete
        // title = "Hledat videa"
        // badgeDrawable = ContextCompat.getDrawable(requireContext(), R.mipmap.ic_launcher_tv)
    }

    private fun setupResultsAdapter() {
        // Vytvoření adapteru pro zobrazení výsledků vyhledávání
        val listRowPresenter = ListRowPresenter(FocusHighlight.ZOOM_FACTOR_MEDIUM)
        rowsAdapter = ArrayObjectAdapter(listRowPresenter)
        // Adapter se nastavuje implementací SearchResultProvider a getResultsAdapter()
    }

    private fun observeViewModel() {
        Log.d(TAG, "Setting up observers for ViewModel.")

        // Pozorování stavu vyhledávání
        viewModel.searchState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is SearchState.Loading, is SearchState.LoadingMore -> {
                    Log.d(TAG, "Search state: Loading/LoadingMore")
                }
                is SearchState.Success -> {
                    Log.d(TAG, "Search state: Success - ${state.results.size} files loaded, total: ${state.totalResults}")
                    displaySearchResults(state.results)
                }
                is SearchState.Error -> {
                    Log.e(TAG, "Search state: Error - ${state.message}")
                    if (!state.message.contains("přihlášení", ignoreCase = true)) {
                        Toast.makeText(activity, getString(R.string.search_error_toast, state.message), Toast.LENGTH_LONG).show()
                    }
                    displaySearchResults(emptyList())
                }
                is SearchState.EmptyResults -> {
                    Log.d(TAG, "Search state: EmptyResults")
                    displaySearchResults(emptyList())
                }
                is SearchState.Idle -> {
                    Log.d(TAG, "Search state: Idle")
                    displaySearchResults(emptyList())
                }
            }
        }

        // Pozorování stavu odkazu pro přehrávání
        viewModel.fileLinkState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is FileLinkState.LoadingLink -> {
                    Log.d(TAG, "FileLinkState: LoadingLink")
                    Toast.makeText(activity, getString(R.string.getting_link_toast), Toast.LENGTH_SHORT).show()
                }
                is FileLinkState.LinkSuccess -> {
                    Log.d(TAG, "FileLinkState: LinkSuccess - URL: ${state.fileUrl}")
                    if (state.fileUrl.isNotEmpty()) {
                        val intent = Intent(activity, PlayerActivity::class.java).apply {
                            putExtra(PlayerActivity.EXTRA_VIDEO_URL, state.fileUrl)
                        }
                        startActivity(intent)
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

    private fun displaySearchResults(results: List<FileModel>) {
        rowsAdapter.clear() // Vyčistit předchozí výsledky

        if (results.isEmpty()) {
            // Zobrazit zprávu, pokud nejsou výsledky
            val header = HeaderItem(0, getString(R.string.search_results_header))
            // Ujistěte se, že TvBrowseFragment.SingleTextViewPresenter je přístupný nebo ho přesuňte
            val messagePresenter = TvBrowseFragment.SingleTextViewPresenter()
            val messageAdapter = ArrayObjectAdapter(messagePresenter)
            messageAdapter.add(getString(R.string.search_no_results_for_query))
            rowsAdapter.add(ListRow(header, messageAdapter))
            Log.d(TAG, "Displayed 'no results' message.")
        } else {
            // Zobrazit výsledky v jednom řádku
            val cardPresenter = CardPresenter()
            val listRowAdapter = ArrayObjectAdapter(cardPresenter)
            results.forEach { listRowAdapter.add(it) }
            val header = HeaderItem(0, getString(R.string.search_results_header))
            rowsAdapter.add(ListRow(header, listRowAdapter))
            Log.d(TAG, "Displayed ${results.size} search results.")
        }
    }


    // --- SearchResultProvider implementace ---

    override fun onQueryTextChange(newQuery: String?): Boolean {
        Log.d(TAG, "onQueryTextChange: $newQuery")
        searchQueryRunnable?.let { handler.removeCallbacks(it) }

        val query = newQuery?.trim() ?: ""
        if (query.length < 2 && query.isNotEmpty()) {
            rowsAdapter.clear()
            return true
        }

        if (query.isEmpty()) {
            rowsAdapter.clear()
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
        }
        return true
    }

    private fun performSearch(query: String) {
        Log.d(TAG, "Performing search for: $query")
        viewModel.search(query, "video", null)
    }


    override fun getResultsAdapter(): ObjectAdapter {
        Log.d(TAG, "getResultsAdapter called")
        return rowsAdapter
    }

    // --- Listener pro kliknutí na výsledek vyhledávání ---
    private fun setupEventListeners() {
        setOnItemViewClickedListener { itemViewHolder, item, rowViewHolder, row ->
            if (item is FileModel) {
                Log.d(TAG, "Search result clicked: ${item.name}")
                viewModel.getFileLinkForFile(item)
            }
        }
    }

    override fun onDestroyView() {
        searchQueryRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroyView()
        Log.d(TAG, "onDestroyView") // Přidáno logování pro ověření
    }
}
