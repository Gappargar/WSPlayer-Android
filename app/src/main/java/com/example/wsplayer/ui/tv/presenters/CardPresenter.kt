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

    companion object {
        private const val CARD_WIDTH = 313 // dp, typická šířka pro karty v Leanback
        private const val CARD_HEIGHT = 176 // dp, typická výška pro karty v Leanback
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        Log.d(TAG, "onCreateViewHolder")

        // Načtení výchozího obrázku, který se použije jako placeholder nebo při chybě
        // Ujistěte se, že máte obrázek 'default_background' ve složce res/drawable
        defaultCardImage = ContextCompat.getDrawable(parent.context, R.drawable.default_background)


        val cardView = object : ImageCardView(parent.context) {
            // Volitelně: Můžete přepsat setSelected pro vlastní vizuální zpětnou vazbu
            // override fun setSelected(selected: Boolean) {
            //     super.setSelected(selected)
            //     // updateCardBackgroundColor(this, selected) // Příklad volání metody pro změnu barvy
            // }
        }

        cardView.isFocusable = true
        cardView.isFocusableInTouchMode = true
        // cardView.setBackgroundColor(ContextCompat.getColor(parent.context, LeanbackR.color.lb_default_card_background)) // Počáteční barva
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val file = item as FileModel // Přetypování položky na váš FileModel
        val cardView = viewHolder.view as ImageCardView // Přetypování view na ImageCardView

        Log.d(TAG, "onBindViewHolder for: ${file.name}")

        // Nastavení typu karty - INFO_UNDER_WITH_EXTRA zobrazí obrázek, titulek pod ním a další text
        cardView.cardType = ImageCardView.CARD_TYPE_INFO_UNDER_WITH_EXTRA
        cardView.titleText = file.name // Nastavení názvu souboru jako titulku karty

        // Ošetření null/prázdného typu
        val fileTypeDisplay = if (file.type.isNullOrEmpty()) "?" else file.type.uppercase()
        cardView.contentText = "$fileTypeDisplay - ${formatFileSize(file.size)}" // Příklad obsahu: typ souboru a velikost

        // Nastavení rozměrů hlavního obrázku karty
        cardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)

        // Načtení obrázku náhledu pomocí knihovny Coil
        // Pokud je img null nebo prázdný, Coil automaticky použije placeholder/error drawable
        cardView.mainImageView.load(file.img) {
            placeholder(defaultCardImage) // Zobrazí se během načítání obrázku
            error(defaultCardImage)       // Zobrazí se, pokud dojde k chybě při načítání
            // Můžete přidat další transformace nebo nastavení pro Coil
            // crossfade(true)
        }

        // Volitelně: Můžete nastavit "badge" obrázek (malá ikona v rohu karty)
        // cardView.badgeImage = ContextCompat.getDrawable(cardView.context, R.drawable.ic_hd_badge) // Příklad
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        Log.d(TAG, "onUnbindViewHolder")
        val cardView = viewHolder.view as ImageCardView
        // Uvolnění zdrojů, zejména hlavního obrázku, aby se předešlo memory leakům
        cardView.badgeImage = null
        cardView.mainImage = null // Důležité pro uvolnění obrázku drženého Coil/Glide/Picasso
    }

    // Pomocná funkce pro formátování velikosti souboru do čitelnější podoby
    private fun formatFileSize(sizeInBytes: Long): String {
        if (sizeInBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
        // Zajistíme, aby digitGroups nepřesáhlo rozsah pole units
        val safeDigitGroups = digitGroups.coerceIn(0, units.size - 1)
        return String.format("%.1f %s", sizeInBytes / Math.pow(1024.0, safeDigitGroups.toDouble()), units[safeDigitGroups])
    }

    // Příklad volitelné metody pro změnu barvy pozadí karty při výběru
    // private fun updateCardBackgroundColor(view: ImageCardView, selected: Boolean) {
    //     val colorRes = if (selected) LeanbackR.color.lb_basic_card_info_bg_color_selected else LeanbackR.color.lb_basic_card_info_bg_color // Použití Leanback barev
    //     val color = ContextCompat.getColor(view.context, colorRes)
    //     // view.setBackgroundColor(color) // Může změnit celé pozadí
    //     view.infoAreaBackgroundColor = color // Pro oblast pod obrázkem
    // }
}
