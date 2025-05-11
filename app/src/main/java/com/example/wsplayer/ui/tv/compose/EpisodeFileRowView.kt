package com.example.wsplayer.ui.tv.compose // Alebo váš preferovaný balíček

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wsplayer.data.models.FileModel // Predpokladáme, že FileModel je Parcelable
import com.example.wsplayer.data.models.SeriesEpisodeFile // Predpokladáme, že SeriesEpisodeFile je Parcelable
import java.text.NumberFormat

// Pomocná funkcia na formátovanie veľkosti súboru (môžete ju mať v Utils)
fun formatFileSizeCompose(sizeInBytes: Long): String {
    if (sizeInBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
    val safeDigitGroups = digitGroups.coerceIn(0, units.size - 1)
    val nf = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }
    return "${nf.format(sizeInBytes / Math.pow(1024.0, safeDigitGroups.toDouble()))} ${units[safeDigitGroups]}"
}

@Composable
fun EpisodeFileRowView(
    episodeFile: SeriesEpisodeFile,
    onFileClicked: (SeriesEpisodeFile) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .focusTarget() // Umožní focus pre D-pad
            .focusable(interactionSource = interactionSource) // Pre sledovanie focusu
            .clickable { onFileClicked(episodeFile) },
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 8.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episodeFile.fileModel.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                val details = mutableListOf<String>()
                episodeFile.quality?.let { details.add("Kvalita: $it") }
                episodeFile.language?.let { details.add("Jazyk: $it") }
                details.add("Velikost: ${formatFileSizeCompose(episodeFile.fileModel.size)}")

                Text(
                    text = details.joinToString(" - "),
                    fontSize = 12.sp,
                )
            }
            // Môžete sem pridať ikonu alebo iný indikátor
        }
    }
}

@Preview(showBackground = true, device = "id:tv_1080p")
@Composable
fun PreviewEpisodeFileRowView() {
    val sampleFileModel = FileModel("ident1", "Star.Trek.S01E01.The.Man.Trap.1080p.BluRay.x265.CZ.SK.EN.mkv", "mkv", null, null, null, 1234567890L, 0,0,0,0, videoQuality = "1080p", videoLanguage = "CZ+SK+EN")
    val sampleEpisodeFile = SeriesEpisodeFile(sampleFileModel, "1080p BluRay", "CZ+SK")
    MaterialTheme { // Pre náhľad je dobré obaliť do MaterialTheme
        Column {
            EpisodeFileRowView(episodeFile = sampleEpisodeFile, onFileClicked = {})
            // Pridajte ďalšie pre testovanie focusu, ak je to možné v preview
        }
    }
}
