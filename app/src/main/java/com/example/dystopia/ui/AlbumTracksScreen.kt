package com.example.dystopia.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.dystopia.MusicViewModel
import com.example.dystopia.data.SearchResult

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
    val playlists = viewModel.getAllPlaylistsForSelection()
    val context = LocalContext.current

    // ✅ Состояния для диалогов
    var showTrackPlaylistDialog by remember { mutableStateOf(false) }  // Для одного трека
    var showAlbumPlaylistDialog by remember { mutableStateOf(false) }  // Для всех треков альбома
    var selectedTrackForPlaylist by remember { mutableStateOf<SearchResult?>(null) }

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
                    // ✅ Кнопка "Добавить все треки альбома"
                    if (tracks.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                showAlbumPlaylistDialog = true  // ✅ Открываем диалог
                            },
                            enabled = !state.isLoading
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistAdd,
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
                if (state.isLoading) {
                    item {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ✅ Список треков с меню для каждого
                items(tracks, key = { it.cleanTitle }) { result ->
                    AlbumTrackItemWithMenu(
                        track = result,
                        onClick = { onTrackClick(result) },
                        onAddToPlaylist = {
                            selectedTrackForPlaylist = result
                            showTrackPlaylistDialog = true
                        }
                    )
                }
            }
        }

        // ✅ Диалог для ОДНОГО трека
        if (showTrackPlaylistDialog && selectedTrackForPlaylist != null) {
            PlaylistSelectionDialog(
                playlists = playlists,
                onPlaylistSelected = { playlistId ->
                    selectedTrackForPlaylist?.let { track ->
                        viewModel.addSingleTrackToPlaylist(track, playlistId)
                    }
                    showTrackPlaylistDialog = false
                    selectedTrackForPlaylist = null
                },
                onCreateNewPlaylist = { name ->
                    selectedTrackForPlaylist?.let { track ->
                        viewModel.createPlaylistAndAddTracks(name, listOf(track))
                    }
                    showTrackPlaylistDialog = false
                    selectedTrackForPlaylist = null
                },
                onDismiss = {
                    showTrackPlaylistDialog = false
                    selectedTrackForPlaylist = null
                }
            )
        }


        if (showAlbumPlaylistDialog) {
            PlaylistSelectionDialog(
                playlists = playlists,
                onPlaylistSelected = { playlistId ->
                    // ✅ Используем существующую функцию
                    viewModel.addTracksToSelectedPlaylist(playlistId, tracks, albumName)
                    showAlbumPlaylistDialog = false
                },
                onCreateNewPlaylist = { name ->
                    // ✅ Создаём новый плейлист с именем альбома
                    viewModel.createPlaylistAndAddTracks(name, tracks)
                    showAlbumPlaylistDialog = false
                },
                onDismiss = {
                    showAlbumPlaylistDialog = false
                }
            )
        }
    }
}

// ✅ Компонент трека альбома с меню (⋮)
@Composable
fun AlbumTrackItemWithMenu(
    track: SearchResult,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Text(
                track.displayTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                track.cleanTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        "Menu",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Воспроизвести") },
                        onClick = {
                            showMenu = false
                            onClick()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.PlayArrow, "Play")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Добавить в плейлист") },
                        onClick = {
                            showMenu = false
                            onAddToPlaylist()
                        },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist")
                        }
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}