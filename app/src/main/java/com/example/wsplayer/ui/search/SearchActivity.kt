package com.example.wsplayer.ui.search // Váš balíček pro SearchActivity - ZKONTROLUJTE

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo // Import pro EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView // Import pro AdapterView (pro OnItemSelectedListener)
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
// import androidx.lifecycle.Observer // Nechat pokud používáte Observer { } syntaxi, jinak smazat (již používáte lambda)
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// Importy pro Repository, AuthTokenManager, ApiService - potřeba pro Factory volání (ZKONTROLUJTE CESTU)
import com.example.wsplayer.data.repository.WebshareRepository
import com.example.wsplayer.AuthTokenManager // Import AuthTokenManager
import com.example.wsplayer.data.api.WebshareApiService // Import ApiService

// **Importujte modelové třídy z vašeho data.models balíčku** (ZKONTROLUJTE CESTU)
import com.example.wsplayer.data.models.* // Toto by mělo importovat FileModel, SearchState, FileLinkState, SearchResponse atd.


// Import pro MainActivity (pro přesměrování zpět) (ZKONTROLUJTE CESTU)
import com.example.wsplayer.MainActivity

// Import pro R třídu (pro string resources)
import com.example.wsplayer.R

// Import pro SearchViewModel a Factory (ZKONTROLUJTE CESTU)
import com.example.wsplayer.ui.search.SearchViewModel // ViewModel se logout logikou
import com.example.wsplayer.ui.search.SearchViewModelFactory // Factory pro SearchViewModel

// Import pro SettingsActivity (ZKONTROLUJTE CESTU)
import com.example.wsplayer.ui.settings.SettingsActivity

// Import pro SearchAdapter (ZKONTROLUJTE CESTU)
import com.example.wsplayer.ui.search.SearchAdapter

// Import pro View Binding (vygenerovaná třída) (ZKONTROLUJTE CESTU)
import com.example.wsplayer.databinding.ActivitySearchBinding


