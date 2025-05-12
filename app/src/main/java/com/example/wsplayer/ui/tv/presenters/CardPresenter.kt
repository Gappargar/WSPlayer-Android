package com.example.wsplayer.ui.tv.presenters

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.leanback.widget.Presenter
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView // Důležité pro RecyclerView.LayoutParams
import coil.load
import com.example.wsplayer.R // Váš R soubor
import com.example.wsplayer.data.models.FileModel // Váš datový model
import java.text.NumberFormat
// Import pro Leanback R s aliasem, aby se předešlo konfliktu
import androidx.leanback.R as LeanbackR // Ujistěte se, že tento import je správný

class CardPresenter : Presenter() {
    private val TAG = "CardPresenter"
    private var defaultCardImage: Drawable? = null
    private var selectedBackgroundColor: Int = 0
    private var defaultBackgroundColor: Int = 0

    companion object {
        // Rozměry obrázku v kartě v dp
        private const val CARD_IMAGE_WIDTH_DP = 200
        private const val CARD_IMAGE_HEIGHT_DP = 112
        // Mezery mezi kartami v dp
        private const val CARD_HORIZONTAL_MARGIN_DP = 6
        private const val CARD_VERTICAL_MARGIN_DP = 6
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        Log.d(TAG, "onCreateViewHolder")
        val context = parent.context

        defaultCardImage = ContextCompat.getDrawable(context, R.drawable.default_background)
        selectedBackgroundColor = ContextCompat.getColor(context, R.color.tv_card_selected_info_background)
        defaultBackgroundColor = ContextCompat.getColor(context, R.color.tv_card_default_info_background)

        // Použijeme ContextThemeWrapper, aby se zajistilo, že layout se nafoukne se správnou Leanback tématem
        // Používáme vaši vlastní TV téma, která by měla dědit z Theme.Leanback
        val themedContext = ContextThemeWrapper(context, R.style.Theme_WSPlayer_Leanback)
        val inflater = LayoutInflater.from(themedContext)

        // Nafoukneme náš vlastní layout, který obsahuje FocusableImageCardView
        val cardView = inflater.inflate(R.layout.presenter_image_card_item, parent, false) as FocusableImageCardView

        // Nastavení callbacku pro změnu selekce/focusu
        cardView.onSelectedChangedCallback = { selected ->
            updateCardBackgroundColor(cardView, selected)
        }

        // Nastavení LayoutParams pro RecyclerView, aby se správně zobrazily mezery
        val horizontalMarginPx = (CARD_HORIZONTAL_MARGIN_DP * context.resources.displayMetrics.density).toInt()
        val verticalMarginPx = (CARD_VERTICAL_MARGIN_DP * context.resources.displayMetrics.density).toInt()
        val lp = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.WRAP_CONTENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(horizontalMarginPx, verticalMarginPx, horizontalMarginPx, verticalMarginPx)
        cardView.layoutParams = lp

        updateCardBackgroundColor(cardView, false) // Počáteční stav barvy pozadí
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        if (item == null) {
            Log.w(TAG, "onBindViewHolder called with null item.")
            // Můžete zde nastavit nějaký výchozí stav pro cardView, pokud je to potřeba
            // (viewHolder.view as? FocusableImageCardView)?.mainImage = defaultCardImage
            // (viewHolder.view as? FocusableImageCardView)?.titleText = "Chyba"
            // (viewHolder.view as? FocusableImageCardView)?.contentText = "Položka není dostupná"
            return
        }
        val file = item as FileModel
        val cardView = viewHolder.view as FocusableImageCardView

        // Log.d(TAG, "onBindViewHolder for: ${file.name}") // Může být příliš upovídané

        // cardType je již nastaven v XML (app:cardType="infoUnderWithExtra")
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
        if (!file.displayDate.isNullOrEmpty()) { // Pro zobrazení data z historie
            details.add("(${file.displayDate})")
        }
        cardView.contentText = details.joinToString(" - ")

        // Nastavení rozměrů hlavního obrázku karty
        val cardWidthPx = (CARD_IMAGE_WIDTH_DP * cardView.context.resources.displayMetrics.density).toInt()
        val cardHeightPx = (CARD_IMAGE_HEIGHT_DP * cardView.context.resources.displayMetrics.density).toInt()
        cardView.setMainImageDimensions(cardWidthPx, cardHeightPx)

        cardView.mainImageView?.load(file.img) { // Použití safe call ?.
            placeholder(defaultCardImage)
            error(defaultCardImage)
        }
        // Barva pozadí se aktualizuje přes onSelectedChangedCallback
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        // Log.d(TAG, "onUnbindViewHolder")
        val cardView = viewHolder.view as FocusableImageCardView
        // Uvolnění zdrojů
        cardView.badgeImage = null
        cardView.mainImage = null // Důležité pro Coil/Glide/Picasso, aby se obrázek uvolnil
    }

    // Pomocná funkce pro formátování velikosti souboru
    private fun formatFileSize(sizeInBytes: Long): String {
        if (sizeInBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
        val safeDigitGroups = digitGroups.coerceIn(0, units.size - 1)
        // Použití NumberFormat pro lokalizované formátování desetinných míst
        val nf = NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = 1
        }
        return "${nf.format(sizeInBytes / Math.pow(1024.0, safeDigitGroups.toDouble()))} ${units[safeDigitGroups]}"
    }

    // Metoda pro aktualizaci barvy pozadí informační oblasti karty
    private fun updateCardBackgroundColor(cardView: FocusableImageCardView, selected: Boolean) {
        val color = if (selected) selectedBackgroundColor else defaultBackgroundColor
        cardView.setInfoAreaBackgroundColor(color) // Správná metoda pro nastavení barvy
    }
}
