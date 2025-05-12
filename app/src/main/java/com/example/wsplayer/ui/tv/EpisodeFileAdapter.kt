package com.example.wsplayer.ui.tv // Ujistěte se, že balíček odpovídá

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.wsplayer.R
import com.example.wsplayer.data.models.SeriesEpisodeFile
import java.text.NumberFormat

class EpisodeFileAdapter(
    private val files: List<SeriesEpisodeFile>,
    private val onItemClicked: (SeriesEpisodeFile) -> Unit // Lambda pro kliknutí na položku
) : RecyclerView.Adapter<EpisodeFileAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_episode_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val seriesEpisodeFile = files[position]
        holder.bind(seriesEpisodeFile)
    }

    override fun getItemCount(): Int = files.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val fileNameTextView: TextView = itemView.findViewById(R.id.tvItemFileName)
        private val fileDetailsTextView: TextView = itemView.findViewById(R.id.tvItemFileDetails)

        init {
            itemView.setOnClickListener {
                // Získání pozice a předání kliknuté položky přes lambda funkci
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClicked(files[position])
                }
            }
        }

        fun bind(seriesEpisodeFile: SeriesEpisodeFile) {
            val file = seriesEpisodeFile.fileModel
            fileNameTextView.text = file.name

            val quality = seriesEpisodeFile.quality ?: "N/A"
            val language = seriesEpisodeFile.language ?: "N/A"
            val size = formatFileSize(file.size)

            fileDetailsTextView.text = "Kvalita: $quality - Jazyk: $language - Velikost: $size"
        }

        // Pomocná funkce pro formátování velikosti souboru
        private fun formatFileSize(sizeInBytes: Long): String {
            if (sizeInBytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
            val safeDigitGroups = digitGroups.coerceIn(0, units.size - 1)
            val nf = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }
            return "${nf.format(sizeInBytes / Math.pow(1024.0, safeDigitGroups.toDouble()))} ${units[safeDigitGroups]}"
        }
    }
}
