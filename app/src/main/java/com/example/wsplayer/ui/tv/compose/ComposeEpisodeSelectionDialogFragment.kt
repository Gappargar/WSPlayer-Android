package com.example.wsplayer.ui.tv.compose // Ujistěte se, že balíček odpovídá

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
// Importy pro FocusRequester a LaunchedEffect zde již nejsou potřeba, jsou v EpisodeSelectionDialogView
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
// import androidx.compose.ui.ExperimentalComposeUiApi
// import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import com.example.wsplayer.R // Váš R soubor
import com.example.wsplayer.data.models.FileModel
import com.example.wsplayer.data.models.SeriesEpisodeFile
import java.util.ArrayList
import android.os.Parcelable
// import kotlinx.coroutines.delay // Již není potřeba zde

class ComposeEpisodeSelectionDialogFragment : DialogFragment() {

    private val TAG = "ComposeEpisodeSelection"

    interface OnEpisodeFileSelectedListener {
        fun onEpisodeFileSelected(selectedFileModel: FileModel)
        fun onEpisodeSelectionCancelled()
    }

    private var listener: OnEpisodeFileSelectedListener? = null
    private var episodeFilesArg: ArrayList<SeriesEpisodeFile>? = null
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
            val parcelableList = ArrayList<Parcelable>() // Bundle vyžaduje ArrayList<Parcelable>
            files.forEach { file -> parcelableList.add(file) }
            args.putParcelableArrayList(ARG_EPISODE_FILES, parcelableList)
            args.putString(ARG_DIALOG_TITLE, title ?: "Vyberte verzi souboru")
            fragment.arguments = args
            return fragment
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (targetFragment is OnEpisodeFileSelectedListener) {
            listener = targetFragment as OnEpisodeFileSelectedListener
            Log.d(TAG, "Listener set from targetFragment.")
        } else if (context is OnEpisodeFileSelectedListener) {
            listener = context
            Log.d(TAG, "Listener set from host activity.")
        } else {
            Log.w(TAG, "Host (Fragment or Activity) does not implement OnEpisodeFileSelectedListener.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            episodeFilesArg = it.getParcelableArrayList(ARG_EPISODE_FILES)
            dialogTitleFromArgs = it.getString(ARG_DIALOG_TITLE)
            Log.d(TAG, "Arguments received: title='${dialogTitleFromArgs}', files=${episodeFilesArg?.size ?: 0}")
        }
        if (episodeFilesArg == null && savedInstanceState == null) {
            Log.e(TAG, "Episode files list is null from arguments, dismissing dialog.")
            if (isAdded && !isStateSaved) {
                dismissAllowingStateLoss()
            }
        }
        // Nastavení stylu dialogu pro TV, aby neměl standardní rámeček a titulek DialogFragmentu
        setStyle(STYLE_NO_FRAME, R.style.Theme_WSPlayer_Dialog_Transparent)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val currentFiles = episodeFilesArg ?: arguments?.getParcelableArrayList<SeriesEpisodeFile>(ARG_EPISODE_FILES) ?: emptyList()
        val currentTitle = dialogTitleFromArgs ?: arguments?.getString(ARG_DIALOG_TITLE) ?: "Vyberte soubor"

        if (currentFiles.isEmpty() && episodeFilesArg != null) {
            Log.w(TAG, "No files to display, even though arguments were present. Dismissing.")
            return View(requireContext())
        }

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            isFocusable = true
            isFocusableInTouchMode = true

            setContent {
                MaterialTheme {
                    EpisodeSelectionDialogView(
                        title = currentTitle,
                        episodeFiles = currentFiles,
                        onFileSelected = { selectedEpisodeFile ->
                            Log.d(TAG, "File selected in Compose dialog via lambda: ${selectedEpisodeFile.fileModel.name}")
                            listener?.onEpisodeFileSelected(selectedEpisodeFile.fileModel)
                            dismissAllowingStateLoss()
                        },
                        onDismissRequest = {
                            Log.d(TAG, "Compose dialog dismiss requested.")
                            listener?.onEpisodeSelectionCancelled()
                            dismissAllowingStateLoss()
                        }
                    )
                }
            }
        }
    }

    // 👇 Tyto metody MUSÍ být mimo `setContent`
    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            val width = (resources.displayMetrics.widthPixels * 0.75).toInt()
            val height = ViewGroup.LayoutParams.WRAP_CONTENT
            window.setLayout(width, height)
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            Log.d(TAG, "Dialog window configured for focus.")
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        Log.d(TAG, "Dialog dismissed via onDismiss callback.")
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        Log.d(TAG, "Dialog cancelled via onCancel callback.")
        listener?.onEpisodeSelectionCancelled()
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView - disposing composition.")
        (view as? ComposeView)?.disposeComposition()
        super.onDestroyView()
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
        Log.d(TAG, "Fragment detached, listener nulled.")
    }

}
