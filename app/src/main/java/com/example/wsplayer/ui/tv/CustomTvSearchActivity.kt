package com.example.wsplayer.ui.tv

import android.content.Context
import android.content.Intent // Potřeba pro spouštění PlayerActivity
import android.net.Uri // Potřeba pro Uri.parse()
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.wsplayer.R
import com.example.wsplayer.data.api.WebshareApiService
import com.example.wsplayer.data.models.FileLinkState // Import pro FileLinkState
import com.example.wsplayer.data.models.FileModel
import com.example.wsplayer.databinding.ActivityCustomTvSearchBinding // ViewBinding pro nový layout
import com.example.wsplayer.ui.search.SearchViewModel
import com.example.wsplayer.ui.search.SearchViewModelFactory // Správný ViewModelFactory
import com.example.wsplayer.ui.search.SeriesOrganizationState
import java.text.NumberFormat

class CustomTvSearchActivity : AppCompatActivity(), TvSearchResultsFragment.OnFileSelectedListener {

    private lateinit var binding: ActivityCustomTvSearchBinding
    private lateinit var viewModel: SearchViewModel
    private val TAG = "CustomTvSearchActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomTvSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "onCreate")

        val apiService = WebshareApiService.create()
        // Použití SearchViewModelFactory
        val factory = SearchViewModelFactory(application, apiService)
        viewModel = ViewModelProvider(this, factory)[SearchViewModel::class.java]

        setupListeners()
        observeViewModel()
        observeFileLinkState() // Přidáno pozorování stavu odkazu

