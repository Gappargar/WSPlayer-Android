package com.example.wsplayer.ui.tv // Ujistěte se, že balíček odpovídá

import android.app.Activity // Potřeba pro REQUEST_SPEECH
import android.content.ActivityNotFoundException // Potřeba pro speech
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent // Potřeba pro speech
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
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
import com.example.wsplayer.data.models.LoadMoreAction
import com.example.wsplayer.data.models.SearchState
import com.example.wsplayer.ui.search.SearchViewModel
import com.example.wsplayer.ui.search.SearchViewModelFactory
import com.example.wsplayer.ui.tv.presenters.CardPresenter
import com.example.wsplayer.ui.tv.presenters.LoadMorePresenter
// Import pro Leanback R, pokud ho používáte pro ID (např. lb_search_text_editor)
import androidx.leanback.R as LeanbackR

class TvSearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val TAG = "TvSearchFragment"

    interface OnFileSelectedInSearchListener {
        fun onFileSelectedInSearch(file: FileModel?)
    }
    private var fileSelectedListener: OnFileSelectedInSearchListener? = null

    private lateinit var rowsAdapter: ArrayObjectAdapter
    private val viewModel: SearchViewModel by lazy {
        val factory = SearchViewModelFactory(
            requireActivity().application,
            WebshareApiService.create()
        )
        ViewModelProvider(this, factory)[SearchViewModel::class.java]
    }

    private val handler = Handler(Looper.getMainLooper())
    private var searchQueryRunnable: Runnable? = null
    private val SEARCH_DELAY_MS = 500L

    private var resultsListRowAdapter: ArrayObjectAdapter? = null
    private var loadMoreRow: ListRow? = null

    private val REQUEST_SPEECH = 0x00000010 // Kód pro rozpoznávání řeči

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFileSelectedInSearchListener) {
            fileSelectedListener = context
            Log.d(TAG, "OnFileSelectedInSearchListener attached to activity.")
        } else {
            Log.e(TAG, "$context must implement OnFileSelectedInSearchListener for detail view to work.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        setupUI()
        setupResultsAdapter()
        setSearchResultProvider(this)
        setupEventListeners()
        // ODSTRANĚNO: setSpeechRecognitionCallback(null) nebo jakákoli jiná explicitní manipulace
        // Spoléháme na výchozí chování SearchSupportFragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")
        observeViewModel()

        // Pokus o zobrazení klávesnice a nastavení fokusu
        view.post {
            val searchEditText = view.findViewById<EditText>(LeanbackR.id.lb_search_text_editor)
            if (searchEditText != null && isAdded) { // Kontrola isAdded
                searchEditText.requestFocus()
                val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                val success = imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
                Log.d(TAG, "Attempted to show keyboard. Success: $success")
            } else {
                Log.w(TAG, "Search EditText (lb_search_text_editor) not found or fragment not added.")
            }
        }
    }

    // ODSTRANĚNA metoda setupSpeechRecognition()

    private fun setupUI() {
        title = getString(R.string.search_title_tv)
        // SearchSupportFragment by měl sám zobrazit ikonu mikrofonu (search orb),
        // pokud je rozpoznávání řeči dostupné.
        // Pokud bychom chtěli explicitně zakázat hlasové vyhledávání (i ikonu):
        // permissionsDelegate = null // Nebo vlastní implementace, která vrací false pro RECORD_AUDIO
    }

    private fun setupResultsAdapter() {
        val listRowPresenter = ListRowPresenter(FocusHighlight.ZOOM_FACTOR_MEDIUM)
        rowsAdapter = ArrayObjectAdapter(listRowPresenter)
    }

    private fun observeViewModel() {
        Log.d(TAG, "Setting up observers for ViewModel.")
        viewModel.searchState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is SearchState.Loading, is SearchState.LoadingMore -> {
                    Log.d(TAG, "Search state: Loading/LoadingMore")
                    fileSelectedListener?.onFileSelectedInSearch(null)
                }
                is SearchState.Success -> {
                    Log.d(TAG, "Search state: Success - ${state.results.size} files loaded, total: ${state.totalResults}")
                    if (resultsListRowAdapter == null) {
                        displaySearchResults(state.results, state.totalResults)
                    } else {
                        appendSearchResults(state.results, state.totalResults)
                    }
                    if (state.results.isEmpty()) {
                        fileSelectedListener?.onFileSelectedInSearch(null)
                    }
                }
                is SearchState.Error -> {
                    Log.e(TAG, "Search state: Error - ${state.message}")
                    if (!state.message.contains("přihlášení", ignoreCase = true)) {
                        Toast.makeText(activity, getString(R.string.search_error_toast, state.message), Toast.LENGTH_LONG).show()
                    }
                    displaySearchResults(emptyList(), 0)
                    fileSelectedListener?.onFileSelectedInSearch(null)
                }
                is SearchState.EmptyResults -> {
                    Log.d(TAG, "Search state: EmptyResults")
                    displaySearchResults(emptyList(), 0)
                    fileSelectedListener?.onFileSelectedInSearch(null)
                }
                is SearchState.Idle -> {
                    Log.d(TAG, "Search state: Idle")
                    displaySearchResults(emptyList(), 0)
                    fileSelectedListener?.onFileSelectedInSearch(null)
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

    private fun displaySearchResults(results: List<FileModel>, totalResults: Int) {
        rowsAdapter.clear()
        resultsListRowAdapter = null
        loadMoreRow = null

        if (results.isEmpty()) {
            val header = HeaderItem(0, getString(R.string.search_results_header))
            val messagePresenter = TvBrowseFragment.SingleTextViewPresenter()
            val messageAdapter = ArrayObjectAdapter(messagePresenter)
            messageAdapter.add(getString(R.string.search_no_results_for_query))
            rowsAdapter.add(ListRow(header, messageAdapter))
            Log.d(TAG, "Displayed 'no results' message.")
            fileSelectedListener?.onFileSelectedInSearch(null)
        } else {
            val cardPresenter = CardPresenter()
            resultsListRowAdapter = ArrayObjectAdapter(cardPresenter)
            results.forEach { resultsListRowAdapter?.add(it) }
            val header = HeaderItem(0, getString(R.string.search_results_header))
            rowsAdapter.add(ListRow(header, resultsListRowAdapter))
            Log.d(TAG, "Displayed ${results.size} search results.")
            checkAndAddLoadMoreRow(results.size, totalResults)
            if (results.isNotEmpty()) {
                // Detaily se aktualizují přes onItemViewSelectedListener
            } else {
                fileSelectedListener?.onFileSelectedInSearch(null)
            }
        }
    }

    private fun appendSearchResults(newResults: List<FileModel>, totalResults: Int) {
        if (resultsListRowAdapter == null) {
            Log.w(TAG, "resultsListRowAdapter is null in appendSearchResults, displaying all results again.")
            displaySearchResults(newResults, totalResults)
            return
        }
        newResults.forEach { resultsListRowAdapter?.add(it) }
        Log.d(TAG, "Appended ${newResults.size} search results. Total items now: ${resultsListRowAdapter?.size()}")

        loadMoreRow?.let { rowToRemove ->
            rowsAdapter.remove(rowToRemove)
        }
        loadMoreRow = null
        checkAndAddLoadMoreRow(resultsListRowAdapter?.size() ?: 0, totalResults)
    }

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


    override fun onQueryTextChange(newQuery: String?): Boolean {
        Log.d(TAG, "onQueryTextChange: $newQuery")
        searchQueryRunnable?.let { handler.removeCallbacks(it) }

        val query = newQuery?.trim() ?: ""
        if (query.length < 2 && query.isNotEmpty()) {
            rowsAdapter.clear()
            resultsListRowAdapter = null
            loadMoreRow = null
            fileSelectedListener?.onFileSelectedInSearch(null)
            return true
        }

        if (query.isEmpty()) {
            rowsAdapter.clear()
            resultsListRowAdapter = null
            loadMoreRow = null
            fileSelectedListener?.onFileSelectedInSearch(null)
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
            fileSelectedListener?.onFileSelectedInSearch(null)
        }
        return true
    }

    private fun performSearch(query: String) {
        Log.d(TAG, "Performing search for: $query")
        resultsListRowAdapter = null
        loadMoreRow = null
        fileSelectedListener?.onFileSelectedInSearch(null)
        viewModel.search(query, "video", null)
    }


    override fun getResultsAdapter(): ObjectAdapter {
        Log.d(TAG, "getResultsAdapter called")
        return rowsAdapter
    }

    private fun setupEventListeners() {
        setOnItemViewClickedListener { itemViewHolder, item, rowViewHolder, row ->
            when (item) {
                is FileModel -> {
                    Log.d(TAG, "Search result clicked: ${item.name}")
                    viewModel.getFileLinkForFile(item)
                }
                is LoadMoreAction -> {
                    Log.d(TAG, "'Load More' clicked.")
                    loadMoreRow?.let { rowToRemove ->
                        rowsAdapter.remove(rowToRemove)
                        loadMoreRow = null
                        Toast.makeText(activity, "Načítám další...", Toast.LENGTH_SHORT).show()
                        viewModel.loadNextPage()
                    }
                }
            }
        }
        setOnItemViewSelectedListener { itemViewHolder, item, rowViewHolder, row ->
            if (item is FileModel) {
                Log.d(TAG,"Selected FileModel in Search: ${item.name}")
                fileSelectedListener?.onFileSelectedInSearch(item)
            } else if (item !is LoadMoreAction) {
                fileSelectedListener?.onFileSelectedInSearch(null)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "onActivityResult requestCode=$requestCode resultCode=$resultCode")
        if (requestCode == REQUEST_SPEECH && resultCode == Activity.RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val recognizedText = results[0]
                Log.d(TAG, "Voice search recognized: $recognizedText")
                // SearchSupportFragment by měl automaticky nastavit text do vyhledávacího pole
                // a zavolat onQueryTextSubmit nebo onQueryTextChange.
                // Pokud ne, můžeme to udělat explicitně:
                setSearchQuery(recognizedText, true) // true pro odeslání dotazu
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroyView() {
        searchQueryRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroyView()
        Log.d(TAG, "onDestroyView")
    }
}
