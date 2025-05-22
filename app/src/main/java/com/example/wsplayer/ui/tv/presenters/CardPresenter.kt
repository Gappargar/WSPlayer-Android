package com.example.wsplayer.ui.tv.presenters

import android.graphics.drawable.Drawable
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.wsplayer.R
import com.example.wsplayer.data.models.FileModel
import java.text.NumberFormat

class CardPresenter : Presenter() {
    private val TAG = "CardPresenter"
    private var defaultCardImage: Drawable? = null
    private var selectedBackgroundColor: Int = 0
    private var defaultBackgroundColor: Int = 0

    companion object {
        private const val CARD_IMAGE_WIDTH_DP = 140
        private const val CARD_IMAGE_HEIGHT_DP = 78
        private const val CARD_HORIZONTAL_MARGIN_DP = 18
        private const val CARD_VERTICAL_MARGIN_DP = 16
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        Log.d(TAG, "onCreateViewHolder")
        val context = parent.context

        defaultCardImage = ContextCompat.getDrawable(context, R.drawable.default_background)
        selectedBackgroundColor = ContextCompat.getColor(context, R.color.tv_card_selected_info_background)
        defaultBackgroundColor = ContextCompat.getColor(context, R.color.tv_card_default_info_background)

        val themedContext = ContextThemeWrapper(context, R.style.Theme_WSPlayer_Leanback)
        val inflater = LayoutInflater.from(themedContext)
        val cardView = inflater.inflate(R.layout.presenter_image_card_item, parent, false) as FocusableImageCardView

        cardView.onSelectedChangedCallback = { selected ->
            updateCardBackgroundColor(cardView, selected)
        }

        // Nastavíme mezery přímo na cardView
        val horizontalMarginPx = (CARD_HORIZONTAL_MARGIN_DP * context.resources.displayMetrics.density).toInt()
        val verticalMarginPx = (CARD_VERTICAL_MARGIN_DP * context.resources.displayMetrics.density).toInt()
        val lp = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.WRAP_CONTENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(horizontalMarginPx, verticalMarginPx, horizontalMarginPx, verticalMarginPx)
        cardView.layoutParams = lp

        updateCardBackgroundColor(cardView, false)
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        val cardView = viewHolder.view as FocusableImageCardView

        if (item == null) {
            Log.w(TAG, "onBindViewHolder called with null item.")
            return
        }
        val file = item as FileModel

        cardView.titleText = file.name

        val fileTypeDisplay = if (file.type.isNullOrEmpty()) "?" else file.type.uppercase()
        val details = mutableListOf<String>()
        details.add(fileTypeDisplay)
        details.add(formatFileSize(file.size))

        if (!file.videoQuality.isNullOrBlank()) {
            details.add("Kvalita: ${file.videoQuality}")
        }
        if (!file.videoLanguage.isNullOrBlank()) {
            details.add("Jazyk: ${file.videoLanguage}")
        }
        if (!file.displayDate.isNullOrEmpty()) {
            details.add("(${file.displayDate})")
        }
        cardView.contentText = details.joinToString(" - ")

        val cardWidthPx = (CARD_IMAGE_WIDTH_DP * cardView.context.resources.displayMetrics.density).toInt()
        val cardHeightPx = (CARD_IMAGE_HEIGHT_DP * cardView.context.resources.displayMetrics.density).toInt()
        cardView.setMainImageDimensions(cardWidthPx, cardHeightPx)

        cardView.mainImageView?.load(file.img) {
            placeholder(defaultCardImage)
            error(defaultCardImage)
        }

        cardView.nextFocusLeftId = R.id.nav_search_button
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        val cardView = viewHolder.view as FocusableImageCardView
        cardView.badgeImage = null
        cardView.mainImage = null
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

    private fun updateCardBackgroundColor(cardView: FocusableImageCardView, selected: Boolean) {
        val color = if (selected) selectedBackgroundColor else defaultBackgroundColor
        cardView.setInfoAreaBackgroundColor(color)
    }
}
