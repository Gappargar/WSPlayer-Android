package com.example.wsplayer.ui.search // Váš balíček pro SearchActivity - ZKONTROLUJTE

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer // Nechat pro Observer na SearchViewModel LiveData
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wsplayer.data.api.WebshareApiService
import com.example.wsplayer.data.repository.WebshareRepository
import com.example.wsplayer.AuthTokenManager // Potřeba pro Repository
import com.example.wsplayer.databinding.ActivitySearchBinding
import com.example.wsplayer.MainActivity // Import pro případné přesměrování zpět
import com.example.wsplayer.R // Import pro přístup k řetězcovým zdrojům
import com.example.wsplayer.data.models.*
import com.example.wsplayer.ui.settings.SettingsActivity

// Activity pro obrazovku vyhledávání souborů
class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var viewModel: SearchViewModel // Toto je SearchViewModel
    // **ODSTRANILI JSME PROMĚNNOU PRO LoginViewModel**
    // private lateinit var loginViewModel: LoginViewModel

    private lateinit var searchAdapter: SearchAdapter

    // Posluchač posunu pro RecyclerView (pro stránkování)
    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)

            val layoutManager = recyclerView.layoutManager as LinearLayoutManager?
            if (layoutManager != null) {
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                val threshold = 5
                // **Používáme isLoading z SearchViewModelu**
                if (viewModel.isLoading.value != true && (visibleItemCount + firstVisibleItemPosition) >= (totalItemCount - threshold) && firstVisibleItemPosition >= 0) {
                    println("SearchActivity: Detekován konec seznamu, volám loadNextPage().")
                    viewModel.loadNextPage()
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        println("SearchActivity: >>> onCreate spuštěn. PID: ${android.os.Process.myPid()}")

        // --- Nastavení UI pomocí View Bindingu ---
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        println("SearchActivity: ContentView nastaven.")


        // --- Inicializace Spinneru pro výběr kategorie ---
        val categoriesArray = resources.getStringArray(R.array.search_categories_array)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categoriesArray)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategory.adapter = adapter
        println("SearchActivity: Spinner nastaven.")


        // --- Inicializace Repository a SearchViewModelu ---
        val apiService = WebshareApiService.create()
        val authTokenManager = AuthTokenManager(applicationContext) // Repository potřebuje Context pro AuthTokenManager
        val repository = WebshareRepository(apiService, authTokenManager) // Vytvoření Repository

        // Získání instance SearchViewModelu pomocí Factory
        // SearchViewModelFactory si sama vytvoří Repository (proto potřebuje applicationContext a apiService)
        val viewModelFactory = SearchViewModelFactory(applicationContext, apiService) // Factory pro SearchViewModel
        viewModel = ViewModelProvider(this, viewModelFactory).get(SearchViewModel::class.java)
        println("SearchActivity: ViewModel pro vyhledávání získán.")


        // **ODSTRANILI JSME ZÍSKÁNÍ INSTANCE LoginViewModelu zde**


        // --- Inicializace Adapteru a Nastavení RecyclerView ---
        searchAdapter = SearchAdapter { clickedFile -> // **TATO ŘÁDKA INICIALIZUJE searchAdapter**
            if (clickedFile.password == 1) {
                Toast.makeText(this, "Soubor je chráněn heslem. Podpora zatím není implementována.", Toast.LENGTH_SHORT).show()
                return@SearchAdapter
            }

            viewModel.getFileLinkForFile(clickedFile)

            // TODO: Volitelně zobrazit ProgressBar nebo jiný indikátor získávání odkazu zde
        }
        binding.recyclerViewSearchResults.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewSearchResults.addOnScrollListener(scrollListener)
        binding.recyclerViewSearchResults.adapter = searchAdapter
        println("SearchActivity: RecyclerView a Adapter nastaveny.")


        // --- Sledování stavů ze SearchViewModelu ---

        // Observer pro stav vyhledávání souborů (SearchState) - Zjednodušená syntaxe lambda observeru
        println("SearchActivity: Nastavuji Observer pro SearchState.")
        viewModel.searchState.observe(this) { state ->
            when (state) {
                is SearchViewModel.SearchState.Idle -> {
                    binding.progressBarSearch.visibility = View.GONE
                    binding.buttonSearch.isEnabled = true
                    binding.recyclerViewSearchResults.visibility = View.GONE
                    binding.textViewSearchMessage.text = "Zadejte, co hledáte..."
                    binding.textViewSearchMessage.visibility = View.VISIBLE
                }
                is SearchViewModel.SearchState.Loading -> {
                    binding.progressBarSearch.visibility = View.VISIBLE
                    binding.buttonSearch.isEnabled = false
                    binding.recyclerViewSearchResults.visibility = View.GONE
                    binding.textViewSearchMessage.visibility = View.GONE
                }
                is SearchViewModel.SearchState.Success -> {
                    binding.progressBarSearch.visibility = View.GONE
                    binding.buttonSearch.isEnabled = true

                    if (::searchAdapter.isInitialized) {
                        searchAdapter.submitList(state.results)
                    }

                    binding.recyclerViewSearchResults.visibility = View.VISIBLE
                    binding.textViewSearchMessage.visibility = View.GONE
                    Toast.makeText(this, "Vyhledávání úspěšné! Nalezeno ${state.results.size} souborů (první stránka). Celkem: ${state.totalResults}", Toast.LENGTH_SHORT).show()
                }
                is SearchViewModel.SearchState.Error -> {
                    binding.progressBarSearch.visibility = View.GONE
                    binding.buttonSearch.isEnabled = true
                    if (::searchAdapter.isInitialized) {
                        searchAdapter.submitList(emptyList())
                    }
                    binding.recyclerViewSearchResults.visibility = View.GONE
                    binding.textViewSearchMessage.text = "Chyba při vyhledávání: ${state.message}"
                    binding.textViewSearchMessage.visibility = View.VISIBLE
                    Toast.makeText(this, "Chyba při vyhledávání: ${state.message}", Toast.LENGTH_LONG).show()
                }
                is SearchViewModel.SearchState.EmptyResults -> {
                    binding.progressBarSearch.visibility = View.GONE
                    binding.buttonSearch.isEnabled = true
                    if (::searchAdapter.isInitialized) {
                        searchAdapter.submitList(emptyList())
                    }
                    binding.recyclerViewSearchResults.visibility = View.GONE
                    binding.textViewSearchMessage.text = "Nenalezeny žádné soubory pro daný dotaz."
                    binding.textViewSearchMessage.visibility = View.VISIBLE
                    Toast.makeText(this, "Nenalezeny žádné soubory.", Toast.LENGTH_SHORT).show()
                }
                // TODO: Přidat zpracování stavu LoadingMore
            }
        }


        // Observer pro stav získání odkazu na soubor (FileLinkState) - Zjednodušená syntaxe lambda observeru
        println("SearchActivity: Nastavuji Observer pro FileLinkState.")
        viewModel.fileLinkState.observe(this) { state ->
            when (state) {
                is SearchViewModel.FileLinkState.Idle -> {
                    // TODO: Skrýt indikátor získávání odkazu
                }
                is SearchViewModel.FileLinkState.LoadingLink -> {
                    // TODO: Zobrazit indikátor získávání odkazu
                    Toast.makeText(this, "Získávám odkaz na soubor...", Toast.LENGTH_SHORT).show()
                }
                is SearchViewModel.FileLinkState.LinkSuccess -> {
                    val fileUrl = state.fileUrl
                    binding.textViewSearchMessage.visibility = View.GONE // Skrýt TextView zpráv

                    println("SearchActivity: FileLinkState.LinkSuccess - Spouštím Intent ACTION_VIEW pro URL: $fileUrl")

                    if (fileUrl != null && fileUrl.isNotEmpty()) {
                        val playIntent = Intent(Intent.ACTION_VIEW)
                        playIntent.setDataAndType(Uri.parse(fileUrl), "video/*")

                        if (playIntent.resolveActivity(packageManager) != null) {
                            startActivity(playIntent)
                        } else {
                            Toast.makeText(this, "Nenalezena žádná aplikace pro přehrávání videa.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this, "Chyba: Získán prázdný odkaz pro přehrávání.", Toast.LENGTH_LONG).show()
                        binding.textViewSearchMessage.text = "Chyba: Získán prázdný odkaz pro přehrávání."
                        binding.textViewSearchMessage.visibility = View.VISIBLE
                    }
                }
                is SearchViewModel.FileLinkState.LinkError -> {
                    binding.textViewSearchMessage.text = "Chyba při získání odkazu: ${state.message}"
                    binding.textViewSearchMessage.visibility = View.VISIBLE
                    Toast.makeText(this, "Chyba při získání odkazu: ${state.message}", Toast.LENGTH_LONG).show()
                }
            }
        }// Ponecháno explicitní přetypování

        // **NOVÝ Observer pro kontrolu přihlášení na základě TOKENU v SearchViewModelu**
        println("SearchActivity: Nastavuji Observer pro viewModel.isUserLoggedIn.")
        viewModel.isUserLoggedIn.observe(this) { isLoggedIn ->
            println("SearchActivity: Observer isUserLoggedIn spuštěn. isUserLoggedIn: $isLoggedIn") // Log stavu
            if (!isLoggedIn) { // Pokud SearchViewModel signalizuje, že uživatel NENÍ přihlášen (žádný token nalezen v novém procesu)
                println("SearchActivity: isUserLoggedIn je false (žádný token?). Přesměrovávám na LoginActivity.")
                // Přesměrovat zpět na LoginActivity a ukončit SearchActivity
                val intent = Intent(this, MainActivity::class.java)
                // Tyto flags zajistí, že se LoginActivity stane novým kořenem Tasku
                // a SearchActivity (a vše ostatní v jejím Tasku) bude ukončeno.
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish() // **Ukončit SearchActivity**
                println("SearchActivity: finish() voláno po přesměrování na LoginActivity.")
            } else {
                // Uživatel je přihlášen (má token) - pokračovat v SearchActivity
                println("SearchActivity: isUserLoggedIn je true (token nalezen). Pokračuji v SearchActivity.")
                // Zde můžete případně spustit prvotní načtení dat pro SearchActivity,
                // pokud se to neděje automaticky v init ViewModelu.
            }
        } // Bez explicitního přetypování zde

        println("SearchActivity: Listeners nastaveny.")


        // --- Posluchači na tlačítka a EditText ---
        binding.buttonSearch.setOnClickListener {
            println("SearchActivity: Kliknuto na Hledat.") // Log
            val query = binding.editTextSearchQuery.text.toString().trim()

            val selectedCategoryIndex = binding.spinnerCategory.selectedItemPosition
            val categoriesApiValues = resources.getStringArray(R.array.search_categories_api_values) // Použití R.array

            val selectedCategoryApiValue = if (selectedCategoryIndex >= 0 && selectedCategoryIndex < categoriesApiValues.size) {
                categoriesApiValues[selectedCategoryIndex]
            } else {
                "" // Výchozí prázdná kategorie
            }

            // Spustit vyhledávání POUZE pokud query není prázdná a uživatel je (podle ViewModelu) přihlášen
            if (query.isNotEmpty()) {
                // Přidat kontrolu stavu přihlášení z ViewModelu před spuštěním vyhledávání
                if (viewModel.isUserLoggedIn.value == true) { // Kontrola pomocí LiveData
                    println("SearchActivity: Spouštím vyhledávání pro dotaz: '$query', kategorie: '$selectedCategoryApiValue'") // Log
                    viewModel.search(query, category = selectedCategoryApiValue)
                } else {
                    // Mělo by se již přesměrovat observerem, ale pro jistotu
                    println("SearchActivity: Pokus o vyhledávání bez přihlášení.") // Log
                    Toast.makeText(this, "Pro vyhledávání se prosím přihlaste.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Vyhledávací dotaz nesmí být prázdný.", Toast.LENGTH_SHORT).show()
                println("SearchActivity: Pokus o prázdné vyhledávání.") // Log
            }

            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        }

        binding.editTextSearchQuery.setOnEditorActionListener { v, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                binding.buttonSearch.performClick()
                true
            } else {
                false
            }
        }

        binding.buttonSettings.setOnClickListener {
            println("SearchActivity: Kliknuto na Nastavení.")
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        println("SearchActivity: <<< onCreate dokončen.")
    }

    override fun onDestroy() {
        println("SearchActivity: >>> onDestroy spuštěn.") // Log
        // Odstranit posluchač posunu, aby nedošlo k memory leaku nebo chybám
        if (::binding.isInitialized) { // Ověřit, zda byl binding inicializován
            binding.recyclerViewSearchResults.removeOnScrollListener(scrollListener)
        }
        super.onDestroy()
        println("SearchActivity: <<< onDestroy dokončen.") // Log
    }

    // TODO: Implementovat SearchViewModelFactory.kt pokud ještě nemáte
    // Měla by přijímat Context a WebshareApiService a vytvářet SearchViewModel s Repository.

    // TODO: Implementovat SearchViewModel.kt
    // Měl by mít LiveData<Boolean> isUserLoggedIn inicializovanou v init na základě repository.getAuthToken() != null
    // A metody search(), loadNextPage(), getFileLinkForFile() a LiveDaty searchState, fileLinkState, isLoading, searchResults, totalResults.

    // TODO: Implementovat SearchAdapter.kt

    // TODO: Implementovat SettingsActivity.kt (zatím stačí prázdná Activity)
}