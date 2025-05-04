package com.example.wsplayer.ui.search // Váš balíček + .ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wsplayer.data.models.FileModel
import com.example.wsplayer.databinding.ListItemFileBinding
import coil.load // Import pro knihovnu Coil
import com.example.wsplayer.R // Import pro přístup k zdrojům aplikace (drawable, string, atd.)


// Adapter pro zobrazení seznamu FileModelů v RecyclerView
// Dědí od ListAdapter, což pomáhá s efektivní aktualizací seznamu
// Přijímá lambda funkci onItemClick, která se spustí při kliknutí na položku
class SearchAdapter(private val onItemClick: (FileModel) -> Unit) : // <-- ZDE JE HLAVIČKA TŘÍDY SearchAdapter
    ListAdapter<FileModel, SearchAdapter.FileViewHolder>(FileDiffCallback()) { // <-- Dědění od ListAdapter a předání DiffUtil callbacku

    // Vnořená třída ViewHolder, která drží pohledy pro jednu položku seznamu
    // **TATO TŘÍDA MUSÍ BÝT UVNITŘ složených závorek { } třídy SearchAdapter**
    class FileViewHolder(private val binding: ListItemFileBinding) : // <-- ZDE JE HLAVIČKA TŘÍDY FileViewHolder
        RecyclerView.ViewHolder(binding.root) {

        // Metoda pro "vázání" dat (FileModel) k prvkům v layoutu
        fun bind(file: FileModel) {
            println("SearchAdapter: Vázaná položka - Název: '${file.name}', Typ: '${file.type}'") // <-- PŘIDEJTE TENTO LOG

            // Nastavení názvu souboru
            binding.textViewFileName.text = file.name

            // Formátování velikosti souboru na čitelný string
            binding.textViewFileSize.text = formatFileSize(file.size)

            // Nastavení textu typu souboru
            binding.textViewFileType.text = file.type

            // Nastavení textu pro Hodnocení (Počet hlasů)
            binding.textViewRating.text = "+${file.positive_votes} / -${file.negative_votes}"

            // **Nastavení ikony typu souboru**
            val fileTypeIconRes = when (file.type) {
                // Běžné video přípony
                "avi", "mp4", "mkv", "mov", "wmv", "flv", "webm", "vob", "ogv", "gifv", "m4v", "3gp", "3g2", "f4v", "f4p", "f4a", "f4b" -> R.drawable.ic_file_video // Ikona videa

                // Běžné audio přípony
                "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a", "alac" -> R.drawable.ic_file_audio // Ikona audia

                // Běžné archivační přípony
                "rar", "zip", "7z", "tar", "gz", "bz2", "xz", "iso" -> R.drawable.ic_file_archive // Ikona archivu

                // Běžné dokumentové přípony
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp" -> R.drawable.ic_file_document // Můžete vytvořit ic_file_document ikonu

                // Běžné obrázkové přípony
                "jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "svg" -> R.drawable.ic_file_image // Můžete vytvořit ic_file_image ikonu

                // Přípony e-knih, které se objevily v logu
                "mobi", "epub" -> R.drawable.ic_file_ebook // Můžete vytvořit ic_file_ebook ikonu

                // TODO: Přidat další přípony souborů, které plánujete podporovat

                else -> R.drawable.ic_file_archive // Defaultní ikona pro všechny ostatní (např. generická ikona souboru nebo archiv)
            }
            binding.imageViewFileTypeIcon.setImageResource(fileTypeIconRes) // Nastavení ikony
            binding.imageViewFileTypeIcon.visibility = View.VISIBLE // Zobrazit ImageView

            // **Nastavení ikony zámku (pro heslo)**
            if (file.password == 1) {
                binding.imageViewPasswordIcon.visibility = View.VISIBLE
                binding.imageViewPasswordIcon.setImageResource(R.drawable.ic_file_password)
            } else {
                binding.imageViewPasswordIcon.visibility = View.GONE
            }

            // Načítání náhledu do ImageView pomocí knihovny Coil
            val thumbnailUrl = file.img ?: file.stripe

            if (!thumbnailUrl.isNullOrEmpty()) {
                binding.imageViewThumbnail.visibility = View.VISIBLE // Zobrazit ImageView před načtením
                binding.imageViewThumbnail.load(thumbnailUrl) {
                    // Volitelné: placeholder(R.drawable.placeholder_image)
                    // Volitelné: error(R.drawable.error_image)
                    crossfade(true)
                }
            } else {
                binding.imageViewThumbnail.visibility = View.GONE // Skrýt ImageView
            }
        }

        // Pomocná funkce pro formátování velikosti souboru
        private fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = listOf("B", "KB", "MB", "GB", "TB")
            var size = bytes.toDouble()
            var unitIndex = 0

            while (size >= 1024 && unitIndex < units.size - 1) {
                size /= 1024
                unitIndex++
            }
            return String.format("%.1f %s", size, units[unitIndex])
        }
    }

    // Vnořená třída DiffUtil.ItemCallback pro efektivní aktualizaci seznamu
    // **TATO TŘÍDA MUSÍ BÝT UVNITŘ složených závorek { } třídy SearchAdapter**
    private class FileDiffCallback : DiffUtil.ItemCallback<FileModel>() { // <-- ZDE JE HLAVIČKA TŘÍDY FileDiffCallback
        // Zda se položky reprezentují stejnou položku
        override fun areItemsTheSame(oldItem: FileModel, newItem: FileModel): Boolean { // <-- Musí mít "override"
            return oldItem.ident == newItem.ident
        }

        // Zda se obsah položek změnil
        override fun areContentsTheSame(oldItem: FileModel, newItem: FileModel): Boolean { // <-- Musí mít "override"
            return oldItem == newItem
        }
    }

    // --- Metody Adapteru, které MUSÍ být přepsány (override) ---

    // Metoda volaná LayoutManagerem k vytvoření nového ViewHolderu
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder { // <-- Musí mít "override"
        val binding = ListItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FileViewHolder(binding)
    }

    // Metoda volaná LayoutManagerem k zobrazení dat na dané pozici
    override fun onBindViewHolder(holder: FileViewHolder, position: Int) { // <-- Musí mít "override"
        val file = getItem(position)
        holder.bind(file)

        // Nastavit posluchač kliknutí pro CELOU položku (itemView)
        holder.itemView.setOnClickListener {
            onItemClick(file)
        }
    }

    // Zde končí definice třídy SearchAdapter. Nic pod tímto } (kromě prázdných řádků).
}