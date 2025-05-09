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
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) // Šířka na celou šířku řádku
            isFocusable = true
            isFocusableInTouchMode = true
            // Použití vlastního drawable pro lepší vzhled tlačítka
            background = ContextCompat.getDrawable(context, R.drawable.load_more_background)
            setPadding(48, 24, 48, 24) // Větší padding
            gravity = Gravity.CENTER
            // minWidth = 250 // Můžete nastavit, pokud chcete
            text = context.getString(R.string.load_more)
            setTextColor(ContextCompat.getColor(context, LeanbackR.color.lb_tv_white))
            // Můžete nastavit větší text, pokud je potřeba
            // setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f) // Příklad
            // Nastavení focus highlight (může být potřeba vlastní, pokud výchozí Leanback nestačí)
            // focusHighlight = FocusHighlight.ZOOM_FACTOR_SMALL // Příklad
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
