package com.example.wsplayer.ui.tv.compose // Předpokládaný balíček dle chyby

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.leanback.app.RowsSupportFragment // Předpokládáme, že stále dědí z RowsSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.ViewModelProvider
import com.example.wsplayer.R
import com.example.wsplayer.data.api.WebshareApiService
import com.example.wsplayer.data.models.FileModel
import com.example.wsplayer.data.models.OrganizedSeries
import com.example.wsplayer.data.models.SeriesSeason
import com.example.wsplayer.data.models.SeriesEpisode
import com.example.wsplayer.data.models.SeriesEpisodeFile
import com.example.wsplayer.ui.search.SearchViewModel
import com.example.wsplayer.ui.search.SearchViewModelFactory
import com.example.wsplayer.ui.tv.TvBrowseFragment // Pro SingleTextViewPresenter
import com.example.wsplayer.ui.tv.presenters.CardPresenter

// Ujistěte se, že cesta k tomuto souboru je správná, pokud jste ho přejmenovali/přesunuli
// import com.example.wsplayer.ui.tv.compose.ComposeEpisodeSelectionDialogFragment

class TvSearchFragment : RowsSupportFragment(), // Název třídy dle chyby
    ComposeEpisodeSelectionDialogFragment.OnEpisodeFileSelectedListener {

    private val TAG = "TvSearchFragmentCompose" // Upravený TAG pro odlišení

    private lateinit var viewModel: SearchViewModel
    private var activityListener: OnFileSelectedListener? = null

    private var currentOrganizedSeries: OrganizedSeries? = null
    private var currentOtherVideos: List<FileModel>? = null

    interface OnFileSelectedListener {
        fun onFileSelectedInResults(file: FileModel?)
    }

    companion object {
        private const val ARG_SERIALIZED_SERIES = "arg_serialized_series"
        private const val ARG_SERIALIZED_OTHER_VIDEOS = "arg_serialized_other_videos"
        private const val ARG_SHOW_INITIAL_MESSAGE = "arg_show_initial_message"
        private const val ARG_ERROR_MESSAGE = "arg_error_message"

        fun newInstance(
            series: OrganizedSeries?,
            otherVideos: List<FileModel>?,
            showInitialMessage: Boolean = false,
            errorMessage: String? = null
        ): TvSearchFragment { // Vrací novou instanci TvSearchFragment
            val fragment = TvSearchFragment()
            val args = Bundle()
            if (series != null) args.putParcelable(ARG_SERIALIZED_SERIES, series)
            if (otherVideos != null) args.putParcelableArrayList(ARG_SERIALIZED_OTHER_VIDEOS, ArrayList(otherVideos))
            args.putBoolean(ARG_SHOW_INITIAL_MESSAGE, showInitialMessage)
            args.putString(ARG_ERROR_MESSAGE, errorMessage)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFileSelectedListener) {
            activityListener = context
        } else {
            Log.e(TAG, "$context must implement TvSearchFragment.OnFileSelectedListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[SearchViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")

        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter(FocusHighlight.ZOOM_FACTOR_MEDIUM))
        adapter = rowsAdapter

        currentOrganizedSeries = arguments?.getParcelable(ARG_SERIALIZED_SERIES)
        currentOtherVideos = arguments?.getParcelableArrayList(ARG_SERIALIZED_OTHER_VIDEOS)
        val showInitial = arguments?.getBoolean(ARG_SHOW_INITIAL_MESSAGE, false) ?: false
        val errorMsg = arguments?.getString(ARG_ERROR_MESSAGE)

        when {
            errorMsg != null -> displayErrorMessageInFragment(errorMsg, rowsAdapter)
            showInitial -> displayInitialSearchMessageInFragment(rowsAdapter)
            currentOrganizedSeries != null || !currentOtherVideos.isNullOrEmpty() -> {
                displayOrganizedResultsInFragment(
                    currentOrganizedSeries ?: OrganizedSeries("Neznámý seriál"),
                    currentOtherVideos ?: emptyList(),
                    rowsAdapter
                )
            }
            else -> {
                displayInitialSearchMessageInFragment(rowsAdapter)
            }
        }
        setupEventListeners()
        // observeFileLinkState() // Přesunuto do CustomTvSearchActivity
    }

    private fun displayInitialSearchMessageInFragment(rowsAdapter: ArrayObjectAdapter) {
        rowsAdapter.clear()
        val header = HeaderItem(0, getString(R.string.search_results_header))
        val messagePresenter = TvBrowseFragment.SingleTextViewPresenter()
        val messageAdapter = ArrayObjectAdapter(messagePresenter)
        messageAdapter.add(getString(R.string.search_series_prompt))
        rowsAdapter.add(ListRow(header, messageAdapter))
        activityListener?.onFileSelectedInResults(null)
    }

    private fun displayErrorMessageInFragment(message: String, rowsAdapter: ArrayObjectAdapter) {
        rowsAdapter.clear()
        val header = HeaderItem(0, getString(R.string.error_title))
        val messagePresenter = TvBrowseFragment.SingleTextViewPresenter()
        val messageAdapter = ArrayObjectAdapter(messagePresenter)
        messageAdapter.add(message)
        rowsAdapter.add(ListRow(header, messageAdapter))
        activityListener?.onFileSelectedInResults(null)
    }

    private fun displayOrganizedResultsInFragment(
        series: OrganizedSeries,
        otherVideos: List<FileModel>,
        rowsAdapter: ArrayObjectAdapter
    ) {
        rowsAdapter.clear()
        var hasContent = false
        val cardPresenter = CardPresenter()
        var headerIdCounter = 0L

        if (series.seasons.isNotEmpty()) {
            series.getSortedSeasons().forEach { season: SeriesSeason ->
                if (season.episodes.isNotEmpty()) {
                    val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                    season.getSortedEpisodes().forEach { episode: SeriesEpisode ->
                        episode.files.firstOrNull()?.let { seriesEpisodeFile: SeriesEpisodeFile ->
                            listRowAdapter.add(seriesEpisodeFile.fileModel)
                        }
                    }
                    if (listRowAdapter.size() > 0) {
                        val header = HeaderItem(headerIdCounter++, "Série ${season.seasonNumber} (${series.title})")
                        rowsAdapter.add(ListRow(header, listRowAdapter))
                        hasContent = true
                    }
                }
            }
        }

        if (otherVideos.isNotEmpty()) {
            val moviesHeader = HeaderItem(headerIdCounter++, "Filmy a jiné video")
            val moviesListAdapter = ArrayObjectAdapter(cardPresenter)
            otherVideos.forEach { fileModel: FileModel ->
                moviesListAdapter.add(fileModel)
            }
            rowsAdapter.add(ListRow(moviesHeader, moviesListAdapter))
            hasContent = true
        }

        if (!hasContent) {
            displayErrorMessageInFragment(getString(R.string.series_no_episodes_found_for, series.title), rowsAdapter)
        }
        activityListener?.onFileSelectedInResults(null)
    }

    private fun setupEventListeners() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is FileModel) {
                Log.d(TAG, "Item clicked in ResultsFragment: ${item.name} (ident: ${item.ident})")
                val clickedSeriesEpisode: SeriesEpisode? = findSeriesEpisodeForFileModel(item)

                if (clickedSeriesEpisode != null) {
                    if (clickedSeriesEpisode.files.size > 1) {
                        Log.d(TAG, "Episode '${item.episodeTitle ?: item.name}' has ${clickedSeriesEpisode.files.size} files.")
                        val dialog = ComposeEpisodeSelectionDialogFragment.newInstance(
                            clickedSeriesEpisode.files,
                            "S${clickedSeriesEpisode.seasonNumber}E${clickedSeriesEpisode.episodeNumber}: ${clickedSeriesEpisode.commonEpisodeTitle ?: item.name}"
                        )
                        dialog.setTargetFragment(this@TvSearchFragment, 0)
                        parentFragmentManager.let { dialog.show(it, "ComposeEpisodeFileSelectionDialog") }
                    } else if (clickedSeriesEpisode.files.isNotEmpty()) {
                        viewModel.getFileLinkForFile(clickedSeriesEpisode.files.first().fileModel)
                    } else {
                        Toast.makeText(activity, "Pro tuto epizodu nebyly nalezeny žádné soubory.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    viewModel.getFileLinkForFile(item)
                }
            }
        }
        onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, _ ->
            activityListener?.onFileSelectedInResults(item as? FileModel)
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

    override fun onEpisodeFileSelected(selectedFileModel: FileModel) {
        Log.d(TAG, "File selected from Compose dialog in ResultsFragment: ${selectedFileModel.name}")
        viewModel.getFileLinkForFile(selectedFileModel)
    }

    // ***** PŘIDÁNA CHYBĚJÍCÍ METODA S override *****
    override fun onEpisodeSelectionCancelled() {
        Log.d(TAG, "Episode selection was cancelled in ResultsFragment.")
        // Zde můžete provést jakékoli akce potřebné po zrušení dialogu,
        // například obnovit focus na původní prvek, pokud je to nutné.
    }
    // ********************************************

    override fun onDetach() {
        super.onDetach()
        activityListener = null
    }
}
