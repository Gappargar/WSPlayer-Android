package com.example.wsplayer.ui.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.lifecycle.ViewModelProvider
import com.example.wsplayer.R
import com.example.wsplayer.data.api.WebshareApiService
import com.example.wsplayer.data.models.FileLinkState
import com.example.wsplayer.data.models.FileModel
import com.example.wsplayer.data.models.HistoryItem
import com.example.wsplayer.ui.search.HistoryState
import com.example.wsplayer.ui.search.SearchViewModel
import com.example.wsplayer.ui.search.SearchViewModelFactory
import com.example.wsplayer.ui.tv.presenters.CardPresenter

class TvBrowseFragment : BrowseSupportFragment() {

    private val TAG = "TvBrowseFragment"

    interface OnFileSelectedListener {
        fun onFileSelectedInBrowse(file: FileModel?)
    }

    private var fileSelectedListener: OnFileSelectedListener? = null

    private val viewModel: SearchViewModel by lazy {
        val factory = SearchViewModelFactory(
            requireActivity().application,
            WebshareApiService.create()
        )
        ViewModelProvider(this, factory)[SearchViewModel::class.java]
    }

    private lateinit var rowsAdapter: ArrayObjectAdapter
    private var historyListRowAdapter: ArrayObjectAdapter? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        fileSelectedListener = context as? OnFileSelectedListener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUIElements()
        setupEventListeners()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
        viewModel.isUserLoggedIn.observe(viewLifecycleOwner) { isLoggedIn ->
            if (isLoggedIn == true) {
                if (viewModel.historyState.value is HistoryState.Idle || viewModel.historyState.value == null) {
                    viewModel.fetchHistory(limit = 30)
                }
            } else {
                displayInitialMessage()
                fileSelectedListener?.onFileSelectedInBrowse(null)
            }
        }
    }

    private fun setupUIElements() {
        title = getString(R.string.app_name_tv)
        badgeDrawable = null // odstranění orb lupy
        headersState = HEADERS_DISABLED // odstranění modrého sloupce

        val listRowPresenter = ListRowPresenter(FocusHighlight.ZOOM_FACTOR_MEDIUM).apply {
            shadowEnabled = true
            selectEffectEnabled = true
        }
        rowsAdapter = ArrayObjectAdapter(listRowPresenter)
        adapter = rowsAdapter

        setOnSearchClickedListener(null) // deaktivace klikání na lupu
    }

    private fun observeViewModel() {
        viewModel.historyState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is HistoryState.Loading -> fileSelectedListener?.onFileSelectedInBrowse(null)
                is HistoryState.Success -> {
                    displayHistory(state.items)
                    if (state.items.isEmpty()) fileSelectedListener?.onFileSelectedInBrowse(null)
                }
                is HistoryState.Error -> {
                    Toast.makeText(activity, "Chyba načítání historie: ${state.message}", Toast.LENGTH_LONG).show()
                    displayInitialMessage()
                    fileSelectedListener?.onFileSelectedInBrowse(null)
                }
                is HistoryState.Idle -> {
                    displayInitialMessage()
                    fileSelectedListener?.onFileSelectedInBrowse(null)
                }
            }
        }

        viewModel.fileLinkState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is FileLinkState.LoadingLink ->
                    Toast.makeText(activity, getString(R.string.getting_link_toast), Toast.LENGTH_SHORT).show()
                is FileLinkState.LinkSuccess -> {
                    if (state.fileUrl.isNotEmpty()) {
                        val playIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(state.fileUrl), "video/*")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        if (playIntent.resolveActivity(requireActivity().packageManager) != null) {
                            startActivity(playIntent)
                        } else {
                            Toast.makeText(activity, getString(R.string.no_video_player_app_toast), Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(activity, getString(R.string.empty_link_error_toast), Toast.LENGTH_LONG).show()
                    }
                    viewModel.resetFileLinkState()
                }
                is FileLinkState.Error -> {
                    Toast.makeText(activity, getString(R.string.link_error_toast, state.message), Toast.LENGTH_LONG).show()
                    viewModel.resetFileLinkState()
                }
                is FileLinkState.Idle -> {}
            }
        }
    }

    private fun displayInitialMessage() {
        rowsAdapter.clear()
        historyListRowAdapter = null
        val header = HeaderItem(0, getString(R.string.welcome_message_header))
        val messagePresenter = SingleTextViewPresenter()
        val messageAdapter = ArrayObjectAdapter(messagePresenter)
        messageAdapter.add(getString(R.string.use_search_prompt))
        rowsAdapter.add(ListRow(header, messageAdapter))
    }

    private fun displayHistory(historyItems: List<HistoryItem>) {
        rowsAdapter.clear()
        historyListRowAdapter = null

        if (historyItems.isEmpty()) {
            val header = HeaderItem(0, getString(R.string.history_header))
            val messagePresenter = SingleTextViewPresenter()
            val messageAdapter = ArrayObjectAdapter(messagePresenter)
            messageAdapter.add(getString(R.string.history_empty))
            rowsAdapter.add(ListRow(header, messageAdapter))
            fileSelectedListener?.onFileSelectedInBrowse(null)
            return
        }

        val cardPresenter = CardPresenter()
        historyListRowAdapter = ArrayObjectAdapter(cardPresenter)

        historyItems.forEach { historyItem ->
            val fileModel = FileModel(
                ident = historyItem.ident, name = historyItem.name,
                type = historyItem.name.substringAfterLast('.', "?"),
                img = null, stripe = null, stripe_count = null, size = historyItem.size,
                queued = 0, positive_votes = 0, negative_votes = 0,
                password = historyItem.password,
                displayDate = historyItem.startedAt ?: historyItem.endedAt
            )
            historyListRowAdapter?.add(fileModel)
        }

        val header = HeaderItem(0, getString(R.string.history_header))
        rowsAdapter.add(ListRow(header, historyListRowAdapter))
    }

    private fun setupEventListeners() {
        onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, _ ->
            fileSelectedListener?.onFileSelectedInBrowse(item as? FileModel)
        }

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is FileModel) viewModel.getFileLinkForFile(item)
        }
    }

    class SingleTextViewPresenter : Presenter() {
        override fun onCreateViewHolder(parent: ViewGroup) = ViewHolder(TextView(parent.context).apply {
            isFocusable = false
            setPadding(32, 16, 32, 16)
            textSize = 18f
        })

        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
            (viewHolder.view as TextView).text = item as? String ?: ""
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
    }
}