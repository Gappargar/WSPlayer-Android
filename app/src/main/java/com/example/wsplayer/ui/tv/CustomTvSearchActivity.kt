package com.example.wsplayer.ui.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import coil.load
import com.example.wsplayer.R
import com.example.wsplayer.data.api.WebshareApiService
import com.example.wsplayer.data.models.FileLinkState
import com.example.wsplayer.data.models.FileModel
import com.example.wsplayer.databinding.ActivityCustomTvSearchBinding
import com.example.wsplayer.ui.search.SearchViewModel
import com.example.wsplayer.ui.search.SearchViewModelFactory
import com.example.wsplayer.ui.search.SeriesOrganizationState
import com.example.wsplayer.ui.settings.SettingsActivity
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
        val factory = SearchViewModelFactory(application, apiService)
        viewModel = ViewModelProvider(this, factory)[SearchViewModel::class.java]

        setupNavigationMenuListeners()
        setupListeners()
        observeViewModel()
        observeFileLinkState()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.tv_search_results_container, TvSearchResultsFragment.newInstance(null, emptyList(), true))
                .commitNow()
            binding.customSearchDetailsPanel.visibility = View.GONE
        }
        binding.etSearchQueryTv.requestFocus()
        showKeyboard(binding.etSearchQueryTv)
    }

    private fun setupNavigationMenuListeners() {
        binding.navSearchButton.setOnClickListener {
            binding.etSearchQueryTv.requestFocus()
        }
        binding.navSettingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
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
            binding.tvSearchStatus.text = "Vyhledávám \"$query\"..."
            binding.tvSearchStatus.visibility = View.VISIBLE
            viewModel.searchAndOrganizeSeries(query)
        } else {
            Toast.makeText(this, "Zadejte hledaný výraz", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        viewModel.seriesOrganizationState.observe(this) { state ->
            Log.d(TAG, "SeriesOrganizationState changed in Activity: $state")
            binding.tvSearchStatus.visibility = if (state is SeriesOrganizationState.Loading) View.VISIBLE else View.GONE

            when (state) {
                is SeriesOrganizationState.Success -> {
                    binding.tvSearchStatus.visibility = View.GONE
                    supportFragmentManager.beginTransaction()
                        .replace(
                            R.id.tv_search_results_container,
                            TvSearchResultsFragment.newInstance(state.series, state.otherVideos)
                        )
                        .commitAllowingStateLoss()
                    Handler(Looper.getMainLooper()).postDelayed({
                        focusResultsGrid()
                    }, 350)
                }
                is SeriesOrganizationState.Error -> {
                    binding.tvSearchStatus.text = "Chyba: ${state.message}"
                    binding.tvSearchStatus.visibility = View.VISIBLE
                    supportFragmentManager.beginTransaction()
                        .replace(
                            R.id.tv_search_results_container,
                            TvSearchResultsFragment.newInstance(null, emptyList(), false, state.message)
                        )
                        .commitAllowingStateLoss()
                }
                is SeriesOrganizationState.Loading -> {
                    binding.tvSearchStatus.text = "Zpracovávám výsledky..."
                }
                is SeriesOrganizationState.Idle -> {
                    supportFragmentManager.beginTransaction()
                        .replace(
                            R.id.tv_search_results_container,
                            TvSearchResultsFragment.newInstance(null, emptyList(), true)
                        )
                        .commitAllowingStateLoss()
                }
            }
        }
    }

    private fun focusResultsGrid() {
        val fragment = supportFragmentManager.findFragmentById(R.id.tv_search_results_container)
        if (fragment is TvSearchResultsFragment) {
            fragment.requestFocusOnResults()
        }
    }

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
                    viewModel.resetFileLinkState()
                }
                is FileLinkState.Error -> {
                    Log.e(TAG, "FileLinkState: Error in CustomTvSearchActivity - ${state.message}")
                    if (!state.message.contains("přihlášení", ignoreCase = true)) {
                        Toast.makeText(this, getString(R.string.link_error_toast, state.message), Toast.LENGTH_LONG).show()
                    }
                    viewModel.resetFileLinkState()
                }
                is FileLinkState.Idle -> {
                    Log.d(TAG, "FileLinkState: Idle in CustomTvSearchActivity")
                }
            }
        }
    }

    override fun onFileSelectedInResults(file: FileModel?) {
        if (file != null) {
            binding.tvCustomSearchDetailTitle.text = file.name
            binding.tvCustomSearchDetailType.text = "Typ: ${file.type?.uppercase() ?: "N/A"}"
            binding.tvCustomSearchDetailSize.text = "Velikost: ${formatFileSize(file.size)}"

            // Výčet všech dostupných informací
            val infoParts = mutableListOf<String>()
            if (!file.videoQuality.isNullOrBlank()) infoParts.add("Kvalita: ${file.videoQuality}")
            if (!file.videoLanguage.isNullOrBlank()) infoParts.add("Jazyk: ${file.videoLanguage}")
            if (!file.seriesName.isNullOrBlank()) infoParts.add("Seriál: ${file.seriesName}")
            if (file.seasonNumber != null) infoParts.add("Série: ${file.seasonNumber}")
            if (file.episodeNumber != null) infoParts.add("Epizoda: ${file.episodeNumber}")
            if (!file.episodeTitle.isNullOrBlank()) infoParts.add("Název dílu: ${file.episodeTitle}")
            if (file.positive_votes != null) infoParts.add("Hodnocení +: ${file.positive_votes}")
            if (file.negative_votes != null) infoParts.add("Hodnocení -: ${file.negative_votes}")
            if (!file.displayDate.isNullOrBlank()) infoParts.add("Datum: ${file.displayDate}")
            if (file.password == 1) infoParts.add("⚠️ Zamčeno heslem")
            if (file.queued == 1) infoParts.add("Ve frontě ke stažení")

            // Stripe obrázek (nově místo obrázku)
            val stripeImageView = binding.root.findViewById<ImageView>(R.id.tvCustomSearchDetailStripe)
            if (stripeImageView != null && !file.stripe.isNullOrBlank()) {
                stripeImageView.visibility = View.VISIBLE
                stripeImageView.load(file.stripe)
            } else if (stripeImageView != null) {
                stripeImageView.visibility = View.GONE
            }

            binding.tvCustomSearchDetailInfo.text = infoParts.joinToString(" • ")
            binding.tvCustomSearchDetailInfo.visibility = if (infoParts.isNotEmpty()) View.VISIBLE else View.GONE
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
