package com.example.wsplayer.ui.tv.presenters // Ujistěte se, že balíček odpovídá

import android.graphics.drawable.Drawable
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.ViewGroup
import coil.load // Knihovna Coil pro načítání obrázků
import com.example.wsplayer.R // Váš R soubor
import com.example.wsplayer.data.models.FileModel // Váš datový model
// Import pro Leanback R
import androidx.leanback.R as LeanbackR

class CardPresenter : Presenter() {
    private val TAG = "CardPresenter"
    private var defaultCardImage: Drawable? = null
    private var selectedBackgroundColor: Int = 0
    private var defaultBackgroundColor: Int = 0


    companion object {
        private const val CARD_WIDTH = 313 // dp, typická šířka pro karty v Leanback
        private const val CARD_HEIGHT = 176 // dp, typická výška pro karty v Leanback
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        Log.d(TAG, "onCreateViewHolder")

        defaultCardImage = ContextCompat.getDrawable(parent.context, R.drawable.default_background)
        // Načtení barev pro pozadí
        selectedBackgroundColor = ContextCompat.getColor(parent.context, R.color.tv_card_selected_info_background)
        defaultBackgroundColor = ContextCompat.getColor(parent.context, R.color.tv_card_default_info_background)


        val cardView = object : ImageCardView(parent.context) {
            // Přepsání setSelected pro změnu barvy pozadí informační oblasti
            override fun setSelected(selected: Boolean) {
                updateCardBackgroundColor(this, selected)
                super.setSelected(selected) // Důležité zavolat super metodu
            }
        }

        cardView.isFocusable = true
        cardView.isFocusableInTouchMode = true
        updateCardBackgroundColor(cardView, false) // Nastavení počáteční barvy pozadí
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val file = item as FileModel
        val cardView = viewHolder.view as ImageCardView

        Log.d(TAG, "onBindViewHolder for: ${file.name}")

        cardView.cardType = ImageCardView.CARD_TYPE_INFO_UNDER_WITH_EXTRA
        cardView.titleText = file.name

        val fileTypeDisplay = if (file.type.isNullOrEmpty()) "?" else file.type.uppercase()
        var content = "$fileTypeDisplay - ${formatFileSize(file.size)}"
        if (!file.displayDate.isNullOrEmpty()) {
            content += " (${file.displayDate})"
        }
        cardView.contentText = content

        cardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)

        cardView.mainImageView.load(file.img) {
            placeholder(defaultCardImage)
            error(defaultCardImage)
        }
        // Resetování barvy pozadí při bindování, pro případ, že se view recykluje
        updateCardBackgroundColor(cardView, cardView.isSelected)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        Log.d(TAG, "onUnbindViewHolder")
        val cardView = viewHolder.view as ImageCardView
        cardView.badgeImage = null
        cardView.mainImage = null
    }

    private fun formatFileSize(sizeInBytes: Long): String {
        if (sizeInBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
        val safeDigitGroups = digitGroups.coerceIn(0, units.size - 1)
        return String.format("%.1f %s", sizeInBytes / Math.pow(1024.0, safeDigitGroups.toDouble()), units[safeDigitGroups])
    }

    // Metoda pro aktualizaci barvy pozadí informační oblasti karty
    private fun updateCardBackgroundColor(cardView: ImageCardView, selected: Boolean) {
        val color = if (selected) selectedBackgroundColor else defaultBackgroundColor
        // ***** OPRAVA ZDE: Použití metody setInfoAreaBackgroundColor *****
        cardView.setInfoAreaBackgroundColor(color)
        Log.d(TAG, "Updating card info area background color. Selected: $selected, Color: $color")
    }
}
