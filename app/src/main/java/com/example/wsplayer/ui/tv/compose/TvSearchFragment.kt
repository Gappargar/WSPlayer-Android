package com.example.wsplayer.ui.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.* // Import pro SearchBar a SearchOrbView
import androidx.lifecycle.ViewModelProvider
import com.example.wsplayer.R // Váš R soubor
import com.example.wsplayer.data.api.WebshareApiService
import com.example.wsplayer.data.models.FileLinkState
import com.example.wsplayer.data.models.FileModel
import com.example.wsplayer.data.models.LoadMoreAction
// Explicitní importy pro datové modely seriálů
import com.example.wsplayer.data.models.OrganizedSeries
import com.example.wsplayer.data.models.SeriesSeason
import com.example.wsplayer.data.models.SeriesEpisode
import com.example.wsplayer.data.models.SeriesEpisodeFile
import com.example.wsplayer.ui.search.SearchViewModel
import com.example.wsplayer.ui.search.SearchViewModelFactory
// Import pre SeriesOrganizationState z SearchViewModel
import com.example.wsplayer.ui.search.SeriesOrganizationState
// Import pro náš nový Compose dialog
import com.example.wsplayer.ui.tv.compose.ComposeEpisodeSelectionDialogFragment
import com.example.wsplayer.ui.tv.presenters.CardPresenter
import com.example.wsplayer.ui.tv.presenters.LoadMorePresenter
// Import pre Leanback R, ak ho používate pre ID (napr. lb_search_text_editor, lb_search_bar, lb_search_orb)
import androidx.leanback.R as LeanbackR

class TvSearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider,
    ComposeEpisodeSelectionDialogFragment.OnEpisodeFileSelectedListener { // ***** IMPLEMENTACE NOVÉHO LISTENERU *****

    private val TAG = "TvSearchFragment"

    // Listener pro komunikaci s aktivitou ohledně detailů vybraného souboru
    interface OnFileSelectedInSearchListener {
        fun onFileSelectedInSearch(file: FileModel?)
    }
    private var activityFileSelectedListener: OnFileSelectedInSearchListener? = null

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

    private var currentOrganizedSeries: OrganizedSeries? = null
    private var currentOtherVideos: List<FileModel>? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFileSelectedInSearchListener) {
            activityFileSelectedListener = context
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
        setSearchResultProvider(this)
        setupEventListeners()

        setSpeechRecognitionCallback(null)
        Log.d(TAG, "Fragment's SpeechRecognitionCallback set to null in onCreate.")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")
        observeViewModel()

        val searchBarWidget = view.findViewById<SearchBar>(LeanbackR.id.lb_search_bar)
        if (searchBarWidget != null) {
            val searchEditText = searchBarWidget.findViewById<EditText>(LeanbackR.id.lb_search_text_editor)
            searchEditText?.hint = getString(R.string.search_hint_tv_series)
            Log.d(TAG, "Search EditText hint set.")

            searchBarWidget.setSpeechRecognitionCallback(null)
            Log.d(TAG, "SearchBar widget's SpeechRecognitionCallback set to null.")

            val searchOrbView = searchBarWidget.findViewById<SearchOrbView>(LeanbackR.id.lb_search_bar_speech_orb)
            searchOrbView?.visibility = View.GONE
            searchOrbView?.isFocusable = false
            searchOrbView?.isClickable = false
            Log.d(TAG, "SearchOrbView visibility set to GONE, focusable and clickable to false.")
        } else {
            Log.e(TAG, "SearchBar widget (lb_search_bar) not found in view!")
            val transparent = ContextCompat.getColor(requireContext(), android.R.color.transparent)
            setSearchAffordanceColors(SearchOrbView.Colors(transparent, transparent, transparent))
        }

        view.post {
            val searchEditText = view.findViewById<EditText>(LeanbackR.id.lb_search_text_editor)
            if (searchEditText != null && isAdded) {
                searchEditText.requestFocus()
                val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
                Log.d(TAG, "Attempted to show keyboard.")

                searchEditText.setOnEditorActionListener { v, actionId, event ->
                    if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                        actionId == EditorInfo.IME_ACTION_DONE ||
                        (event != null && event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                        val queryText = v.text.toString()
                        Log.d(TAG, "Explicit OnEditorActionListener: Search action triggered. Query: $queryText")
                        this@TvSearchFragment.onQueryTextSubmit(queryText)
                        hideKeyboard(v)
                        true
                    } else {
                        false
                    }
                }
            } else {
                Log.w(TAG, "Search EditText (lb_search_text_editor) not found or fragment not added for keyboard/listener.")
            }
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun startRecognition() {
        Log.w(TAG, "startRecognition() called, but overridden to do nothing to completely prevent voice input.")
    }

    private fun setupResultsAdapter() {
        val listRowPresenter = ListRowPresenter(FocusHighlight.ZOOM_FACTOR_MEDIUM)
        listRowPresenter.shadowEnabled = true
        listRowPresenter.selectEffectEnabled = true
        rowsAdapter = ArrayObjectAdapter(listRowPresenter)
    }

    private fun observeViewModel() {
        Log.d(TAG, "Setting up observers for ViewModel.")
        viewModel.seriesOrganizationState.observe(viewLifecycleOwner) { state ->
            Log.d(TAG, "SeriesOrganizationState changed: $state")
            when (state) {
                is SeriesOrganizationState.Loading -> {
                    Log.d(TAG, "SeriesOrganizationState: Loading")
                    rowsAdapter.clear()
                    currentOrganizedSeries = null
                    currentOtherVideos = null
                    val header = HeaderItem(0, "Zpracovávám...")
                    val messagePresenter = TvBrowseFragment.SingleTextViewPresenter()
                    val messageAdapter = ArrayObjectAdapter(messagePresenter)
                    messageAdapter.add("Vyhledávám a organizuji epizody, prosím čekejte...")
                    rowsAdapter.add(ListRow(header, messageAdapter))
                    activityFileSelectedListener?.onFileSelectedInSearch(null)
                }
                is SeriesOrganizationState.Success -> {
                    Log.d(TAG, "SeriesOrganizationState: Success - Organized series: ${state.series.title}, Other videos: ${state.otherVideos.size}")
                    currentOrganizedSeries = state.series
                    currentOtherVideos = state.otherVideos
                    displayOrganizedResults(state.series, state.otherVideos)
                }
                is SeriesOrganizationState.Error -> {
                    Log.e(TAG, "SeriesOrganizationState: Error - ${state.message}")
                    currentOrganizedSeries = null
                    currentOtherVideos = null
                    displayErrorMessage(state.message)
                    activityFileSelectedListener?.onFileSelectedInSearch(null)
                }
                is SeriesOrganizationState.Idle -> {
                    Log.d(TAG, "SeriesOrganizationState: Idle")
                    currentOrganizedSeries = null
                    currentOtherVideos = null
                    displayInitialSearchMessage()
                    activityFileSelectedListener?.onFileSelectedInSearch(null)
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

    private fun displayInitialSearchMessage() {
        rowsAdapter.clear()
        currentOrganizedSeries = null
        currentOtherVideos = null
        val header = HeaderItem(0, getString(R.string.search_title_tv))
        val messagePresenter = TvBrowseFragment.SingleTextViewPresenter()
        val messageAdapter = ArrayObjectAdapter(messagePresenter)
        messageAdapter.add(getString(R.string.search_series_prompt))
        rowsAdapter.add(ListRow(header, messageAdapter))
        Log.d(TAG, "Displayed initial search message.")
    }

    private fun displayErrorMessage(message: String) {
        rowsAdapter.clear()
        currentOrganizedSeries = null
        currentOtherVideos = null
        val header = HeaderItem(0, getString(R.string.error_title))
        val messagePresenter = TvBrowseFragment.SingleTextViewPresenter()
        val messageAdapter = ArrayObjectAdapter(messagePresenter)
        messageAdapter.add(message)
        rowsAdapter.add(ListRow(header, messageAdapter))
        Log.d(TAG, "Displayed error message: $message")
    }

    private fun displayOrganizedResults(series: OrganizedSeries, otherVideos: List<FileModel>) {
        rowsAdapter.clear()
        Log.d(TAG, "Displaying organized results for: ${series.title}, Other videos count: ${otherVideos.size}")

        var hasContentDisplayed = false
        val cardPresenter = CardPresenter()
        var headerIdCounter = 0L

        if (series.seasons.isNotEmpty()) {
            series.getSortedSeasons().forEach { season ->
                if (season.episodes.isNotEmpty()) {
                    val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                    season.getSortedEpisodes().forEach { episode ->
                        // Zobrazíme první soubor jako reprezentanta epizody.
                        // FileModel na kartě bude mít informace z tohoto prvního souboru.
                        // Skutečný FileModel pro přehrání se vybere po kliknutí.
                        episode.files.firstOrNull()?.let { seriesEpisodeFile ->
                            // Do FileModelu na kartě můžeme přidat informaci, že má více verzí
                            // Např. přidáním "(+)" k názvu, pokud episode.files.size > 1
                            val displayFileModel = seriesEpisodeFile.fileModel.copy()
                            if (episode.files.size > 1) {
                                // Toto je jen vizuální indikace, můžeme ji vylepšit v CardPresenter
                                // displayFileModel.name = "${displayFileModel.name} (+ ${episode.files.size -1} další)"
                            }
                            listRowAdapter.add(displayFileModel)
                        }
                    }
                    if (listRowAdapter.size() > 0) {
                        val header = HeaderItem(headerIdCounter++, "Série ${season.seasonNumber} (${series.title})")
                        rowsAdapter.add(ListRow(header, listRowAdapter))
                        hasContentDisplayed = true
                    }
                }
            }
        }

        if (otherVideos.isNotEmpty()) {
            val moviesHeader = HeaderItem(headerIdCounter++, "Filmy a jiné video")
            val moviesListAdapter = ArrayObjectAdapter(cardPresenter)
            otherVideos.forEach { fileModel ->
                moviesListAdapter.add(fileModel)
            }
            rowsAdapter.add(ListRow(moviesHeader, moviesListAdapter))
            hasContentDisplayed = true
        }

        if (!hasContentDisplayed) {
            displayErrorMessage(getString(R.string.series_no_episodes_found_for, series.title))
        } else {
            activityFileSelectedListener?.onFileSelectedInSearch(null)
        }
    }


    override fun onQueryTextChange(newQuery: String?): Boolean {
        Log.d(TAG, "onQueryTextChange: $newQuery")
        if (newQuery.isNullOrEmpty()) {
            rowsAdapter.clear()
            displayInitialSearchMessage()
            activityFileSelectedListener?.onFileSelectedInSearch(null)
        }
        return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        Log.d(TAG, "onQueryTextSubmit: $query")
        val finalQuery = query?.trim() ?: ""
        if (finalQuery.isNotEmpty()) {
            performSeriesSearch(finalQuery)
        } else {
            rowsAdapter.clear()
            displayInitialSearchMessage()
            activityFileSelectedListener?.onFileSelectedInSearch(null)
        }
        return true
    }

    private fun performSeriesSearch(query: String) {
        Log.d(TAG, "Performing series search and organization for: $query")
        currentOrganizedSeries = null
        currentOtherVideos = null
        activityFileSelectedListener?.onFileSelectedInSearch(null)
        viewModel.searchAndOrganizeSeries(query)
    }


    override fun getResultsAdapter(): ObjectAdapter {
        Log.d(TAG, "getResultsAdapter called")
        return rowsAdapter
    }

    private fun setupEventListeners() {
        setOnItemViewClickedListener { itemViewHolder, item, rowViewHolder, row ->
            if (item is FileModel) {
                Log.d(TAG, "Item clicked: ${item.name} (ident: ${item.ident})")

                val clickedSeriesEpisode = findSeriesEpisodeForFileModel(item)

                if (clickedSeriesEpisode != null) {
                    // Je to epizoda seriálu
                    if (clickedSeriesEpisode.files.size > 1) {
                        Log.d(TAG, "Episode '${item.episodeTitle ?: item.name}' (S${clickedSeriesEpisode.seasonNumber}E${clickedSeriesEpisode.episodeNumber}) has ${clickedSeriesEpisode.files.size} files.")
                        // Zobrazení Compose dialogu pro výběr souboru
                        val dialog = ComposeEpisodeSelectionDialogFragment.newInstance(
                            clickedSeriesEpisode.files, // Seznam SeriesEpisodeFile
                            "S${clickedSeriesEpisode.seasonNumber}E${clickedSeriesEpisode.episodeNumber}: ${clickedSeriesEpisode.commonEpisodeTitle ?: item.name}"
                        )
                        // Nastavení tohoto fragmentu jako cíle pro callback z dialogu
                        dialog.setTargetFragment(this@TvSearchFragment, 0) // REQUEST_CODE je 0, nepotřebujeme ho rozlišovat
                        parentFragmentManager.let { dialog.show(it, "ComposeEpisodeFileSelectionDialog") }

                    } else if (clickedSeriesEpisode.files.isNotEmpty()) {
                        // Jen jeden soubor pro epizodu
                        Log.d(TAG, "Episode '${item.name}' has 1 file. Playing directly.")
                        viewModel.getFileLinkForFile(clickedSeriesEpisode.files.first().fileModel)
                    } else {
                        Log.w(TAG, "Episode '${item.name}' has no files associated.")
                        Toast.makeText(activity, "Pro tuto epizodu nebyly nalezeny žádné soubory.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Je to pravděpodobně film (z otherVideos)
                    Log.d(TAG, "Clicked on a movie/other video: ${item.name}")
                    viewModel.getFileLinkForFile(item)
                }
            }
        }
        setOnItemViewSelectedListener { itemViewHolder, item, rowViewHolder, row ->
            if (item is FileModel) {
                Log.d(TAG,"Selected FileModel in Search: ${item.name}")
                activityFileSelectedListener?.onFileSelectedInSearch(item)
            } else {
                activityFileSelectedListener?.onFileSelectedInSearch(null)
            }
        }
    }

    private fun findSeriesEpisodeForFileModel(clickedFileModel: FileModel): SeriesEpisode? {
        val seasonNum = clickedFileModel.seasonNumber
        val episodeNum = clickedFileModel.episodeNumber

        if (seasonNum != null && episodeNum != null) {
            return currentOrganizedSeries?.seasons?.get(seasonNum)?.episodes?.get(episodeNum)
        }
        return null
    }

    // ***** IMPLEMENTACE OnEpisodeFileSelectedListener z Compose DIALOGU *****
    override fun onEpisodeFileSelected(selectedFileModel: FileModel) {
        Log.d(TAG, "File selected from Compose dialog: ${selectedFileModel.name}")
        viewModel.getFileLinkForFile(selectedFileModel)
    }
    // *******************************************************************

    override fun onDestroyView() {
        searchQueryRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroyView()
        Log.d(TAG, "onDestroyView")
    }

    // Presenter pre zobrazenie textovej správy
    class SingleTextViewPresenter : Presenter() {
        override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
            val textView = TextView(parent.context).apply {
                isFocusable = false
                setPadding(32, 16, 32, 16)
                textSize = 18f
            }
            return ViewHolder(textView)
        }
        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
            (viewHolder.view as? TextView)?.text = item as? String ?: ""
        }
        override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
    }
}
