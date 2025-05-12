package com.example.wsplayer.ui.tv

import android.content.Context
import android.content.Intent
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
import com.example.wsplayer.data.models.FileModel
import com.example.wsplayer.databinding.ActivityCustomTvSearchBinding // ViewBinding pro nový layout
import com.example.wsplayer.ui.search.SearchViewModel
import com.example.wsplayer.ui.search.SearchViewModelFactory // ***** SPRÁVNÝ IMPORT *****
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
        // ***** OPRAVA ZDE: Použití SearchViewModelFactory *****
        val factory = SearchViewModelFactory(application, apiService)
        viewModel = ViewModelProvider(this, factory)[SearchViewModel::class.java]
        // ****************************************************

        setupListeners()
        observeViewModel()

        if (savedInstanceState == null) {
            Log.d(TAG, "Adding initial TvSearchResultsFragment.")
            supportFragmentManager.beginTransaction()
                .replace(R.id.tv_search_results_container, TvSearchResultsFragment.newInstance(null, emptyList(), true))
                .commitNow()
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
                    Log.d(TAG, "Updating fragment with ${state.series.seasons.size} seasons and ${state.otherVideos.size} other videos.")
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.tv_search_results_container, TvSearchResultsFragment.newInstance(state.series, state.otherVideos))
                        .commitAllowingStateLoss()
                }
                is SeriesOrganizationState.Error -> {
                    binding.tvSearchStatus.text = "Chyba: ${state.message}"
                    binding.tvSearchStatus.visibility = View.VISIBLE
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.tv_search_results_container, TvSearchResultsFragment.newInstance(null, emptyList(), false, state.message))
                        .commitAllowingStateLoss()
                }
                is SeriesOrganizationState.Loading -> {
                    binding.tvSearchStatus.text = "Zpracovávám výsledky..."
                }
                is SeriesOrganizationState.Idle -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.tv_search_results_container, TvSearchResultsFragment.newInstance(null, emptyList(), true))
                        .commitAllowingStateLoss()
                }
            }
        }
    }

    override fun onFileSelectedInResults(file: FileModel?) {
        if (file != null) {
            binding.tvCustomSearchDetailTitle.text = file.name
            binding.tvCustomSearchDetailType.text = "Typ: ${file.type?.uppercase() ?: "N/A"}"
            binding.tvCustomSearchDetailSize.text = "Velikost: ${formatFileSize(file.size)}"
            var extraInfo = ""
            if (!file.videoQuality.isNullOrBlank()) extraInfo += "Kvalita: ${file.videoQuality} "
            if (!file.videoLanguage.isNullOrBlank()) extraInfo += "Jazyk: ${file.videoLanguage}"
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
