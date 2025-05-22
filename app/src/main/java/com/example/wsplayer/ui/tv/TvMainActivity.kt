package com.example.wsplayer.ui.tv

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.example.wsplayer.AuthTokenManager
import com.example.wsplayer.R
import com.example.wsplayer.data.models.FileModel
import com.example.wsplayer.databinding.ActivityTvMainBinding
import com.example.wsplayer.ui.settings.SettingsActivity
import java.text.NumberFormat

class TvMainActivity : FragmentActivity(), TvBrowseFragment.OnFileSelectedListener {

    private val TAG = "TvMainActivity"
    private lateinit var binding: ActivityTvMainBinding
    private lateinit var authTokenManager: AuthTokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")

        authTokenManager = AuthTokenManager(this)
        val token = authTokenManager.getAuthToken()

        if (token.isNullOrEmpty()) {
            Log.w(TAG, "No token found, redirecting to CustomTvLoginActivity.")
            val loginIntent = Intent(this, CustomTvLoginActivity::class.java)
            loginIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(loginIntent)
            finish()
            return
        }

        Log.d(TAG, "Token found, setting up main TV content.")
        binding = ActivityTvMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.tv_main_fragment_container, TvBrowseFragment())
                .commitNow()
        }

        setupNavigationMenuListeners()
        binding.detailsPanel.visibility = View.GONE
    }

    private fun setupNavigationMenuListeners() {
        binding.navSearchButton.setOnClickListener {
            startActivity(Intent(this, CustomTvSearchActivity::class.java))
        }

        binding.navSettingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (authTokenManager.getAuthToken().isNullOrEmpty()) {
            val loginIntent = Intent(this, CustomTvLoginActivity::class.java)
            loginIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(loginIntent)
            finish()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            val currentFocused = currentFocus
            if (currentFocused != null && isChildOfFragmentContainer(currentFocused)) {
                val nextFocus = FocusFinder.getInstance().findNextFocus(
                    binding.tvMainFragmentContainer, currentFocused, View.FOCUS_LEFT
                )
                if (nextFocus == null) {
                    binding.navSearchButton.requestFocus()
                    return true
                } else {
                    nextFocus.requestFocus()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isChildOfFragmentContainer(view: View): Boolean {
        val fragmentContainer = binding.tvMainFragmentContainer
        var parent = view.parent
        while (parent is View) {
            if (parent == fragmentContainer) return true
            parent = parent.parent
        }
        return false
    }

    override fun onFileSelectedInBrowse(file: FileModel?) {
        if (file != null) {
            binding.tvDetailTitle.text = file.name
            binding.tvDetailType.text = "Typ: ${file.type?.uppercase() ?: "N/A"}"
            binding.tvDetailSize.text = "Velikost: ${formatFileSize(file.size)}"

            val positiveVotes = file.positive_votes ?: 0
            val negativeVotes = file.negative_votes ?: 0
            binding.tvDetailVotes.text = "Hlasy: +$positiveVotes / -$negativeVotes"

            var extraInfo = ""
            if (!file.videoQuality.isNullOrBlank()) extraInfo += "Kvalita: ${file.videoQuality} "
            if (!file.videoLanguage.isNullOrBlank()) extraInfo += "Jazyk: ${file.videoLanguage} "
            if (!file.displayDate.isNullOrBlank()) extraInfo += "(${file.displayDate})"

            binding.tvDetailInfo.text = extraInfo.trim()
            binding.tvDetailInfo.visibility = if (extraInfo.isNotBlank()) View.VISIBLE else View.GONE

            binding.detailsPanel.visibility = View.VISIBLE
        } else {
            binding.detailsPanel.visibility = View.GONE
        }
    }

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