// Activity pro obrazovku vyhledávání souborů
// Spouští se po úspěšném přihlášení z MainActivity
class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding // Binding pro layout activity_search.xml
    private lateinit var viewModel: SearchViewModel // Toto je SearchViewModel

    private lateinit var searchAdapter: SearchAdapter

    // Hodnoty pro API z pole stringů (pro řazení)
    private lateinit var sortApiValues: Array<String>

    // Posluchač posunu pro RecyclerView (pro stránkování)
    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)

            val layoutManager = recyclerView.layoutManager as LinearLayoutManager?
            if (layoutManager != null) {
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                val threshold = 5 // Načíst další stránku, když zbývá N položek do konce
                if (viewModel.isLoading.value == false && viewModel.searchState.value !is SearchState.LoadingMore &&
                    (visibleItemCount + firstVisibleItemPosition) >= (totalItemCount - threshold) && firstVisibleItemPosition >= 0 && totalItemCount > 0) {
                    println("SearchActivity: Detekován konec seznamu, volám loadNextPage().")
                    viewModel.loadNextPage()
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        println("SearchActivity: >>> onCreate spuštěn. PID: ${android.os.Process.myPid()}")

        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        println("SearchActivity: ContentView nastaven.")

        // Načtení pole pro API hodnoty řazení ze strings.xml
        sortApiValues = resources.getStringArray(R.array.sort_options_api_values)

        // --- Inicializace Spinneru pro výběr řazení ---
        setupSortSpinner()
        println("SearchActivity: Spinner řazení nastaven.")


        // --- Inicializace ViewModelu (SearchViewModel) pomocí Factory ---
        val apiService = WebshareApiService.create()
        val viewModelFactory = SearchViewModelFactory(applicationContext, apiService)
        viewModel = ViewModelProvider(this, viewModelFactory)[SearchViewModel::class.java]
        println("SearchActivity: ViewModel (SearchViewModel) získán.")


        // --- Inicializace Adapteru a Nastavení RecyclerView ---
        searchAdapter = SearchAdapter { clickedFile ->
            println("SearchActivity: Kliknuto na soubor: ${clickedFile.name}")

            if (clickedFile.password == 1) {
                Toast.makeText(this, "Soubor je chráněn heslem. Podpora zatím není implementována.", Toast.LENGTH_SHORT).show()
                return@SearchAdapter
            }
            viewModel.getFileLinkForFile(clickedFile)
        }
        binding.recyclerViewSearchResults.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewSearchResults.addOnScrollListener(scrollListener)
        binding.recyclerViewSearchResults.adapter = searchAdapter
        println("SearchActivity: RecyclerView a Adapter nastaveny.")


        // --- Sledování stavů ze SearchViewModelu (Observere) ---
        observeSearchState()
        observeFileLinkState()
        observeLoginStatus()


        // --- Posluchači na tlačítka a EditText ---
        setupListeners()

        println("SearchActivity: <<< onCreate dokončen.")
    }

    private fun setupSortSpinner() {
        val sortOptionsDisplayArray = resources.getStringArray(R.array.sort_options_display_texts)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sortOptionsDisplayArray)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSort.adapter = adapter // Používáme binding pro spinnerSort

        // Listener můžeme ponechat, pokud by měl vyhledávání spouštět automaticky
        // binding.spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        //    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        //        performSearchWithCurrentValues() // Volitelně spustit hledání hned
        //    }
        //    override fun onNothingSelected(parent: AdapterView<*>?) {}
        // }
    }


    private fun observeSearchState() {
        println("SearchActivity: Nastavuji Observer pro viewModel.searchState.")
        viewModel.searchState.observe(this) { state ->
            println("SearchActivity: Observer searchState spuštěn. Aktuální stav: $state")
            when (state) {
                is SearchState.Idle -> {
                    binding.progressBarSearch.visibility = View.GONE
                    binding.buttonSearch.isEnabled = true
                    binding.recyclerViewSearchResults.visibility = View.GONE
                    binding.textViewSearchMessage.text = getString(R.string.search_idle_message)
                    binding.textViewSearchMessage.visibility = View.VISIBLE
                }
                is SearchState.Loading -> {
                    binding.progressBarSearch.visibility = View.VISIBLE
                    binding.buttonSearch.isEnabled = false
                    binding.recyclerViewSearchResults.visibility = View.GONE
                    binding.textViewSearchMessage.visibility = View.GONE
                }
                is SearchState.Success -> {
                    binding.progressBarSearch.visibility = View.GONE
                    binding.buttonSearch.isEnabled = true
                    if (::searchAdapter.isInitialized) {
                        searchAdapter.submitList(state.results)
                    }
                    binding.recyclerViewSearchResults.visibility = View.VISIBLE
                    binding.textViewSearchMessage.visibility = View.GONE
                    if (state.results.isNotEmpty()) {
                        Toast.makeText(this, getString(R.string.search_success_toast, state.results.size, state.totalResults), Toast.LENGTH_SHORT).show()
                    } else if (binding.editTextSearchQuery.text.toString().trim().isNotEmpty()) {
                        binding.textViewSearchMessage.text = getString(R.string.search_no_results_for_query)
                        binding.textViewSearchMessage.visibility = View.VISIBLE
                        Toast.makeText(this, getString(R.string.search_no_results_toast), Toast.LENGTH_SHORT).show()
                    }
                }
                is SearchState.Error -> {
                    binding.progressBarSearch.visibility = View.GONE
                    binding.buttonSearch.isEnabled = true
                    if (::searchAdapter.isInitialized) {
                        searchAdapter.submitList(emptyList())
                    }
                    binding.recyclerViewSearchResults.visibility = View.GONE
                    binding.textViewSearchMessage.text = getString(R.string.search_error_message, state.message)
                    binding.textViewSearchMessage.visibility = View.VISIBLE
                    Toast.makeText(this, getString(R.string.search_error_toast, state.message), Toast.LENGTH_LONG).show()
                }
                is SearchState.EmptyResults -> {
                    binding.progressBarSearch.visibility = View.GONE
                    binding.buttonSearch.isEnabled = true
                    if (::searchAdapter.isInitialized) {
                        searchAdapter.submitList(emptyList())
                    }
                    binding.recyclerViewSearchResults.visibility = View.GONE
                    binding.textViewSearchMessage.text = getString(R.string.search_no_results_for_query)
                    binding.textViewSearchMessage.visibility = View.VISIBLE
                    Toast.makeText(this, getString(R.string.search_no_results_toast), Toast.LENGTH_SHORT).show()
                }
                is SearchState.LoadingMore -> {
                    println("SearchActivity: SearchState je LoadingMore.")
                    binding.buttonSearch.isEnabled = false
                }
            }
        }
    }

    private fun observeFileLinkState() {
        println("SearchActivity: Nastavuji Observer pro viewModel.fileLinkState.")
        viewModel.fileLinkState.observe(this) { state ->
            println("SearchActivity: Observer fileLinkState spuštěn. Aktuální stav: $state")
            when (state) {
                is FileLinkState.Idle -> {}
                is FileLinkState.LoadingLink -> {
                    Toast.makeText(this, getString(R.string.getting_link_toast), Toast.LENGTH_SHORT).show()
                }
                is FileLinkState.LinkSuccess -> {
                    val fileUrl = state.fileUrl
                    binding.textViewSearchMessage.visibility = View.GONE

                    if (fileUrl.isNotEmpty()) {
                        val playIntent = Intent(Intent.ACTION_VIEW)
                        playIntent.setDataAndType(Uri.parse(fileUrl), "video/*")
                        println("SearchActivity: FileLinkState.LinkSuccess - Spouštím Intent ACTION_VIEW pro URL: $fileUrl")
                        if (playIntent.resolveActivity(packageManager) != null) {
                            startActivity(playIntent)
                        } else {
                            Toast.makeText(this, getString(R.string.no_video_player_app_toast), Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this, getString(R.string.empty_link_error_toast), Toast.LENGTH_LONG).show()
                        binding.textViewSearchMessage.text = getString(R.string.empty_link_error_message)
                        binding.textViewSearchMessage.visibility = View.VISIBLE
                    }
                    viewModel.resetFileLinkState() // Reset stavu
                }
                is FileLinkState.Error -> {
                    binding.textViewSearchMessage.text = getString(R.string.link_error_message, state.message)
                    binding.textViewSearchMessage.visibility = View.VISIBLE
                    Toast.makeText(this, getString(R.string.link_error_toast, state.message), Toast.LENGTH_LONG).show()
                    viewModel.resetFileLinkState() // Reset stavu
                }
            }
        }
    }

    private fun observeLoginStatus() {
        println("SearchActivity: Nastavuji Observer pro viewModel.isUserLoggedIn.")
        viewModel.isUserLoggedIn.observe(this) { isLoggedIn ->
            println("SearchActivity: Observer isUserLoggedIn spuštěn. isUserLoggedIn: $isLoggedIn")
            if (!isLoggedIn) {
                println("SearchActivity: isUserLoggedIn je false. Přesměrovávám na MainActivity.")
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
                println("SearchActivity: finish() voláno po přesměrování na MainActivity.")
            } else {
                println("SearchActivity: isUserLoggedIn je true. Pokračuji v SearchActivity.")
            }
        }
        println("SearchActivity: Observer isUserLoggedIn nastaven.")
    }

    private fun setupListeners() {
        println("SearchActivity: Nastavuji OnClickListener pro tlačítko Hledat.")
        binding.buttonSearch.setOnClickListener {
            performSearchWithCurrentValues()
        }

        binding.editTextSearchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearchWithCurrentValues()
                true
            } else {
                false
            }
        }

        println("SearchActivity: Nastavuji OnClickListener pro tlačítko Nastavení.")
        binding.buttonSettings.setOnClickListener {
            println("SearchActivity: Kliknuto na tlačítko Nastavení.")
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun performSearchWithCurrentValues() {
        println("SearchActivity: Kliknuto na tlačítko Hledat / Akce z klávesnice.")
        val query = binding.editTextSearchQuery.text.toString().trim()

        val fixedCategoryApiValue = "video" // ***** VŽDY HLEDÁME VIDEO *****

        val selectedSortIndex = binding.spinnerSort.selectedItemPosition // Čteme hodnotu ze spinneru pro řazení
        val selectedSortApiValue = sortApiValues.getOrNull(selectedSortIndex)?.let {
            if (it == "@null") null else it
        }

        if (query.isNotEmpty()) {
            println("SearchActivity: Spouštím vyhledávání pro dotaz: '$query', kategorie: '$fixedCategoryApiValue', řazení: '$selectedSortApiValue'")
            viewModel.search(query, category = fixedCategoryApiValue, sort = selectedSortApiValue) // Použití pevné kategorie
        } else {
            Toast.makeText(this, getString(R.string.empty_query_toast), Toast.LENGTH_SHORT).show()
            println("SearchActivity: Pokus o prázdné vyhledávání.")
        }

        // Skrytí klávesnice
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        println("SearchActivity: Klávesnice skryta.")
    }

    override fun onDestroy() {
        println("SearchActivity: >>> onDestroy spuštěn. PID: ${android.os.Process.myPid()}")
        if (::binding.isInitialized) {
            binding.recyclerViewSearchResults.removeOnScrollListener(scrollListener)
        }
        super.onDestroy()
        println("SearchActivity: <<< onDestroy dokončen.")
    }
}
