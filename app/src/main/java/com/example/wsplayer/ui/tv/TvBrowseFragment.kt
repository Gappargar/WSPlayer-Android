package com.example.wsplayer.ui.tv // Ujistěte se, že balíček odpovídá

import android.content.Intent // <-- Přidán import
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
// import androidx.fragment.app.viewModels // Pro by viewModels() - nahrazeno lazy inicializací s factory
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.ViewModelProvider
import com.example.wsplayer.R // Váš R soubor
import com.example.wsplayer.data.api.WebshareApiService
import com.example.wsplayer.data.models.FileLinkState // Import pro FileLinkState
import com.example.wsplayer.data.models.FileModel
import com.example.wsplayer.data.models.SearchState
import com.example.wsplayer.ui.player.PlayerActivity // Import pro PlayerActivity
import com.example.wsplayer.ui.search.SearchViewModel // Váš existující SearchViewModel
import com.example.wsplayer.ui.search.SearchViewModelFactory
import com.example.wsplayer.ui.tv.presenters.CardPresenter // Váš nový CardPresenter
// import com.example.wsplayer.ui.tv.TvSearchActivity // Import pro TvSearchActivity (pokud je ve stejném balíčku)

class TvBrowseFragment : BrowseSupportFragment() {

    private val TAG = "TvBrowseFragment"

    private val viewModel: SearchViewModel by lazy {
        val factory = SearchViewModelFactory(
            requireActivity().application,
            WebshareApiService.create()
        )
        ViewModelProvider(this, factory)[SearchViewModel::class.java]
    }

    private lateinit var rowsAdapter: ArrayObjectAdapter

    // Pomocná proměnná pro uchování aktuálního dotazu pro zobrazení v UI
    private var currentQuery: String = ""
    // Flag pro zajištění, že se počáteční vyhledávání spustí jen jednou
    private var initialSearchTriggered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        setupUIElements()
        setupEventListeners()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")
        observeViewModel()

        // Spustit až po potvrzení přihlášení
        viewModel.isUserLoggedIn.observe(viewLifecycleOwner) { isLoggedIn ->
            if (isLoggedIn == true && !initialSearchTriggered) {
                initialSearchTriggered = true
                triggerSearch("Star Trek")
                Log.d(TAG, "Initial search triggered for 'Star Trek' after login confirmed.")
            }
        }
    }


    private fun setupUIElements() {
        title = getString(R.string.app_name_tv)
        badgeDrawable = ContextCompat.getDrawable(requireActivity(), R.mipmap.ic_launcher_tv)

        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true

        val listRowPresenter = ListRowPresenter(FocusHighlight.ZOOM_FACTOR_MEDIUM)
        listRowPresenter.shadowEnabled = true
        listRowPresenter.selectEffectEnabled = true

        rowsAdapter = ArrayObjectAdapter(listRowPresenter)
        adapter = rowsAdapter

        // ***** ZMĚNA ZDE: Spuštění TvSearchActivity *****
        setOnSearchClickedListener {
            Log.d(TAG, "Search icon clicked, starting TvSearchActivity")
            // Vytvoření Intentu pro spuštění TvSearchActivity
            // Ujistěte se, že TvSearchActivity existuje a je ve správném balíčku
            val intent = Intent(activity, TvSearchActivity::class.java)
            startActivity(intent)
        }
    }

    private fun observeViewModel() {
        // Kód observerů zůstává stejný...
        Log.d(TAG, "Setting up observers for ViewModel.")

        viewModel.isUserLoggedIn.observe(viewLifecycleOwner) { isLoggedIn ->
            Log.d(TAG, "Observer isUserLoggedIn triggered. isLoggedIn: $isLoggedIn")
            // Spuštění počátečního vyhledávání se přesunulo do onViewCreated
        }

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
                    } else {
                        Log.w(TAG, "Search failed because user is not logged in (should have been handled earlier).")
                    }
                    displaySearchResults(emptyList())
                }
                is SearchState.EmptyResults -> {
                    Log.d(TAG, "Search state: EmptyResults")
                    if (currentQuery.isNotEmpty()) {
                        Toast.makeText(activity, getString(R.string.search_no_results_toast), Toast.LENGTH_SHORT).show()
                    }
                    displaySearchResults(emptyList())
                }
                is SearchState.Idle -> {
                    Log.d(TAG, "Search state: Idle")
                    if (currentQuery.isEmpty()) {
                        displaySearchResults(emptyList())
                    }
                }
            }
        }

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
                    } else {
                        Log.w(TAG, "Get link failed because user is not logged in.")
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
        // Kód pro zobrazení výsledků zůstává stejný...
        rowsAdapter.clear()

        if (results.isEmpty() && currentQuery.isNotEmpty()) {
            val header = HeaderItem(0, getString(R.string.search_no_results_tv))
            val messagePresenter = SingleTextViewPresenter()
            val messageAdapter = ArrayObjectAdapter(messagePresenter)
            messageAdapter.add(getString(R.string.search_no_results_for_query_tv, currentQuery))
            rowsAdapter.add(ListRow(header, messageAdapter))
            Log.d(TAG, "Displayed 'no results' message for query: $currentQuery")
            return
        } else if (results.isEmpty()) {
            Log.d(TAG, "No results to display (initial state or query was empty).")
            return
        }

        val cardPresenter = CardPresenter()
        val listRowAdapter = ArrayObjectAdapter(cardPresenter)

        results.forEach { file ->
            listRowAdapter.add(file)
        }

        val rowTitle = "Výsledky pro: '$currentQuery'"
        val header = HeaderItem(0, rowTitle)
        val listRow = ListRow(header, listRowAdapter)
        rowsAdapter.add(listRow)
        Log.d(TAG, "Displayed ${results.size} items in a row titled '$rowTitle'.")
    }


    private fun triggerSearch(query: String) {
        currentQuery = query
        Log.d(TAG, "Triggering search for query: $query with category 'video'")
        viewModel.search(query, "video", null)
    }


    private fun setupEventListeners() {
        // Kód listenerů zůstává stejný...
        onItemViewSelectedListener = OnItemViewSelectedListener { itemViewHolder, item, rowViewHolder, row ->
            if (item is FileModel) {
                Log.d(TAG, "Item selected: ${item.name}")
            }
        }

        onItemViewClickedListener = OnItemViewClickedListener { itemViewHolder, item, rowViewHolder, row ->
            if (item is FileModel) {
                Log.d(TAG, "Item clicked: ${item.name}")
                viewModel.getFileLinkForFile(item)
            }
        }
    }

    class SingleTextViewPresenter : Presenter() {
        // Kód presenteru zůstává stejný...
        override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
            val textView = TextView(parent.context).apply {
                isFocusable = false
                setPadding(32, 16, 32, 16)
                textSize = 18f
            }
            return Presenter.ViewHolder(textView)
        }

        override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
            (viewHolder.view as? TextView)?.text = item as? String ?: ""
        }

        override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder?) {
            // Nic
        }
    }
}
