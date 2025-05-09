package com.example.wsplayer.ui.tv.presenters // Ujistěte se, že balíček odpovídá

import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import com.example.wsplayer.R // Váš R soubor
// Import pro Leanback R už není potřeba pro pozadí, ale necháme pro barvu textu
import androidx.leanback.R as LeanbackR

/**
 * Presenter for displaying a "Load More" action item.
 */
class LoadMorePresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val textView = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            isFocusable = true
            isFocusableInTouchMode = true
            // ***** Použití vlastního drawable *****
            background = ContextCompat.getDrawable(context, R.drawable.load_more_background) // Použití vašeho nového drawable
            setPadding(32, 16, 32, 16)
            gravity = Gravity.CENTER
            minWidth = 200
            text = context.getString(R.string.load_more) // Text tlačítka z vašich stringů
            setTextColor(ContextCompat.getColor(context, LeanbackR.color.lb_tv_white)) // Barva textu může zůstat z Leanback
        }
        return ViewHolder(textView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        // No binding needed as the text is static
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder?) {
        // Nothing to unbind
    }
}
