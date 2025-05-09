package com.example.wsplayer.ui.tv // Ujistěte se, že balíček odpovídá

import android.content.Intent
import android.net.Uri
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
import com.example.wsplayer.data.models.* // Import modelů (FileModel, HistoryItem atd.)
// import com.example.wsplayer.ui.player.PlayerActivity // Už není potřeba
import com.example.wsplayer.ui.search.SearchViewModel // Váš existující SearchViewModel
import com.example.wsplayer.ui.search.SearchViewModelFactory
// ***** PŘIDÁN SPRÁVNÝ IMPORT PRO HistoryState *****
import com.example.wsplayer.ui.search.HistoryState // Import sealed classy z jejího souboru
// ***********************************************
import com.example.wsplayer.ui.tv.presenters.CardPresenter // Váš nový CardPresenter
// import com.example.wsplayer.ui.tv.TvSearchActivity // Import pro TvSearchActivity

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
    // Adapter pro řádek historie
    private var historyListRowAdapter: ArrayObjectAdapter? = null

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

        setOnSearchClickedListener {
            Log.d(TAG, "Search icon clicked, starting TvSearchActivity")
            val intent = Intent(activity, TvSearchActivity::class.java)
            startActivity(intent)
        }
    }

    private fun observeViewModel() {
        Log.d(TAG, "Setting up observers for ViewModel.")

        // Observer pro stav historie
        viewModel.historyState.observe(viewLifecycleOwner) { state ->
            Log.d(TAG, "HistoryState changed: $state")
            // Nyní by měl when výraz fungovat správně
            when (state) {
                is HistoryState.Loading -> {
                    Log.d(TAG, "History state: Loading")
                }
                is HistoryState.Success -> {
                    Log.d(TAG, "History state: Success - ${state.items.size} items")
                    displayHistory(state.items)
                }
                is HistoryState.Error -> {
                    Log.e(TAG, "History state: Error - ${state.message}")
                    Toast.makeText(activity, "Chyba načítání historie: ${state.message}", Toast.LENGTH_LONG).show()
                    displayInitialMessage()
                }
                is HistoryState.Idle -> {
                    Log.d(TAG, "History state: Idle")
                    displayInitialMessage()
                }
                // else větev není potřeba, pokud jsou všechny stavy pokryty
            }
        }

        // Observer pro stav odkazu (pro kliknutí na položku historie)
        viewModel.fileLinkState.observe(viewLifecycleOwner) { state ->
            // Zpracování stavu odkazu... (beze změny)
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
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Přidání flagu
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
        historyListRowAdapter = null // Resetovat adapter historie
        val header = HeaderItem(0, getString(R.string.welcome_message_header)) // Použití existujícího stringu
        val messagePresenter = SingleTextViewPresenter()
        val messageAdapter = ArrayObjectAdapter(messagePresenter)
        messageAdapter.add(getString(R.string.use_search_prompt)) // Použití existujícího stringu
        rowsAdapter.add(ListRow(header, messageAdapter))
        Log.d(TAG, "Displayed initial message.")
    }

    /**
     * Zobrazí načtenou historii stahování.
     */
    private fun displayHistory(historyItems: List<HistoryItem>) {
        rowsAdapter.clear() // Vyčistit předchozí řádky (např. úvodní zprávu)
        historyListRowAdapter = null // Resetovat

        if (historyItems.isEmpty()) {
            // Pokud je historie prázdná, zobrazit zprávu
            val header = HeaderItem(0, getString(R.string.history_header)) // Nový string
            val messagePresenter = SingleTextViewPresenter()
            val messageAdapter = ArrayObjectAdapter(messagePresenter)
            messageAdapter.add(getString(R.string.history_empty)) // Nový string
            rowsAdapter.add(ListRow(header, messageAdapter))
            Log.d(TAG, "Displayed empty history message.")
            return
        }

        // Vytvořit adapter pro historii s CardPresenterem
        val cardPresenter = CardPresenter()
        historyListRowAdapter = ArrayObjectAdapter(cardPresenter)

        // Přidat položky historie do adapteru
        // Převedeme HistoryItem na FileModel pro kompatibilitu s CardPresenterem
        historyItems.forEach { historyItem ->
            val fileModel = FileModel(
                ident = historyItem.ident,
                name = historyItem.name,
                type = historyItem.name.substringAfterLast('.', ""), // Odvození typu z koncovky
                img = null, // Historie nemá obrázek
                stripe = null,
                stripe_count = null,
                size = historyItem.size,
                queued = 0, // Výchozí hodnota 0
                positive_votes = 0, // Výchozí hodnota 0
                negative_votes = 0, // Výchozí hodnota 0
                password = historyItem.password
            )
            historyListRowAdapter?.add(fileModel)
        }

        // Vytvořit řádek pro historii
        val header = HeaderItem(0, getString(R.string.history_header)) // Nový string
        rowsAdapter.add(ListRow(header, historyListRowAdapter))
        Log.d(TAG, "Displayed ${historyItems.size} history items.")
    }


    private fun setupEventListeners() {
        // Listener pro kliknutí - nyní musí rozlišit, zda klikáme na FileModel z historie
        onItemViewSelectedListener = OnItemViewSelectedListener { itemViewHolder, item, rowViewHolder, row ->
            // Můžeme přidat logování pro typ vybrané položky
            if (item is FileModel) { Log.d(TAG,"Selected FileModel: ${item.name}") }
        }

        onItemViewClickedListener = OnItemViewClickedListener { itemViewHolder, item, rowViewHolder, row ->
            // Zpracujeme kliknutí pouze na FileModel (který jsme vytvořili z HistoryItem)
            if (item is FileModel) {
                Log.d(TAG, "History item clicked: ${item.name} (ident: ${item.ident})")
                // Zavoláme getFileLinkForFile s tímto FileModel objektem
                viewModel.getFileLinkForFile(item)
            } else {
                Log.w(TAG, "Clicked on unknown item type: $item")
            }
        }
    }

    // Presenter pro zobrazení textové zprávy
    class SingleTextViewPresenter : Presenter() {
        // Kód presenteru... (beze změny)
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