        if (savedInstanceState == null) {
            // Na začátku zobrazíme fragment s výzvou
            Log.d(TAG, "Adding initial TvSearchResultsFragment.")
            supportFragmentManager.beginTransaction()
                .replace(R.id.tv_search_results_container, TvSearchResultsFragment.newInstance(null, emptyList(), true))
                .commitNow() // Použijeme commitNow pro okamžité zobrazení
            binding.customSearchDetailsPanel.visibility = View.GONE
        }
        binding.etSearchQueryTv.requestFocus()
        showKeyboard(binding.etSearchQueryTv)
    }

    private fun setupListeners() {
        binding.btnSearchTv.setOnClickListener {
            performSearch()
        }
        binding.etSearchQueryTv.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                performSearch()
                true
            } else {
                false
            }
        }
    }

    private fun performSearch() {
        val query = binding.etSearchQueryTv.text.toString().trim()
        if (query.isNotEmpty()) {
            hideKeyboard()
            binding.tvSearchStatus.text = "Vyhledávám \"$query\"..." // Český text
            binding.tvSearchStatus.visibility = View.VISIBLE
            // Voláme metodu pro seriály/filmy
            viewModel.searchAndOrganizeSeries(query)
        } else {
            Toast.makeText(this, "Zadejte hledaný výraz", Toast.LENGTH_SHORT).show() // Český text
        }
    }

    private fun observeViewModel() {
        viewModel.seriesOrganizationState.observe(this) { state ->
            Log.d(TAG, "SeriesOrganizationState changed in Activity: $state")
            binding.tvSearchStatus.visibility = if (state is SeriesOrganizationState.Loading) View.VISIBLE else View.GONE

            when (state) {
                is SeriesOrganizationState.Success -> {
                    binding.tvSearchStatus.visibility = View.GONE
                    Log.d(TAG, "Updating fragment with ${state.series.seasons.size} seasons and ${state.otherVideos.size} other videos.")
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.tv_search_results_container, TvSearchResultsFragment.newInstance(state.series, state.otherVideos))
                        .commitAllowingStateLoss() // Použijeme commitAllowingStateLoss pro jistotu při aktualizaci fragmentu
                }
                is SeriesOrganizationState.Error -> {
                    binding.tvSearchStatus.text = "Chyba: ${state.message}" // Český text
                    binding.tvSearchStatus.visibility = View.VISIBLE
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.tv_search_results_container, TvSearchResultsFragment.newInstance(null, emptyList(), false, state.message))
                        .commitAllowingStateLoss()
                }
                is SeriesOrganizationState.Loading -> {
                    binding.tvSearchStatus.text = "Zpracovávám výsledky..." // Český text
                }
                is SeriesOrganizationState.Idle -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.tv_search_results_container, TvSearchResultsFragment.newInstance(null, emptyList(), true))
                        .commitAllowingStateLoss()
                }
            }
        }
    }

    // Metoda pro pozorování stavu odkazu a spuštění přehrávače
    private fun observeFileLinkState() {
        viewModel.fileLinkState.observe(this) { state ->
            when (state) {
                is FileLinkState.LoadingLink -> {
                    Log.d(TAG, "FileLinkState: LoadingLink in CustomTvSearchActivity")
                    Toast.makeText(this, getString(R.string.getting_link_toast), Toast.LENGTH_SHORT).show()
                }
                is FileLinkState.LinkSuccess -> {
                    Log.d(TAG, "FileLinkState: LinkSuccess in CustomTvSearchActivity - URL: ${state.fileUrl}")
                    if (state.fileUrl.isNotEmpty()) {
                        val playIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(state.fileUrl), "video/*")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        Log.d(TAG, "Attempting to start player from CustomTvSearchActivity for URL: ${state.fileUrl}")
                        if (packageManager.resolveActivity(playIntent, 0) != null) {
                            startActivity(playIntent)
                        } else {
                            Log.e(TAG, "No activity found to handle ACTION_VIEW for video.")
                            Toast.makeText(this, getString(R.string.no_video_player_app_toast), Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Log.e(TAG, "FileLinkState: LinkSuccess but URL is empty")
                        Toast.makeText(this, getString(R.string.empty_link_error_toast), Toast.LENGTH_LONG).show()
                    }
                    viewModel.resetFileLinkState() // Resetovat stav po zpracování
                }
                is FileLinkState.Error -> {
                    Log.e(TAG, "FileLinkState: Error in CustomTvSearchActivity - ${state.message}")
                    if (!state.message.contains("přihlášení", ignoreCase = true)) {
                        Toast.makeText(this, getString(R.string.link_error_toast, state.message), Toast.LENGTH_LONG).show()
                    }
                    viewModel.resetFileLinkState() // Resetovat stav po zpracování
                }
                is FileLinkState.Idle -> {
                    Log.d(TAG, "FileLinkState: Idle in CustomTvSearchActivity")
                }
            }
        }
    }

    // Implementace listeneru z TvSearchResultsFragment pro zobrazení detailů
    override fun onFileSelectedInResults(file: FileModel?) {
        if (file != null) {
            binding.tvCustomSearchDetailTitle.text = file.name
            binding.tvCustomSearchDetailType.text = "Typ: ${file.type?.uppercase() ?: "N/A"}" // Český text
            binding.tvCustomSearchDetailSize.text = "Velikost: ${formatFileSize(file.size)}" // Český text
            var extraInfo = ""
            if (!file.videoQuality.isNullOrBlank()) extraInfo += "Kvalita: ${file.videoQuality} " // Český text
            if (!file.videoLanguage.isNullOrBlank()) extraInfo += "Jazyk: ${file.videoLanguage}" // Český text
            binding.tvCustomSearchDetailInfo.text = extraInfo.trim()
            binding.tvCustomSearchDetailInfo.visibility = if (extraInfo.isNotBlank()) View.VISIBLE else View.GONE

            binding.customSearchDetailsPanel.visibility = View.VISIBLE
        } else {
            binding.customSearchDetailsPanel.visibility = View.GONE
        }
    }

    private fun hideKeyboard() {
        val view = this.currentFocus
        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
    private fun showKeyboard(view: View) {
        if (view.requestFocus()) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun formatFileSize(sizeInBytes: Long): String {
        if (sizeInBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
        val safeDigitGroups = digitGroups.coerceIn(0, units.size - 1)
        val nf = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }
        return "${nf.format(sizeInBytes / Math.pow(1024.0, safeDigitGroups.toDouble()))} ${units[safeDigitGroups]}"
    }
}
