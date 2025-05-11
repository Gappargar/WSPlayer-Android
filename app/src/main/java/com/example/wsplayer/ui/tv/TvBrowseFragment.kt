package com.example.wsplayer.ui.tv // Uistite sa, že balíček zodpovedá

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.ViewModelProvider
import com.example.wsplayer.R // Váš R súbor
import com.example.wsplayer.data.api.WebshareApiService
import com.example.wsplayer.data.models.* // Import všetkých modelov vrátane HistoryState a HistoryItem
import com.example.wsplayer.ui.search.SearchViewModel // Váš existujúci SearchViewModel
import com.example.wsplayer.ui.search.SearchViewModelFactory
// Import pre HistoryState z SearchViewModel
import com.example.wsplayer.ui.search.HistoryState
import com.example.wsplayer.ui.tv.presenters.CardPresenter // Váš nový CardPresenter
// ***** SPRÁVNÝ IMPORT PRO CustomTvSearchActivity *****
import com.example.wsplayer.ui.tv.CustomTvSearchActivity // Import pre CustomTvSearchActivity

class TvBrowseFragment : BrowseSupportFragment() {

    private val TAG = "TvBrowseFragment"

    /**
     * Interface pro komunikaci s hostitelskou aktivitou, když je vybrán soubor.
     */
    interface OnFileSelectedListener {
        fun onFileSelectedInBrowse(file: FileModel?) // Předáváme FileModel nebo null
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
    // Adapter pro řádek historie
    private var historyListRowAdapter: ArrayObjectAdapter? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Přiřazení listeneru z kontextu (hostitelské aktivity)
        if (context is OnFileSelectedListener) {
            fileSelectedListener = context
            Log.d(TAG, "OnFileSelectedListener attached to activity.")
        } else {
            Log.e(TAG, "$context must implement OnFileSelectedListener for detail view to work.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        setupUIElements()
        setupEventListeners() // Listener se nastaví zde
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")
        observeViewModel()

        // Načíst historii po ověření přihlášení
        viewModel.isUserLoggedIn.observe(viewLifecycleOwner) { isLoggedIn ->
            if (isLoggedIn == true) {
                // Načíst historii pouze pokud ještě není načtená nebo se nenačítá
                if (viewModel.historyState.value is HistoryState.Idle || viewModel.historyState.value == null) {
                    Log.d(TAG, "User logged in, fetching history...")
                    viewModel.fetchHistory(limit = 30) // Načteme např. posledních 30 položek
                }
            } else {
                Log.w(TAG, "User is not logged in, cannot fetch history.")
                // Můžeme zobrazit zprávu nebo vyčistit UI
                displayInitialMessage()
                fileSelectedListener?.onFileSelectedInBrowse(null) // Vyčistit detaily
            }
        }
    }


    private fun setupUIElements() {
        title = getString(R.string.app_name_tv)
        badgeDrawable = ContextCompat.getDrawable(requireActivity(), R.mipmap.ic_launcher_tv)

        headersState = HEADERS_ENABLED // Zobrazíme hlavičky riadkov
        isHeadersTransitionOnBackEnabled = true

        // Nastavenie presentera pre riadky
        val listRowPresenter = ListRowPresenter(FocusHighlight.ZOOM_FACTOR_MEDIUM)
        listRowPresenter.shadowEnabled = true // Zapnutie tieňov pre lepší vzhľad kariet
        listRowPresenter.selectEffectEnabled = true // Efekt pri výbere karty

        rowsAdapter = ArrayObjectAdapter(listRowPresenter)
        adapter = rowsAdapter // Nastavenie hlavného adaptéra pre BrowseSupportFragment

        // Nastavenie listenera pre kliknutie na ikonu vyhľadávania
        setOnSearchClickedListener {
            Log.d(TAG, "Search icon clicked, starting CustomTvSearchActivity")
            // ***** ZMENA ZDE: Spustenie CustomTvSearchActivity *****
            val intent = Intent(activity, CustomTvSearchActivity::class.java)
            startActivity(intent)
        }
    }

    private fun observeViewModel() {
        Log.d(TAG, "Setting up observers for ViewModel.")

        // Observer pro stav historie
        viewModel.historyState.observe(viewLifecycleOwner) { state ->
            Log.d(TAG, "HistoryState changed: $state")
            when (state) {
                is HistoryState.Loading -> {
                    Log.d(TAG, "History state: Loading")
                    fileSelectedListener?.onFileSelectedInBrowse(null)
                }
                is HistoryState.Success -> {
                    Log.d(TAG, "History state: Success - ${state.items.size} items")
                    displayHistory(state.items)
                    if (state.items.isEmpty()){
                        fileSelectedListener?.onFileSelectedInBrowse(null)
                    }
                }
                is HistoryState.Error -> {
                    Log.e(TAG, "History state: Error - ${state.message}")
                    Toast.makeText(activity, "Chyba načítání historie: ${state.message}", Toast.LENGTH_LONG).show()
                    displayInitialMessage()
                    fileSelectedListener?.onFileSelectedInBrowse(null)
                }
                is HistoryState.Idle -> {
                    Log.d(TAG, "History state: Idle")
                    displayInitialMessage()
                    fileSelectedListener?.onFileSelectedInBrowse(null)
                }
            }
        }

        // Observer pro stav odkazu (pro kliknutí na položku historie)
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
     * Zobrazí úvodní zprávu nebo prázdný stav.
     */
    private fun displayInitialMessage() {
        rowsAdapter.clear()
        historyListRowAdapter = null
        val header = HeaderItem(0, getString(R.string.welcome_message_header))
        val messagePresenter = SingleTextViewPresenter()
        val messageAdapter = ArrayObjectAdapter(messagePresenter)
        messageAdapter.add(getString(R.string.use_search_prompt))
        rowsAdapter.add(ListRow(header, messageAdapter))
        Log.d(TAG, "Displayed initial message.")
        fileSelectedListener?.onFileSelectedInBrowse(null)
    }

    /**
     * Zobrazí načtenou historii stahování.
     */
    private fun displayHistory(historyItems: List<HistoryItem>) {
        rowsAdapter.clear()
        historyListRowAdapter = null

        if (historyItems.isEmpty()) {
            val header = HeaderItem(0, getString(R.string.history_header))
            val messagePresenter = SingleTextViewPresenter()
            val messageAdapter = ArrayObjectAdapter(messagePresenter)
            messageAdapter.add(getString(R.string.history_empty))
            rowsAdapter.add(ListRow(header, messageAdapter))
            Log.d(TAG, "Displayed empty history message.")
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
        Log.d(TAG, "Displayed ${historyItems.size} history items.")
        if (historyItems.isNotEmpty() && rowsAdapter.size() > 0) {
            // Spoliehame sa na onItemViewSelectedListener
        } else {
            fileSelectedListener?.onFileSelectedInBrowse(null)
        }
    }


    private fun setupEventListeners() {
        onItemViewSelectedListener = OnItemViewSelectedListener { itemViewHolder, item, rowViewHolder, row ->
            if (item is FileModel) {
                Log.d(TAG,"Selected FileModel: ${item.name}")
                fileSelectedListener?.onFileSelectedInBrowse(item)
            } else {
                fileSelectedListener?.onFileSelectedInBrowse(null)
            }
        }

        onItemViewClickedListener = OnItemViewClickedListener { itemViewHolder, item, rowViewHolder, row ->
            if (item is FileModel) {
                Log.d(TAG, "History item clicked: ${item.name} (ident: ${item.ident})")
                viewModel.getFileLinkForFile(item)
            } else {
                Log.w(TAG, "Clicked on unknown item type: $item")
            }
        }
    }

    // Presenter pro zobrazení textové zprávy
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
