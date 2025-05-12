package com.example.wsplayer.ui.tv.compose // Nebo váš preferovaný balíček

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
// import androidx.compose.foundation.layout.width // Tento import není přímo potřeba pro tento Composable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
// import androidx.compose.ui.focus.focusTarget // focusTarget je pro starší focus API, focusable() je preferováno
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wsplayer.data.models.FileModel
import com.example.wsplayer.data.models.SeriesEpisodeFile
import java.text.NumberFormat // Import pro NumberFormat

// ***** DEFINICE POMOCNÉ FUNKCE PRO FORMÁTOVÁNÍ VELIKOSTI SOUBORU *****
fun formatFileSizeCompose(sizeInBytes: Long): String {
    if (sizeInBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
    val safeDigitGroups = digitGroups.coerceIn(0, units.size - 1)
    val nf = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }
    return "${nf.format(sizeInBytes / Math.pow(1024.0, safeDigitGroups.toDouble()))} ${units[safeDigitGroups]}"
}
// *********************************************************************

@Composable
fun EpisodeFileRowView(
    episodeFile: SeriesEpisodeFile,
    onFileClicked: (SeriesEpisodeFile) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .onFocusChanged { focusState -> // Sledujeme změnu fokusu
                isFocused = focusState.isFocused
            }
            .focusable() // Umožní D-pad fokus
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
                    maxLines = 2 // Omezení na dva řádky pro název
                )
                Spacer(modifier = Modifier.height(4.dp))
                val details = mutableListOf<String>()
                episodeFile.quality?.let { details.add("Kvalita: $it") }
                episodeFile.language?.let { details.add("Jazyk: $it") }
                // Použití opravené funkce
                details.add("Velikost: ${formatFileSizeCompose(episodeFile.fileModel.size)}")

                Text(
                    text = details.joinToString(" - "),
                    fontSize = 12.sp,
                )
            }
            // Zde můžete případně přidat ikonu nebo jiný vizuální indikátor
        }
    }
}

@Preview(showBackground = true, widthDp = 400, device = "id:tv_1080p")
@Composable
fun PreviewEpisodeFileRowView_WithFiles() {
    val sampleFileModel = FileModel("ident1", "Star.Trek.S01E01.The.Man.Trap.1080p.BluRay.x265.CZ.SK.EN.mkv", "mkv", null, null, null, 1234567890L, 0,0,0,0, videoQuality = "1080p", videoLanguage = "CZ+SK+EN", episodeTitle = "The Man Trap")
    val sampleEpisodeFile = SeriesEpisodeFile(sampleFileModel, "1080p BluRay", "CZ+SK")
    MaterialTheme { // Pro náhled je dobré obalit do MaterialTheme
        Column {
            EpisodeFileRowView(episodeFile = sampleEpisodeFile, onFileClicked = {})
            // Můžete přidat další instanci pro testování focusu, pokud to náhled umožňuje
            // EpisodeFileRowView(episodeFile = sampleEpisodeFile.copy(fileModel = sampleFileModel.copy(ident="id2")), onFileClicked = {})
        }
    }
}

// Následující preview pro EpisodeSelectionDialogView zde není relevantní,
// protože tento soubor má obsahovat jen EpisodeFileRowView a jeho preview.
// Pokud chcete preview pro celý dialog, mělo by být v souboru EpisodeSelectionDialogView.kt.

// @Preview(showBackground = true, widthDp = 400, device = "id:tv_1080p")
// @Composable
// fun PreviewEpisodeSelectionDialogView_NoFiles() {
//    MaterialTheme {
//        EpisodeSelectionDialogView( // Toto by mělo být v souboru EpisodeSelectionDialogView.kt
//            title = "S01E02: Jiná Epizoda",
//            episodeFiles = emptyList(),
//            onFileSelected = {},
//            onDismissRequest = {}
//        )
//    }
// }
