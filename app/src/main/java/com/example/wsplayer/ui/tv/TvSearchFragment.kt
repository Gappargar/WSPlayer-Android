package com.example.wsplayer.ui.tv // Uistite sa, že balíček zodpovedá

import android.app.Activity // Potreba pre REQUEST_SPEECH
import android.content.ActivityNotFoundException // Potreba pre speech
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent // Potreba pre speech
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.* // Import pre SearchBar
import androidx.lifecycle.ViewModelProvider
import com.example.wsplayer.R // Váš R súbor
import com.example.wsplayer.data.api.WebshareApiService
import com.example.wsplayer.data.models.FileLinkState
import com.example.wsplayer.data.models.FileModel
import com.example.wsplayer.data.models.LoadMoreAction // Aj keď ho teraz nepoužívame, necháme pre budúcnosť
import com.example.wsplayer.data.models.SearchState // Tento stav už priamo nepoužívame pre zobrazenie
// Explicitné importy pre dátové modely seriálov
import com.example.wsplayer.data.models.OrganizedSeries
import com.example.wsplayer.data.models.SeriesSeason
import com.example.wsplayer.data.models.SeriesEpisode
import com.example.wsplayer.ui.search.SearchViewModel
import com.example.wsplayer.ui.search.SearchViewModelFactory
// Import pre SeriesOrganizationState z SearchViewModel
import com.example.wsplayer.ui.search.SeriesOrganizationState
import com.example.wsplayer.ui.tv.presenters.CardPresenter
import com.example.wsplayer.ui.tv.presenters.LoadMorePresenter
// Import pre Leanback R, ak ho používate pre ID (napr. lb_search_text_editor)
import androidx.leanback.R as LeanbackR

class TvSearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val TAG = "TvSearchFragment"

    interface OnFileSelectedInSearchListener {
        fun onFileSelectedInSearch(file: FileModel?)
    }
    private var fileSelectedListener: OnFileSelectedInSearchListener? = null

    private lateinit var rowsAdapter: ArrayObjectAdapter // Hlavný adapter pre všetky riadky
    private val viewModel: SearchViewModel by lazy {
        val factory = SearchViewModelFactory(
            requireActivity().application,
            WebshareApiService.create()
        )
        ViewModelProvider(this, factory)[SearchViewModel::class.java]
    }

    private val handler = Handler(Looper.getMainLooper())
    private var searchQueryRunnable: Runnable? = null
    private val SEARCH_DELAY_MS = 500L // Oneskorenie pre onQueryTextChange

    // Tieto už nebudeme priamo používať pre výsledky seriálov, rowsAdapter bude obsahovať ListRow pre každú sériu
    // private var resultsListRowAdapter: ArrayObjectAdapter? = null
    // private var loadMoreRow: ListRow? = null

    private val REQUEST_SPEECH = 0x00000010 // Kód pre rozpoznávanie reči

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

        title = getString(R.string.search_title_tv)

        setupResultsAdapter()
        setSearchResultProvider(this) // Tento fragment poskytuje výsledky pre SearchSupportFragment
        setupEventListeners()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")
        observeViewModel()


        view.post {
            val searchEditText = view.findViewById<EditText>(LeanbackR.id.lb_search_text_editor)
            if (searchEditText != null && isAdded) {
                searchEditText.requestFocus()
                val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                val success = imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
                Log.d(TAG, "Attempted to show keyboard. Success: $success")
            } else {
                Log.w(TAG, "Search EditText (lb_search_text_editor) not found or fragment not added.")
            }
        }
    }

    private fun setupResultsAdapter() {
        val listRowPresenter = ListRowPresenter(FocusHighlight.ZOOM_FACTOR_MEDIUM)
        listRowPresenter.shadowEnabled = true // Zapnutie tieňov pre lepší vzhľad kariet
        listRowPresenter.selectEffectEnabled = true // Efekt pri výbere karty
        rowsAdapter = ArrayObjectAdapter(listRowPresenter)
        // Adapter pre SearchSupportFragment sa nastavuje cez getResultsAdapter()
    }

    private fun observeViewModel() {
        Log.d(TAG, "Setting up observers for ViewModel.")

        // Pozorovanie stavu organizácie seriálu
        viewModel.seriesOrganizationState.observe(viewLifecycleOwner) { state ->
            Log.d(TAG, "SeriesOrganizationState changed: $state")
            when (state) {
                is SeriesOrganizationState.Loading -> {
                    Log.d(TAG, "SeriesOrganizationState: Loading")
                    rowsAdapter.clear() // Vyčistiť predchádzajúce výsledky/správy
                    val header = HeaderItem(0, "Spracovávam...")
                    val messagePresenter = TvBrowseFragment.SingleTextViewPresenter()
                    val messageAdapter = ArrayObjectAdapter(messagePresenter)
                    messageAdapter.add("Vyhľadávam a organizujem epizódy, prosím čakajte...")
                    rowsAdapter.add(ListRow(header, messageAdapter))
                    fileSelectedListener?.onFileSelectedInSearch(null)
                }
                is SeriesOrganizationState.Success -> {
                    Log.d(TAG, "SeriesOrganizationState: Success - Organized series: ${state.series.title}, Other videos: ${state.otherVideos.size}")
                    displayOrganizedResults(state.series, state.otherVideos)
                }
                is SeriesOrganizationState.Error -> {
                    Log.e(TAG, "SeriesOrganizationState: Error - ${state.message}")
                    displayErrorMessage(state.message)
                    fileSelectedListener?.onFileSelectedInSearch(null)
                }
                is SeriesOrganizationState.NoEpisodesFound -> { // Tento stav je teraz nahradený Success s prázdnymi zoznamami
                    Log.d(TAG, "SeriesOrganizationState: NoEpisodesFound (should be Success with empty lists)")
                    displayErrorMessage(getString(R.string.series_no_episodes_found))
                    fileSelectedListener?.onFileSelectedInSearch(null)
                }
                is SeriesOrganizationState.Idle -> {
                    Log.d(TAG, "SeriesOrganizationState: Idle")
                    displayInitialSearchMessage()
                    fileSelectedListener?.onFileSelectedInSearch(null)
                }
            }
        }

        // Pozorovanie stavu odkazu pre prehrávanie
        viewModel.fileLinkState.observe(viewLifecycleOwner) { state ->
            // ... (kód pre fileLinkState zostáva rovnaký)
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

    private fun displayInitialSearchMessage() {
        rowsAdapter.clear()
        val header = HeaderItem(0, getString(R.string.search_title_tv))
        val messagePresenter = TvBrowseFragment.SingleTextViewPresenter() // Znovu použijeme presenter z TvBrowseFragment
        val messageAdapter = ArrayObjectAdapter(messagePresenter)
        messageAdapter.add(getString(R.string.search_series_prompt))
        rowsAdapter.add(ListRow(header, messageAdapter))
        Log.d(TAG, "Displayed initial search message.")
    }

    private fun displayErrorMessage(message: String) {
        rowsAdapter.clear()
        val header = HeaderItem(0, getString(R.string.error_title))
        val messagePresenter = TvBrowseFragment.SingleTextViewPresenter()
        val messageAdapter = ArrayObjectAdapter(messagePresenter)
        messageAdapter.add(message)
        rowsAdapter.add(ListRow(header, messageAdapter))
        Log.d(TAG, "Displayed error message: $message")
    }

    /**
     * Zobrazí usporiadaný seriál (série) a prípadne ďalšie videá (filmy).
     */
    private fun displayOrganizedResults(series: OrganizedSeries, otherVideos: List<FileModel>) {
        rowsAdapter.clear()
        Log.d(TAG, "Displaying organized results for: ${series.title}, Other videos count: ${otherVideos.size}")

        var hasContentDisplayed = false
        val cardPresenter = CardPresenter()
        var headerIdCounter = 0L // Pre unikátne ID hlavičiek

        // Zobrazenie sérií
        if (series.seasons.isNotEmpty()) {
            series.getSortedSeasons().forEach { season ->
                if (season.episodes.isNotEmpty()) { // Zobraziť sériu len ak má epizódy
                    val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                    season.episodes.forEach { episode ->
                        // fileModel v SeriesEpisode by už mal mať nastavené seriesName, seasonNumber atď.
                        listRowAdapter.add(episode.fileModel)
                    }
                    val header = HeaderItem(headerIdCounter++, "Séria ${season.seasonNumber} (${series.title})")
                    rowsAdapter.add(ListRow(header, listRowAdapter))
                    hasContentDisplayed = true
                }
            }
        }

        // Zobrazenie ostatných videí (filmov)
        if (otherVideos.isNotEmpty()) {
            val moviesHeader = HeaderItem(headerIdCounter++, "Filmy a iné videá")
            val moviesListAdapter = ArrayObjectAdapter(cardPresenter)
            otherVideos.forEach { fileModel ->
                // Tu môžeme FileModel priamo pridať, ak už má vyplnené potrebné polia
                // alebo ho upraviť, ak je to nutné pre CardPresenter
                moviesListAdapter.add(fileModel)
            }
            rowsAdapter.add(ListRow(moviesHeader, moviesListAdapter))
            hasContentDisplayed = true
        }

        if (!hasContentDisplayed) {
            // Ak sa nenašli ani série, ani iné videá, ale vyhľadávanie bolo úspešné
            displayErrorMessage(getString(R.string.series_no_episodes_found_for, series.title))
        } else {
            fileSelectedListener?.onFileSelectedInSearch(null) // Vyčistiť detaily na začiatku
        }
    }


    override fun onQueryTextChange(newQuery: String?): Boolean {
        Log.d(TAG, "onQueryTextChange: $newQuery")
        // Pre seriály zvyčajne chceme vyhľadávať až po potvrdení (Submit),
        // aby sme zbytočne nezaťažovali API počas písania.
        // Ak by sme chceli návrhy, tu by bola iná logika.
        // Ak je text prázdny, môžeme vyčistiť výsledky.
        if (newQuery.isNullOrEmpty()) {
            rowsAdapter.clear()
            displayInitialSearchMessage()
            fileSelectedListener?.onFileSelectedInSearch(null)
        }
        return true // True znamená, že sme zmenu spracovali
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        Log.d(TAG, "onQueryTextSubmit: $query")
        val finalQuery = query?.trim() ?: ""
        if (finalQuery.isNotEmpty()) {
            performSeriesSearch(finalQuery)
        } else {
            rowsAdapter.clear()
            displayInitialSearchMessage()
            fileSelectedListener?.onFileSelectedInSearch(null)
        }
        return true
    }

    private fun performSeriesSearch(query: String) {
        Log.d(TAG, "Performing series search and organization for: $query")
        fileSelectedListener?.onFileSelectedInSearch(null) // Vyčistiť detaily pred novým hľadaním
        viewModel.searchAndOrganizeSeries(query) // Volanie novej metódy vo ViewModeli
    }


    override fun getResultsAdapter(): ObjectAdapter {
        Log.d(TAG, "getResultsAdapter called")
        return rowsAdapter
    }

    private fun setupEventListeners() {
        setOnItemViewClickedListener { itemViewHolder, item, rowViewHolder, row ->
            // Kliknutie na LoadMoreAction tu už neriešime,
            // pretože stránkovanie pre seriály je teraz riešené inak (alebo zatiaľ nie je)
            if (item is FileModel) {
                Log.d(TAG, "Episode or Movie clicked: ${item.name}")
                viewModel.getFileLinkForFile(item)
            }
        }
        setOnItemViewSelectedListener { itemViewHolder, item, rowViewHolder, row ->
            if (item is FileModel) {
                Log.d(TAG,"Selected FileModel in Search: ${item.name}")
                fileSelectedListener?.onFileSelectedInSearch(item)
            } else { // Ak je vybraná hlavička alebo iný typ položky
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
                setSearchQuery(recognizedText, true) // Nastaví text a odošle (zavolá onQueryTextSubmit)
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
