package com.example.wsplayer.ui.tv.compose // Nebo váš preferovaný balíček

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import com.example.wsplayer.data.models.FileModel
import com.example.wsplayer.data.models.SeriesEpisodeFile
import java.util.ArrayList

class ComposeEpisodeSelectionDialogFragment : DialogFragment() {

    private val TAG = "ComposeEpisodeSelection"

    interface OnEpisodeFileSelectedListener {
        fun onEpisodeFileSelected(selectedFileModel: FileModel)
        // Můžeme přidat i onDialogDismissed() pokud je potřeba
    }

    private var listener: OnEpisodeFileSelectedListener? = null
    private var episodeFiles: List<SeriesEpisodeFile>? = null
    private var dialogTitleFromArgs: String? = "Vyberte verzi souboru"

    companion object {
        private const val ARG_EPISODE_FILES = "arg_episode_files"
        private const val ARG_DIALOG_TITLE = "arg_dialog_title"

        fun newInstance(
            files: List<SeriesEpisodeFile>,
            title: String?
        ): ComposeEpisodeSelectionDialogFragment {
            val fragment = ComposeEpisodeSelectionDialogFragment()
            val args = Bundle()
            args.putParcelableArrayList(ARG_EPISODE_FILES, ArrayList(files))
            args.putString(ARG_DIALOG_TITLE, title ?: "Vyberte verzi souboru")
            fragment.arguments = args
            return fragment
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Získání listeneru z cílového fragmentu nebo aktivity
        if (targetFragment is OnEpisodeFileSelectedListener) {
            listener = targetFragment as OnEpisodeFileSelectedListener
        } else if (context is OnEpisodeFileSelectedListener) {
            listener = context
        } else {
            Log.e(TAG, "Host (Fragment or Activity) must implement OnEpisodeFileSelectedListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            episodeFiles = it.getParcelableArrayList(ARG_EPISODE_FILES)
            dialogTitleFromArgs = it.getString(ARG_DIALOG_TITLE)
        }
        if (episodeFiles == null) {
            Log.e(TAG, "Episode files list is null, dismissing dialog.")
            dismissAllowingStateLoss()
        }
        // Nastavení stylu dialogu pro TV (může být potřeba pro lepší vzhled/velikost)
        // Například, aby neměl standardní rámeček a titulek DialogFragmentu
        // setStyle(STYLE_NO_FRAME, R.style.Theme_WSPlayer_Leanback_Dialog) // Potřebovali byste definovat tento styl
        // Pro jednoduchost zatím necháme výchozí styl DialogFragmentu, který obalí ComposeView
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Vytvoření ComposeView
        return ComposeView(requireContext()).apply {
            // Dispose the Composition when the view's LifecycleOwner
            // is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                // Zde použijeme naši Composable funkci pro zobrazení obsahu dialogu
                // Je dobré obalit do MaterialTheme, aby se aplikovaly správné styly
                MaterialTheme { // Použijte vaši hlavní Compose téma, pokud máte
                    EpisodeSelectionDialogView(
                        title = dialogTitleFromArgs ?: "Vyberte soubor",
                        episodeFiles = episodeFiles ?: emptyList(),
                        onFileSelected = { selectedEpisodeFile ->
                            Log.d(TAG, "File selected in Compose dialog: ${selectedEpisodeFile.fileModel.name}")
                            listener?.onEpisodeFileSelected(selectedEpisodeFile.fileModel)
                            dismiss() // Zavřít dialog po výběru
                        },
                        onDismissRequest = {
                            Log.d(TAG, "Compose dialog dismiss requested.")
                            dismiss() // Zavřít dialog
                        }
                    )
                }
            }
        }
    }

    // Volitelné: Přizpůsobení velikosti a pozice dialogu, pokud je potřeba
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.7).toInt(), // Např. 70% šířky obrazovky
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        // Můžete také nastavit gravity, např. Gravity.CENTER
    }


    override fun onDetach() {
        super.onDetach()
        listener = null
    }
}
