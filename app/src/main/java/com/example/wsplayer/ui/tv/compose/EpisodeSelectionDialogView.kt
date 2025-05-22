package com.example.wsplayer.ui.tv.compose

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wsplayer.data.models.FileModel
import com.example.wsplayer.data.models.SeriesEpisodeFile
import kotlinx.coroutines.delay

@Composable
fun EpisodeSelectionDialogView(
    title: String,
    episodeFiles: List<SeriesEpisodeFile>,
    onFileSelected: (SeriesEpisodeFile) -> Unit,
    onDismissRequest: () -> Unit
) {
    val listState = rememberLazyListState()
    val firstItemFocusRequester = remember { FocusRequester() }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (episodeFiles.isEmpty()) {
                Text(
                    text = "Pro tuto epizodu nebyly nalezeny žádné soubory.",
                    modifier = Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyUp &&
                                (keyEvent.key == Key.Escape || keyEvent.key == Key.Back)
                            ) {
                                Log.d("EpisodeSelectionDialogView", "Back key pressed, dismissing dialog.")
                                onDismissRequest()
                                true
                            } else {
                                false
                            }
                        },
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(episodeFiles, key = { _, item -> item.fileModel.ident }) { index, episodeFile ->
                        val rowModifier = if (index == 0) {
                            Modifier.focusRequester(firstItemFocusRequester)
                        } else {
                            Modifier
                        }

                        EpisodeFileRowView(
                            episodeFile = episodeFile,
                            onFileClicked = { selectedFile ->
                                Log.d("EpisodeSelectionDialogView", "Row clicked: ${selectedFile.fileModel.name}")
                                onFileSelected(selectedFile)
                            },
                            modifier = rowModifier
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(episodeFiles.size) {
        if (episodeFiles.isNotEmpty()) {
            delay(100) // Pomůže zajistit, že první item je už složen
            try {
                Log.d("EpisodeSelectionDialogView", "Requesting focus for first row.")
                firstItemFocusRequester.requestFocus()
            } catch (e: Exception) {
                Log.e("EpisodeSelectionDialogView", "Failed to request focus: ${e.message}")
            }
        }
    }
}
