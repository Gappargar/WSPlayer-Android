package com.example.wsplayer.ui.tv.compose // Nebo váš preferovaný balíček

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager // Potřeba pro WindowManager.LayoutParams
import android.widget.Toast
import androidx.compose.material3.MaterialTheme // Pro obalení Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment // Dědíme od DialogFragment
import com.example.wsplayer.R // Váš R soubor
import com.example.wsplayer.data.models.FileModel
import com.example.wsplayer.data.models.SeriesEpisodeFile
import java.util.ArrayList
import android.os.Parcelable // Potřeba pro ArrayList<Parcelable>
// Import pro Leanback zdroje, pokud je potřebujete pro styl dialogu (např. Theme_Leanback_Dialog)
// import androidx.leanback.R as LeanbackR

class ComposeEpisodeSelectionDialogFragment : DialogFragment() {

    private val TAG = "ComposeEpisodeSelection"

    /**
     * Interface pro callback, když uživatel vybere soubor.
     */
    interface OnEpisodeFileSelectedListener {
        fun onEpisodeFileSelected(selectedFileModel: FileModel)
        // Můžete přidat onDialogDismissed(), pokud potřebujete reagovat na zrušení
    }

    private var listener: OnEpisodeFileSelectedListener? = null
    private var episodeFilesArg: ArrayList<SeriesEpisodeFile>? = null // Pro uchování argumentů
    private var dialogTitleFromArgs: String? = "Vyberte verzi souboru" // Výchozí titulek

    companion object {
        private const val ARG_EPISODE_FILES = "arg_episode_files"
        private const val ARG_DIALOG_TITLE = "arg_dialog_title"

        /**
         * Vytvoří novou instanci ComposeEpisodeSelectionDialogFragment.
         * @param files Seznam souborů (verzí) epizody.
         * @param title Titulek dialogu.
         */
        fun newInstance(
            files: List<SeriesEpisodeFile>, // Přijímáme List
            title: String?
        ): ComposeEpisodeSelectionDialogFragment {
            val fragment = ComposeEpisodeSelectionDialogFragment()
            val args = Bundle()
            // SeriesEpisodeFile musí být Parcelable
            val parcelableList = ArrayList<Parcelable>()
            files.forEach { file -> // 'file' je zde SeriesEpisodeFile, který je Parcelable
                parcelableList.add(file)
            }
            args.putParcelableArrayList(ARG_EPISODE_FILES, parcelableList)
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
            Log.d(TAG, "Listener set from targetFragment.")
        } else if (context is OnEpisodeFileSelectedListener) {
            listener = context
            Log.d(TAG, "Listener set from host activity.")
        } else {
            Log.w(TAG, "Host (Fragment or Activity) does not implement OnEpisodeFileSelectedListener. Callback will not be invoked.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            episodeFilesArg = it.getParcelableArrayList(ARG_EPISODE_FILES) // Načtení ArrayListu
            dialogTitleFromArgs = it.getString(ARG_DIALOG_TITLE)
            Log.d(TAG, "Arguments received: title='${dialogTitleFromArgs}', files=${episodeFilesArg?.size ?: 0}")
        }
        if (episodeFilesArg == null) {
            Log.e(TAG, "Episode files list is null, dismissing dialog.")
            // Pokud chybí data, je bezpečnější dialog zavřít až v onStart nebo onCreateView,
            // kdy je jisté, že fragment je plně připojen.
            // Prozatím to necháme takto, ale pokud by padalo zde, přesuneme.
            if (isAdded && !isStateSaved) { // Kontrola, zda je fragment připojen a stav není uložen
                dismissAllowingStateLoss()
            }
        }
        // Nastavení stylu dialogu pro TV, aby neměl standardní rámeček a titulek DialogFragmentu
        // a aby se správně přizpůsobil obsahu Compose.
        // STYLE_NO_FRAME odstraní standardní rámeček dialogu.
        // 0 jako druhý argument znamená, že se nepoužije žádné speciální téma z resources,
        // spoléháme na to, že ComposeView si vykreslí vlastní pozadí a styl.
        setStyle(STYLE_NO_FRAME, 0)
    }

    @OptIn(ExperimentalComposeUiApi::class) // Potřeba pro FocusRequester
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Získání aktuálních souborů z argumentů, pro případ, že onCreate bylo přeskočeno
        // nebo pro jistotu
        val currentFiles = arguments?.getParcelableArrayList<SeriesEpisodeFile>(ARG_EPISODE_FILES) ?: episodeFilesArg ?: emptyList()
        val currentTitle = arguments?.getString(ARG_DIALOG_TITLE) ?: dialogTitleFromArgs ?: "Vyberte soubor"

        return ComposeView(requireContext()).apply {
            // Dispose the Composition when the view's LifecycleOwner is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                // Použijte vaši hlavní Compose téma, pokud máte (např. WSPlayerTheme { ... })
                // Prozatím použijeme základní MaterialTheme
                MaterialTheme {
                    val focusRequester = remember { FocusRequester() }
                    EpisodeSelectionDialogView(
                        modifier = Modifier.focusRequester(focusRequester), // Předání focusRequesteru
                        title = currentTitle,
                        episodeFiles = currentFiles,
                        onFileSelected = { selectedEpisodeFile ->
                            Log.d(TAG, "File selected in Compose dialog: ${selectedEpisodeFile.fileModel.name}")
                            listener?.onEpisodeFileSelected(selectedEpisodeFile.fileModel)
                            dismiss() // Zavřít dialog po výběru
                        },
                        onDismissRequest = {
                            Log.d(TAG, "Compose dialog dismiss requested (e.g. back press).")
                            dismiss() // Zavřít dialog
                        }
                    )
                    // Požádat o fokus pro LazyColumn (nebo první položku) po zobrazení
                    LaunchedEffect(currentFiles) { // Spustí se, když se změní currentFiles (nebo při první kompozici)
                        if (currentFiles.isNotEmpty()) {
                            Log.d(TAG, "Requesting focus for EpisodeSelectionDialogView (LazyColumn)")
                            try {
                                focusRequester.requestFocus()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error requesting focus for LazyColumn: ${e.message}")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            // Nastavení rozměrů dialogu
            val width = (resources.displayMetrics.widthPixels * 0.70).toInt() // Např. 70% šířky obrazovky
            val height = ViewGroup.LayoutParams.WRAP_CONTENT // Výška se přizpůsobí obsahu
            window.setLayout(width, height)
            // Nastavení pozadí okna dialogu na transparentní, aby prosvítalo pozadí definované v Surface v EpisodeSelectionDialogView
            window.setBackgroundDrawableResource(android.R.color.transparent)
            // Zajištění, že okno dialogu může přijímat focus pro D-pad
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) // Potřebné pro některé window flags
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) // Ujistit se, že okno MŮŽE mít focus
            Log.d(TAG, "Dialog window focusability configured.")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null // Důležité pro uvolnění reference, aby se předešlo memory leakům
    }
}
