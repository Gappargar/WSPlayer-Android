package com.example.wsplayer.ui.tv.compose

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke // ***** PŘIDÁN IMPORT *****
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Card // Použijeme Material 3 Card
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape // Pro ostrý okraj, pokud by byl potřeba
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wsplayer.data.models.FileModel
import com.example.wsplayer.data.models.SeriesEpisodeFile
import java.text.NumberFormat

// Pomocná funkce formatFileSizeCompose zůstává stejná
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
    onFileClicked: (SeriesEpisodeFile) -> Unit,
    modifier: Modifier = Modifier // Přidáme modifier pro případné externí úpravy
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    // Animace pro plynulejší přechody
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.05f else 1.0f, label = "scale")
    val elevation by animateDpAsState(targetValue = if (isFocused) 8.dp else 2.dp, label = "elevation")
    val backgroundColor = if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (isFocused) MaterialTheme.colorScheme.outline else Color.Transparent

    Card(
        modifier = modifier // Aplikujeme externí modifier
            .fillMaxWidth()
            .scale(scale) // Aplikujeme animované zvětšení
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable(interactionSource = interactionSource)
            .clickable { onFileClicked(episodeFile) }
            .padding(vertical = 4.dp, horizontal = 8.dp) // Padding kolem karty
            // ***** OPRAVA: Použití Modifier.border() *****
            .border(
                width = if (isFocused) 2.dp else 0.dp, // Šířka okraje
                color = borderColor, // Barva okraje
                shape = CardDefaults.shape // Tvar okraje by měl odpovídat tvaru karty
            ),
        // *******************************************
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
        // Parametr 'border' pro Card samotnou očekává BorderStroke, ne CardDefaults.outlinedBorder
        // border = if (isFocused) BorderStroke(2.dp, borderColor) else null // Alternativní způsob, pokud by Modifier.border nefungoval dle očekávání
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp) // Vnitřní padding obsahu karty
                .fillMaxWidth()
        ) {
            Text(
                text = episodeFile.fileModel.name,
                style = MaterialTheme.typography.titleMedium, // Použití typografie z tématu
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis // Pokud je text příliš dlouhý
            )
            Spacer(modifier = Modifier.height(6.dp))

            val details = mutableListOf<String>()
            episodeFile.quality?.let { if (it.isNotBlank()) details.add("Kvalita: $it") }
            episodeFile.language?.let { if (it.isNotBlank()) details.add("Jazyk: $it") }
            details.add("Velikost: ${formatFileSizeCompose(episodeFile.fileModel.size)}")

            Text(
                text = details.joinToString("  •  "), // Oddělovač pro lepší čitelnost
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 380)
@Composable
fun PreviewEpisodeFileRowViewFocused() {
    val sampleFileModel = FileModel("ident1", "Star Trek: Discovery - S01E01 - Vulkánský pozdrav (The Vulcan Hello) - Velmi dlouhý název, který se nevejde.mkv", "mkv", null, null, null, 1234567890L, 0,0,0,0, videoQuality = "1080p HDR", videoLanguage = "CZ Dabing + EN + SUB")
    val sampleEpisodeFile = SeriesEpisodeFile(sampleFileModel, "1080p HDR", "CZ+EN+SUB")
    MaterialTheme {
        var isFocused by remember { mutableStateOf(true) } // Pouze pro účely náhledu
        val scale by animateFloatAsState(targetValue = if (isFocused) 1.05f else 1.0f, label = "scale")
        val elevation by animateDpAsState(targetValue = if (isFocused) 8.dp else 2.dp, label = "elevation")
        val backgroundColor = if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        val contentColor = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        val borderColor = if (isFocused) MaterialTheme.colorScheme.outline else Color.Transparent

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .padding(vertical = 4.dp, horizontal = 8.dp)
                .border( // Aplikace okraje v náhledu
                    width = if (isFocused) 2.dp else 0.dp,
                    color = borderColor,
                    shape = CardDefaults.shape
                ),
            colors = CardDefaults.cardColors(
                containerColor = backgroundColor,
                contentColor = contentColor
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(text = sampleEpisodeFile.fileModel.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(6.dp))
                val details = mutableListOf<String>()
                sampleEpisodeFile.quality?.let { if (it.isNotBlank()) details.add("Kvalita: $it") }
                sampleEpisodeFile.language?.let { if (it.isNotBlank()) details.add("Jazyk: $it") }
                details.add("Velikost: ${formatFileSizeCompose(sampleEpisodeFile.fileModel.size)}")
                Text(text = details.joinToString("  •  "), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 380)
@Composable
fun PreviewEpisodeFileRowViewNotFocused() {
    val sampleFileModel = FileModel("ident2", "Krátký název.avi", "avi", null, null, null, 350000000L, 0,0,0,0, videoQuality = "SD", videoLanguage = "CZ")
    val sampleEpisodeFile = SeriesEpisodeFile(sampleFileModel, "SD", "CZ")
    MaterialTheme {
        EpisodeFileRowView(episodeFile = sampleEpisodeFile, onFileClicked = {})
    }
}
