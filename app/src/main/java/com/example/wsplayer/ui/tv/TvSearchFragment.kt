package com.example.wsplayer.ui.tv // Uistite sa, že balíček zodpovedá

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.* // Import pre SearchBar a SearchOrbView
import androidx.lifecycle.ViewModelProvider
import com.example.wsplayer.R // Váš R súbor
import com.example.wsplayer.data.api.WebshareApiService
import com.example.wsplayer.data.models.FileLinkState
import com.example.wsplayer.data.models.FileModel
import com.example.wsplayer.data.models.LoadMoreAction
// Explicitné importy pre dátové modely seriálov
import com.example.wsplayer.data.models.OrganizedSeries
import com.example.wsplayer.data.models.SeriesSeason
import com.example.wsplayer.data.models.SeriesEpisode
import com.example.wsplayer.data.models.SeriesEpisodeFile
import com.example.wsplayer.ui.search.SearchViewModel
import com.example.wsplayer.ui.search.SearchViewModelFactory
// Import pre SeriesOrganizationState z SearchViewModel
import com.example.wsplayer.ui.search.SeriesOrganizationState
import com.example.wsplayer.ui.tv.presenters.CardPresenter
import com.example.wsplayer.ui.tv.presenters.LoadMorePresenter
// Import pre Leanback R, ak ho používate pre ID (napr. lb_search_text_editor, lb_search_bar)
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

    private var currentOrganizedSeries: OrganizedSeries? = null
    private var currentOtherVideos: List<FileModel>? = null

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
        setSearchResultProvider(this)
        setupEventListeners()

        // Definitívne zakázanie hlasového vyhľadávania pre fragment
        setSpeechRecognitionCallback(null)
        Log.d(TAG, "Fragment's SpeechRecognitionCallback set to null in onCreate.")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")
        observeViewModel()

        // Nastavenie farieb "search orb" (ikony mikrofónu) na úplne priehľadné,
        // čím sa efektívne skryje a deaktivuje. Voláme na fragmente.
        val transparent = ContextCompat.getColor(requireContext(), android.R.color.transparent)
        setSearchAffordanceColors(SearchOrbView.Colors(transparent, transparent, transparent))
        Log.d(TAG, "Fragment's SearchAffordanceColors set to transparent.")

        // Nastavenie hintu priamo na internom EditTexte
        val searchEditText = view.findViewById<EditText>(LeanbackR.id.lb_search_text_editor)
        if (searchEditText != null) {
            searchEditText.hint = getString(R.string.search_hint_tv_series)
            Log.d(TAG, "Search EditText hint set.")
        } else {
            Log.w(TAG, "Search EditText (lb_search_text_editor) not found for hint setting.")
        }

        // Pokus o zobrazenie klávesnice a nastavenie fokusu na EditText
        view.post {
            if (searchEditText != null && isAdded) {
                searchEditText.requestFocus()
                val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                val success = imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
                Log.d(TAG, "Attempted to show keyboard. Success: $success")
            } else {
                Log.w(TAG, "Could not request focus or show keyboard: EditText is null or fragment not added.")
            }
        }
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
                    val header = HeaderItem(0, "Spracovávam...")
                    val messagePresenter = TvBrowseFragment.SingleTextViewPresenter()
                    val messageAdapter = ArrayObjectAdapter(messagePresenter)
                    messageAdapter.add("Vyhľadávam a organizujem epizódy, prosím čakajte...")
                    rowsAdapter.add(ListRow(header, messageAdapter))
                    fileSelectedListener?.onFileSelectedInSearch(null)
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
                    fileSelectedListener?.onFileSelectedInSearch(null)
                }
                is SeriesOrganizationState.Idle -> {
                    Log.d(TAG, "SeriesOrganizationState: Idle")
                    currentOrganizedSeries = null
                    currentOtherVideos = null
                    displayInitialSearchMessage()
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
                        episode.files.firstOrNull()?.let { seriesEpisodeFile ->
                            listRowAdapter.add(seriesEpisodeFile.fileModel)
                        }
                    }
                    if (listRowAdapter.size() > 0) {
                        val header = HeaderItem(headerIdCounter++, "Séria ${season.seasonNumber} (${series.title})")
                        rowsAdapter.add(ListRow(header, listRowAdapter))
                        hasContentDisplayed = true
                    }
                }
            }
        }

        if (otherVideos.isNotEmpty()) {
            val moviesHeader = HeaderItem(headerIdCounter++, "Filmy a iné videá")
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
            fileSelectedListener?.onFileSelectedInSearch(null)
        }
    }


    override fun onQueryTextChange(newQuery: String?): Boolean {
        Log.d(TAG, "onQueryTextChange: $newQuery")
        if (newQuery.isNullOrEmpty()) {
            rowsAdapter.clear()
            displayInitialSearchMessage()
            fileSelectedListener?.onFileSelectedInSearch(null)
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
            fileSelectedListener?.onFileSelectedInSearch(null)
        }
        return true
    }

    private fun performSeriesSearch(query: String) {
        Log.d(TAG, "Performing series search and organization for: $query")
        currentOrganizedSeries = null
        currentOtherVideos = null
        fileSelectedListener?.onFileSelectedInSearch(null)
        viewModel.searchAndOrganizeSeries(query)
    }


    override fun getResultsAdapter(): ObjectAdapter {
        Log.d(TAG, "getResultsAdapter called")
        return rowsAdapter
    }

    private fun setupEventListeners() {
        setOnItemViewClickedListener { itemViewHolder, item, rowViewHolder, row ->
            if (item is FileModel) {
                Log.d(TAG, "Episode or Movie clicked: ${item.name}")
                val clickedSeriesEpisode = findSeriesEpisodeForFileModel(item)
                if (clickedSeriesEpisode != null) {
                    if (clickedSeriesEpisode.files.size > 1) {
                        Log.d(TAG, "Episode '${item.name}' has ${clickedSeriesEpisode.files.size} files. Showing selection dialog.")
                        Toast.makeText(activity, "Výber kvality pre '${item.name}' (TODO)", Toast.LENGTH_LONG).show()
                        clickedSeriesEpisode.files.firstOrNull()?.let {
                            viewModel.getFileLinkForFile(it.fileModel)
                        }
                    } else if (clickedSeriesEpisode.files.isNotEmpty()) {
                        Log.d(TAG, "Episode '${item.name}' has 1 file. Playing directly.")
                        viewModel.getFileLinkForFile(clickedSeriesEpisode.files.first().fileModel)
                    } else {
                        Log.w(TAG, "Episode '${item.name}' has no files associated.")
                        Toast.makeText(activity, "Pre túto epizódu neboli nájdené žiadne súbory.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.d(TAG, "Clicked on a movie/other video: ${item.name}")
                    viewModel.getFileLinkForFile(item)
                }
            }
        }
        setOnItemViewSelectedListener { itemViewHolder, item, rowViewHolder, row ->
            if (item is FileModel) {
                Log.d(TAG,"Selected FileModel in Search: ${item.name}")
                fileSelectedListener?.onFileSelectedInSearch(item)
            } else {
                fileSelectedListener?.onFileSelectedInSearch(null)
            }
        }
    }

    private fun findSeriesEpisodeForFileModel(clickedFileModel: FileModel): SeriesEpisode? {
        currentOrganizedSeries?.seasons?.values?.forEach { season ->
            season.episodes.values.forEach { episode ->
                if (episode.files.any { it.fileModel.ident == clickedFileModel.ident }) {
                    return episode
                }
            }
        }
        return null
    }

    // Odstránená metóda onActivityResult, keďže hlasové vyhľadávanie je zakázané

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
