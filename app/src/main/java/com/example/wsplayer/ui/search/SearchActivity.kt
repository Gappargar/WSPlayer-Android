// app/src/main/java/com/example/wsplayer/ui/search/SearchActivity.kt
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
import androidx.lifecycle.Observer // Nechat pokud používáte Observer { } syntaxi, jinak smazat
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

                val threshold = 5 // Načíst další stránku, když zbývá N položek do konce
                // **Používáme isLoading z SearchViewModelu**
                // A kontrolujeme, zda nejsme ve stavu LoadingMore (abychom nespouštěli vícekrát)
                if (viewModel.isLoading.value != true && viewModel.searchState.value != SearchState.LoadingMore &&
                    (visibleItemCount + firstVisibleItemPosition) >= (totalItemCount - threshold) && firstVisibleItemPosition >= 0) {
                    println("SearchActivity: Detekován konec seznamu, volám loadNextPage().") // Log
                    viewModel.loadNextPage() // Voláme metodu SearchViewModelu pro načtení další stránky
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        println("SearchActivity: >>> onCreate spuštěn. PID: ${android.os.Process.myPid()}") // Log

        // --- Nastavení UI pomocí View Bindingu ---
        binding = ActivitySearchBinding.inflate(layoutInflater) // Inflate layout activity_search.xml
        setContentView(binding.root) // Použití binding.root
        println("SearchActivity: ContentView nastaven.")


        // --- Inicializace Spinneru pro výběr kategorie ---
        val categoriesArray = resources.getStringArray(R.array.search_categories_array) // Použití string array z resources
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categoriesArray)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategory.adapter = adapter
        println("SearchActivity: Spinner nastaven.")


        // --- Inicializace ViewModelu (SearchViewModel) pomocí Factory ---
        // SearchViewModelFactory potřebuje Context a ApiService (pro vytvoření Repository uvnitř)
        val apiService = WebshareApiService.create() // Vytvoří instanci ApiService (ZKONTROLUJTE CESTU)
        val viewModelFactory = SearchViewModelFactory(applicationContext, apiService) // Factory pro SearchViewModel (ZKONTROLUJTE CESTU)
        viewModel = ViewModelProvider(this, viewModelFactory).get(SearchViewModel::class.java) // Získání SearchViewModelu
        println("SearchActivity: ViewModel (SearchViewModel) získán.")


        // **ODSTRANILI JSME ZÍSKÁNÍ INSTANCE LoginViewModelu zde**


        // --- Inicializace Adapteru a Nastavení RecyclerView ---
        // Inicializujeme adapter se click listenerem pro položky seznamu
        searchAdapter = SearchAdapter { clickedFile -> // **searchAdapter**
            println("SearchActivity: Kliknuto na soubor: ${clickedFile.name}") // Log

            if (clickedFile.password == 1) {
                Toast.makeText(this, "Soubor je chráněn heslem. Podpora zatím není implementována.", Toast.LENGTH_SHORT).show()
                return@SearchAdapter // Ukončit listener pro tento klik
            }

            // Zavolat metodu ViewModelu pro získání přímého odkazu na soubor
            viewModel.getFileLinkForFile(clickedFile)

            // TODO: Volitelně zobrazit ProgressBar nebo jiný indikátor získávání odkazu zde
            // binding.progressBarGettingLink.visibility = View.VISIBLE // Vyžaduje ProgressBar navíc v layoutu
        }
        // Nastavení LayoutManageru a Adapteru pro RecyclerView
        binding.recyclerViewSearchResults.layoutManager = LinearLayoutManager(this)
        // Přidání posluchače posunu pro stránkování
        binding.recyclerViewSearchResults.addOnScrollListener(scrollListener)
        binding.recyclerViewSearchResults.adapter = searchAdapter // Nastavení adapteru pro RecyclerView
        println("SearchActivity: RecyclerView a Adapter nastaveny.")


        // --- Sledování stavů ze SearchViewModelu (Observere) ---

        // Observer pro stav vyhledávání souborů (SearchState) - Zjednodušená syntaxe lambda observeru
        // Tento observer reaguje na změny stavu SearchViewModelu a aktualizuje UI
        println("SearchActivity: Nastavuji Observer pro viewModel.searchState.") // Log
        viewModel.searchState.observe(this) { state -> // 'state' je aktuální SearchState
            println("SearchActivity: Observer searchState spuštěn. Aktuální stav: $state") // Log aktuálního stavu
            when (state) { // Zpracování různých stavů vyhledávání (vyčerpávající 'when')
                is SearchState.Idle -> { // Použití SearchState přímo (po importu z data.models.*)
                    binding.progressBarSearch.visibility = View.GONE
                    binding.buttonSearch.isEnabled = true
                    // Binding prvků, které se zobrazují v Idle stavu
                    binding.recyclerViewSearchResults.visibility = View.GONE
                    binding.textViewSearchMessage.text = "Zadejte, co hledáte..."
                    binding.textViewSearchMessage.visibility = View.VISIBLE
                }
                is SearchState.Loading -> { // Stav načítání první stránky
                    binding.progressBarSearch.visibility = View.VISIBLE // Zobrazit hlavní progress bar
                    binding.buttonSearch.isEnabled = false
                    // Binding prvků, které se skrývají v Loading stavu
                    binding.recyclerViewSearchResults.visibility = View.GONE
                    binding.textViewSearchMessage.visibility = View.GONE
                }
                is SearchState.Success -> { // Úspěšně načteny výsledky (první nebo další stránka)
                    binding.progressBarSearch.visibility = View.GONE
                    binding.buttonSearch.isEnabled = true

                    // Předat (aktualizovaný) seznam Adapteru - submitList se postará o přidání nových položek
                    if (::searchAdapter.isInitialized) { // Kontrola inicializace adapteru
                        searchAdapter.submitList(state.results) // state.results je List<FileModel>
                    }

                    binding.recyclerViewSearchResults.visibility = View.VISIBLE // Zobrazit seznam
                    binding.textViewSearchMessage.visibility = View.GONE
                    // Toast s informací o výsledcích (používá string resource, pokud je definován)
                    Toast.makeText(this, "Vyhledávání úspěšné! Nalezeno ${state.results.size} souborů na stránce. Celkem: ${state.totalResults}", Toast.LENGTH_SHORT).show()
                }
                is SearchState.Error -> { // Chyba při vyhledávání
                    binding.progressBarSearch.visibility = View.GONE
                    binding.buttonSearch.isEnabled = true
                    if (::searchAdapter.isInitialized) {
                        searchAdapter.submitList(emptyList()) // Vyčistit seznam při chybě
                    }
                    binding.recyclerViewSearchResults.visibility = View.GONE
                    binding.textViewSearchMessage.text = "Chyba při vyhledávání: ${state.message}" // state.message
                    binding.textViewSearchMessage.visibility = View.VISIBLE
                    Toast.makeText(this, "Chyba při vyhledávání: ${state.message}", Toast.LENGTH_LONG).show() // state.message
                }
                is SearchState.EmptyResults -> { // Nalezeno 0 výsledků
                    binding.progressBarSearch.visibility = View.GONE
                    binding.buttonSearch.isEnabled = true
                    if (::searchAdapter.isInitialized) {
                        searchAdapter.submitList(emptyList()) // Vyčistit seznam
                    }
                    binding.recyclerViewSearchResults.visibility = View.GONE
                    binding.textViewSearchMessage.text = "Nenalezeny žádné soubory pro daný dotaz."
                    binding.textViewSearchMessage.visibility = View.VISIBLE
                    Toast.makeText(this, "Nenalezeny žádné soubory.", Toast.LENGTH_SHORT).show()
                }
                is SearchState.LoadingMore -> { // Stav načítání dalších stránek (pro stránkování)
                    println("SearchActivity: SearchState je LoadingMore.") // Log
                    // TODO: Zde můžete zobrazit indikátor načítání pro stránkování (např. malý progress bar dole)
                    // binding.progressBarPagination.visibility = View.VISIBLE // Předpokládá, že máte takový prvek v layoutu
                    binding.progressBarSearch.visibility = View.GONE // Hlavní progress bar by měl být skryt
                    // Seznam výsledků (_searchResults.value) se zde NEMĚNÍ, zůstávají zobrazené předchozí položky
                }
            }
        }


        // Observer pro stav získání odkazu na soubor (FileLinkState) - Zjednodušená syntaxe lambda observeru
        // Tento observer reaguje na stav získání přímého odkazu na soubor a spustí přehrávač
        println("SearchActivity: Nastavuji Observer pro viewModel.fileLinkState.") // Log
        viewModel.fileLinkState.observe(this) { state -> // 'state' je aktuální FileLinkState
            println("SearchActivity: Observer fileLinkState spuštěn. Aktuální stav: $state") // Log aktuálního stavu
            when (state) { // Zpracování různých stavů získání odkazu (vyčerpávající 'when')
                is FileLinkState.Idle -> { // Použití FileLinkState přímo
                    // TODO: Skrýt indikátor získávání odkazu
                    // binding.progressBarGettingLink.visibility = View.GONE
                }
                is FileLinkState.LoadingLink -> { // Použití FileLinkState přímo
                    // TODO: Zobrazit indikátor získávání odkazu
                    // binding.progressBarGettingLink.visibility = View.VISIBLE
                    Toast.makeText(this, "Získávám odkaz na soubor...", Toast.LENGTH_SHORT).show()
                }
                is FileLinkState.LinkSuccess -> { // Použití FileLinkState přímo
                    // Odkaz úspěšně získán - nyní spustíme externí přehrávač
                    // TODO: Skrýt indikátor získávání odkazu
                    // binding.progressBarGettingLink.visibility = View.GONE

                    val fileUrl = state.fileUrl // state.fileUrl je String
                    binding.textViewSearchMessage.visibility = View.GONE // Skrýt TextView zpráv


                    // --- Spuštění Intentu ACTION_VIEW s URL pro externí přehrávač ---
                    if (fileUrl != null && fileUrl.isNotEmpty()) { // Kontrola, zda URL není null/prázdná
                        val playIntent = Intent(Intent.ACTION_VIEW)
                        // Set data a type Intentu
                        // uri je Uri objekt, proto Uri.parse(fileUrl)
                        // type je MIME typ, např. "video/*"
                        playIntent.setDataAndType(Uri.parse(fileUrl), "video/*") // Používá fileUrl

                        println("SearchActivity: FileLinkState.LinkSuccess - Spouštím Intent ACTION_VIEW pro URL: $fileUrl") // Log

                        // Ověřit, zda existuje aplikace schopná Intent zpracovat
                        if (playIntent.resolveActivity(packageManager) != null) {
                            startActivity(playIntent) // Spustí externí aplikaci pro přehrávání
                        } else {
                            Toast.makeText(this, "Nenalezena žádná aplikace pro přehrávání videa.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this, "Chyba: Získán prázdný odkaz pro přehrávání.", Toast.LENGTH_LONG).show()
                        binding.textViewSearchMessage.text = "Chyba: Získán prázdný odkaz pro přehrávání."
                        binding.textViewSearchMessage.visibility = View.VISIBLE
                    }
                }
                is FileLinkState.Error -> { // <- POZOR, název stavu podle DataModels.kt (pokud LinkError, opravit zde)
                    // Došlo k chybě při získání odkazu
                    // TODO: Skrýt indikátor získávání odkazu
                    // binding.progressBarGettingLink.visibility = View.GONE

                    binding.textViewSearchMessage.text = "Chyba při získání odkazu: ${state.message}" // state.message
                    binding.textViewSearchMessage.visibility = View.VISIBLE
                    Toast.makeText(this, "Chyba při získání odkazu: ${state.message}", Toast.LENGTH_LONG).show() // state.message
                }
            }
        }


        // **NOVÝ Observer pro kontrolu přihlášení na základě TOKENU v SearchViewModelu**
        // Tento observer zajistí přesměrování na login obrazovku, pokud uživatel přestane být přihlášen (např. smazání tokenu při logoutu)
        println("SearchActivity: Nastavuji Observer pro viewModel.isUserLoggedIn.") // Log
        viewModel.isUserLoggedIn.observe(this) { isLoggedIn -> // 'isLoggedIn' je Boolean
            println("SearchActivity: Observer isUserLoggedIn spuštěn. isUserLoggedIn: $isLoggedIn") // Log stavu
            if (!isLoggedIn) { // Pokud ViewModel signalizuje, že uživatel NENÍ přihlášen (např. token byl smazán při logoutu)
                println("SearchActivity: isUserLoggedIn je false (žádný token nalezen?). Přesměrovávám na LoginActivity.")
                // Přesměrovat zpět na LoginActivity a vymazat Task Stack
                val intent = Intent(this, MainActivity::class.java) // Intent pro MainActivity (ZKONTROLUJTE CESTU)
                // Tyto flags zajistí, že se LoginActivity stane novým kořenem Tasku
                // a SearchActivity (a vše ostatní v jejím Tasku) bude ukončeno.
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish() // **Ukončit SearchActivity**
                println("SearchActivity: finish() voláno po přesměrování na LoginActivity.")
            } else {
                // Uživatel je přihlášen (má token) - nic nedělat, zůstat na SearchActivity
                println("SearchActivity: isUserLoggedIn je true (token nalezen). Pokračuji v SearchActivity.")
                // Zde můžete případně spustit prvotní načtení dat pro SearchActivity,
                // pokud se to neděje automaticky v init ViewModelu a chcete to spustit zde.
            }
        } // Konec observeru
        println("SearchActivity: Observer isUserLoggedIn nastaven.")


        // --- Posluchači na tlačítka a EditText ---

        // Posluchač na tlačítko Hledat
        println("SearchActivity: Nastavuji OnClickListener pro tlačítko Hledat.") // Log
        binding.buttonSearch.setOnClickListener { // Předpokládá ID buttonSearch v layoutu
            println("SearchActivity: Kliknuto na tlačítko Hledat.") // Log
            val query = binding.editTextSearchQuery.text.toString().trim() // Předpokládá ID editTextSearchQuery v layoutu

            val selectedCategoryIndex = binding.spinnerCategory.selectedItemPosition // Předpokládá ID spinnerCategory v layoutu
            val categoriesApiValues = resources.getStringArray(R.array.search_categories_api_values) // Použití R.array

            val selectedCategoryApiValue = if (selectedCategoryIndex >= 0 && selectedCategoryIndex < categoriesApiValues.size) {
                categoriesApiValues[selectedCategoryIndex]
            } else {
                "" // Výchozí prázdná kategorie (nebo null, záleží na API)
            }

            // Spustit vyhledávání POUZE pokud query není prázdná a uživatel je (podle ViewModelu) přihlášen
            if (query.isNotEmpty()) {
                // Kontrola stavu přihlášení z ViewModelu před spuštěním vyhledávání
                if (viewModel.isUserLoggedIn.value == true) { // Kontrola pomocí LiveData
                    println("SearchActivity: Spouštím vyhledávání pro dotaz: '$query', kategorie: '$selectedCategoryApiValue'") // Log
                    viewModel.search(query, category = selectedCategoryApiValue) // Volání search metody ViewModelu
                } else {
                    // Mělo by se již přesměrovat observerem, ale pro jistotu
                    println("SearchActivity: Pokus o vyhledávání bez přihlášení.") // Log
                    Toast.makeText(this, "Pro vyhledávání se prosím přihlaste.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Vyhledávací dotaz nesmí být prázdný.", Toast.LENGTH_SHORT).show()
                println("SearchActivity: Pokus o prázdné vyhledávání.") // Log
            }

            // Skrýt klávesnici po kliknutí na tlačítko
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.root.windowToken, 0) // Použití binding.root.windowToken
            println("SearchActivity: Klávesnice skryta.") // Log
        }

        // Posluchač na stisknutí "Hledat" na klávesnici
        binding.editTextSearchQuery.setOnEditorActionListener { v, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                binding.buttonSearch.performClick() // Simulace kliknutí na tlačítko Hledat
                true // Událost zpracována
            } else {
                false // Událost nezpracována
            }
        }

        // Posluchač na tlačítko Nastavení
        println("SearchActivity: Nastavuji OnClickListener pro tlačítko Nastavení.") // Log
        binding.buttonSettings.setOnClickListener { // Předpokládá ID buttonSettings v layoutu
            println("SearchActivity: Kliknuto na tlačítko Nastavení.") // Log
            val intent = Intent(this, SettingsActivity::class.java) // Intent pro SettingsActivity (ZKONTROLUJTE CESTU)
            // Při navigaci na SettingsActivity můžeme nechat standardní chování zásobníku,
            // aby se uživatel mohl vrátit tlačítkem Zpět na SearchActivity.
            startActivity(intent) // Spustí SettingsActivity
        }

        println("SearchActivity: <<< onCreate dokončen.") // Log na konci metody
    }

    // Metoda volaná, když Activity končí
    override fun onDestroy() {
        println("SearchActivity: >>> onDestroy spuštěn. PID: ${android.os.Process.myPid()}") // Log
        // Důležité: Odstranit posluchač posunu, aby nedošlo k memory leaku nebo chybám
        if (::binding.isInitialized) { // Ověřit, zda byl binding inicializován před použitím
            binding.recyclerViewSearchResults.removeOnScrollListener(scrollListener) // Předpokládá ID recyclerViewSearchResults
        }
        super.onDestroy()
        println("SearchActivity: <<< onDestroy dokončen.") // Log
    }

    // TODO: SearchActivity potřebuje svůj vlastní layout activity_search.xml
    // který obsahuje UI prvky jako jsou editTextSearchQuery, spinnerCategory, buttonSearch,
    // recyclerViewSearchResults, textViewSearchMessage, progressBarSearch, buttonSettings
    // s odpovídajícími ID.
    // Ujistěte se, že máte povolený View Binding pro activity_search.xml v build.gradle.

    // TODO: SearchActivity také předpokládá existenci SearchAdapter.kt
    // a SearchViewModelFactory.kt
}