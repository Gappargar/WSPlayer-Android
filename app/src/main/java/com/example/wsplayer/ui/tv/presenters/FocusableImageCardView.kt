package com.example.wsplayer.ui.tv.presenters

import android.content.Context
import android.util.AttributeSet
import androidx.leanback.widget.ImageCardView
import androidx.leanback.R as LeanbackR // Import pro Leanback zdroje

/**
 * Vlastní ImageCardView, která umožňuje externě nastavit callback
 * pro změnu stavu selected (focus).
 */
class FocusableImageCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = LeanbackR.attr.imageCardViewStyle // Použití LeanbackR pro atribut
) : ImageCardView(context, attrs, defStyleAttr) {

    var onSelectedChangedCallback: ((Boolean) -> Unit)? = null

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        onSelectedChangedCallback?.invoke(selected)
    }
}
