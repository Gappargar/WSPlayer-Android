package com.example.wsplayer.ui.tv // Ujistěte se, že balíček odpovídá

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog // Můžeme použít AlertDialog jako základ
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wsplayer.data.models.FileModel
import com.example.wsplayer.data.models.SeriesEpisodeFile
import com.example.wsplayer.databinding.DialogCustomEpisodeSelectionBinding // ViewBinding pro layout dialogu
import java.util.ArrayList

class CustomEpisodeSelectionDialogFragment : DialogFragment() {

    private val TAG = "CustomEpisodeSelection"
    private var _binding: DialogCustomEpisodeSelectionBinding? = null
    private val binding get() = _binding!!

    private var episodeFiles: List<SeriesEpisodeFile>? = null
    private var dialogTitleFromArgs: String? = "Vyberte verzi" // Výchozí titulek

    /**
     * Interface pro callback, když uživatel vybere soubor.
     */
    interface OnEpisodeFileSelectedListener {
        fun onEpisodeFileSelected(selectedFileModel: FileModel)
        // Můžeme přidat i onSelectionCancelled(), pokud je potřeba
    }

    private var listener: OnEpisodeFileSelectedListener? = null

    companion object {
        private const val ARG_EPISODE_FILES = "arg_episode_files"
        private const val ARG_DIALOG_TITLE = "arg_dialog_title"

        fun newInstance(
            files: List<SeriesEpisodeFile>,
            title: String?
        ): CustomEpisodeSelectionDialogFragment {
            val fragment = CustomEpisodeSelectionDialogFragment()
            val args = Bundle()
            args.putParcelableArrayList(ARG_EPISODE_FILES, ArrayList(files)) // Musí být ArrayList<Parcelable>
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
            dismissAllowingStateLoss() // Zavřít dialog, pokud nejsou data
        }
    }

    // Použijeme onCreateView pro inflaci vlastního layoutu
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCustomEpisodeSelectionBinding.inflate(inflater, container, false)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent) // Pro vlastní pozadí dialogu
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvDialogTitle.text = dialogTitleFromArgs

        val filesToDisplay = episodeFiles
        if (filesToDisplay.isNullOrEmpty()) {
            Log.w(TAG, "No files to display in dialog.")
            // Můžeme zde zobrazit zprávu v dialogu nebo ho rovnou zavřít
            binding.tvDialogTitle.text = "Nebyly nalezeny žádné verze souboru."
            binding.rvEpisodeFiles.visibility = View.GONE
            // Můžete přidat tlačítko OK na zavření
            return
        }

        val adapter = EpisodeFileAdapter(filesToDisplay) { selectedEpisodeFile ->
            Log.d(TAG, "File selected in dialog: ${selectedEpisodeFile.fileModel.name}")
            listener?.onEpisodeFileSelected(selectedEpisodeFile.fileModel)
            dismiss() // Zavřít dialog po výběru
        }

        binding.rvEpisodeFiles.layoutManager = LinearLayoutManager(context)
        binding.rvEpisodeFiles.adapter = adapter
        // Požádat o fokus pro RecyclerView, aby byla možná navigace D-padem
        binding.rvEpisodeFiles.requestFocus()
    }


    // Volitelné: Přizpůsobení velikosti dialogu
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        // Můžete nastavit i minimální šířku, např.
        // dialog?.window?.setLayout(resources.getDimensionPixelSize(R.dimen.custom_dialog_min_width), ViewGroup.LayoutParams.WRAP_CONTENT)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Vyčistit binding, aby se předešlo memory leakům
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }
}
