package com.example.wsplayer.ui.tv.compose // Alebo váš preferovaný balíček

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wsplayer.data.models.FileModel
import com.example.wsplayer.data.models.SeriesEpisodeFile

@Composable
fun EpisodeSelectionDialogView(
    title: String,
    episodeFiles: List<SeriesEpisodeFile>,
    onFileSelected: (SeriesEpisodeFile) -> Unit,
    onDismissRequest: () -> Unit // Callback pro zavření dialogu (např. tlačítkem Zpět)
) {
    Surface( // Použijeme Surface pro možnost nastavení pozadí a tvaru dialogu
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface, // Barva pozadí dialogu
        modifier = Modifier.padding(16.dp) // Okraj kolem celého dialogu
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp) // Vnitřní padding obsahu dialogu
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
                color = MaterialTheme.colorScheme.onSurface // Barva textu titulku
            )

            if (episodeFiles.isEmpty()) {
                Text(
                    text = "Pro tuto epizodu nebyly nalezeny žádné soubory.",
                    modifier = Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp) // Mezera mezi položkami
                ) {
                    items(episodeFiles) { episodeFile ->
                        EpisodeFileRowView(
                            episodeFile = episodeFile,
                            onFileClicked = { selectedFile ->
                                onFileSelected(selectedFile)
                            }
                        )
                    }
                }
            }

            // Tlačítko Zrušit bychom mohli přidat sem, pokud bychom nepoužívali systémové
            // alebo ak by DialogFragment neriešil zrušenie sám.
            // Napríklad:
            // Button(
            //     onClick = onDismissRequest,
            //     modifier = Modifier.padding(top = 16.dp)
            // ) {
            //     Text("Zrušit")
            // }
        }
    }
}

@Preview(showBackground = true, widthDp = 400, device = "id:tv_1080p")
@Composable
fun PreviewEpisodeSelectionDialogView_WithFiles() {
    val sampleFiles = listOf(
        SeriesEpisodeFile(FileModel("id1", "Epizoda 1 - 1080p CZ.mkv", "mkv", null, null, null, 1500000000L, 0,0,0,0, videoQuality = "1080p", videoLanguage = "CZ"), "1080p", "CZ"),
        SeriesEpisodeFile(FileModel("id2", "Epizoda 1 - 720p EN.mkv", "mkv", null, null, null, 800000000L, 0,0,0,0, videoQuality = "720p", videoLanguage = "EN"), "720p", "EN"),
        SeriesEpisodeFile(FileModel("id3", "Epizoda 1 - SD MULTI.avi", "avi", null, null, null, 350000000L, 0,0,0,0, videoQuality = "SD", videoLanguage = "MULTI"), "SD", "MULTI")
    )
    MaterialTheme { // Důležité pro použití MaterialTheme barev a tvarů v náhledu
        EpisodeSelectionDialogView(
            title = "S01E01: Název Epizody",
            episodeFiles = sampleFiles,
            onFileSelected = {},
            onDismissRequest = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 400, device = "id:tv_1080p")
@Composable
fun PreviewEpisodeSelectionDialogView_NoFiles() {
    MaterialTheme {
        EpisodeSelectionDialogView(
            title = "S01E02: Jiná Epizoda",
            episodeFiles = emptyList(),
            onFileSelected = {},
            onDismissRequest = {}
        )
    }
}
