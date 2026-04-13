package com.example.dystopia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dystopia.MusicViewModel
import com.example.dystopia.data.SearchResult
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.ui.platform.LocalContext



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumTracksScreen(
    viewModel: MusicViewModel,
    albumName: String,
    onBack: () -> Unit,
    onTrackClick: (SearchResult) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val tracks = state.searchResults
    val context = LocalContext.current

    // Показываем сообщение о добавлении
    state.downloadMessage?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "💿 $albumName",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                actions = {
                    // ✅ Кнопка "Добавить все"
                    if (tracks.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                viewModel.addAllTracksToPlaylist(tracks, albumName)
                            },
                            enabled = !state.isLoading
                        ) {
                            Icon(
                                Icons.Default.AddCircle,
                                "Добавить все",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading && tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Треки не найдены")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // ✅ Индикатор добавления
                if (state.isLoading) {
                    item {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                items(tracks, key = { it.cleanTitle }) { result ->
                    ListItem(
                        headlineContent = {
                            Text(
                                result.displayTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Text(
                                result.cleanTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Icon(Icons.Default.PlayArrow, "Play")
                        },
                        modifier = Modifier.clickable { onTrackClick(result) }
                    )
                }
            }
        }
    }
}