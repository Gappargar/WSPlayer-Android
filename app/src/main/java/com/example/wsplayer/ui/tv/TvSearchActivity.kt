package com.example.wsplayer.ui.tv // Ujistěte se, že balíček odpovídá

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.example.wsplayer.R
import com.example.wsplayer.data.models.FileModel // <-- Přidán import
import com.example.wsplayer.databinding.ActivityTvSearchBinding // <-- ViewBinding pro TV Search
import java.text.NumberFormat

/**
 * Aktivita, která hostuje TvSearchFragment pro vyhledávání na Android TV.
 * Implementuje OnFileSelectedInSearchListener pro zobrazení detailů.
 */
class TvSearchActivity : FragmentActivity(), TvSearchFragment.OnFileSelectedInSearchListener {

    private val TAG = "TvSearchActivity"
    private lateinit var binding: ActivityTvSearchBinding // ViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Použití ViewBindingu
        binding = ActivityTvSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "onCreate")

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.tv_search_fragment_container, TvSearchFragment())
                .commitNow()
            Log.d(TAG, "TvSearchFragment added to container.")
        }
    }

    // ***** IMPLEMENTACE INTERFACE OnFileSelectedInSearchListener *****
    override fun onFileSelectedInSearch(file: FileModel?) {
        if (file != null) {
            // Použití ID z layoutu activity_tv_search.xml
            binding.tvSearchDetailTitle.text = file.name
            binding.tvSearchDetailType.text = "Typ: ${file.type?.uppercase() ?: "N/A"}"
            binding.tvSearchDetailSize.text = "Velikost: ${formatFileSize(file.size)}"
            val positiveVotes = file.positive_votes ?: 0
            val negativeVotes = file.negative_votes ?: 0
            binding.tvSearchDetailVotes.text = "Hlasy: +$positiveVotes / -$negativeVotes"
            // Další detaily...

            binding.searchDetailsPanel.visibility = View.VISIBLE
            Log.d(TAG, "Search details updated for: ${file.name}")
        } else {
            binding.searchDetailsPanel.visibility = View.GONE
            Log.d(TAG, "Search details panel hidden.")
        }
    }
    // ********************************************************

    // Pomocná funkce pro formátování velikosti souboru
    private fun formatFileSize(sizeInBytes: Long): String {
        if (sizeInBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
        val safeDigitGroups = digitGroups.coerceIn(0, units.size - 1)
        val nf = NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = 1
        }
        return "${nf.format(sizeInBytes / Math.pow(1024.0, safeDigitGroups.toDouble()))} ${units[safeDigitGroups]}"
    }
}
