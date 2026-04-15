package com.example.dystopia.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.dystopia.MusicViewModel
import com.example.dystopia.ArtistTab
import com.example.dystopia.data.SearchItem
import com.example.dystopia.data.SearchResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistSearchScreen(
    viewModel: MusicViewModel,
    artistName: String,
    onBack: () -> Unit,
    onTrackClick: (SearchResult) -> Unit,
    onAlbumClick: (SearchItem.PlaylistResult) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val tracks = viewModel.getArtistTracks()
    val albums = viewModel.getArtistPlaylists()
    val playlists = viewModel.getAllPlaylistsForSelection()
    val context = LocalContext.current

    var showPlaylistDialog by remember { mutableStateOf(false) }
    var selectedTrackForPlaylist by remember { mutableStateOf<SearchResult?>(null) }
    var showAlbumPlaylistDialog by remember { mutableStateOf(false) }
    var selectedAlbumForPlaylist by remember { mutableStateOf<SearchItem.PlaylistResult?>(null) }

    state.downloadMessage?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎤 $artistName") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = if (state.activeArtistTab == ArtistTab.TRACKS) 0 else 1) {
                Tab(
                    selected = state.activeArtistTab == ArtistTab.TRACKS,
                    onClick = { viewModel.setArtistTab(ArtistTab.TRACKS) },
                    text = { Text("Треки (${tracks.size})") }
                )
                Tab(
                    selected = state.activeArtistTab == ArtistTab.PLAYLISTS,
                    onClick = { viewModel.setArtistTab(ArtistTab.PLAYLISTS) },
                    text = { Text("Альбомы (${albums.size})") }
                )
            }

            when (state.activeArtistTab) {
                ArtistTab.TRACKS -> {
                    if (state.isLoading && tracks.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (tracks.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Треки не найдены 😔")
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                            items(tracks, key = { it.cleanTitle }) { result ->
                                SearchResultItemWithMenu(
                                    result = result,
                                    onClick = { onTrackClick(result) },
                                    onAddToPlaylist = {
                                        selectedTrackForPlaylist = result
                                        showPlaylistDialog = true
                                    }
                                )
                            }

                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "🎉 Все треки загружены (${tracks.size})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                ArtistTab.PLAYLISTS -> {
                    if (state.isLoading && albums.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (albums.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Альбомы не найдены")
                        }
                    } else {
                        LazyColumn {
                            items(albums) { album ->
                                AlbumItemWithMenu(
                                    album = album,
                                    onClick = { onAlbumClick(album) },
                                    onAddToPlaylist = {
                                        selectedAlbumForPlaylist = album
                                        showAlbumPlaylistDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showPlaylistDialog && selectedTrackForPlaylist != null) {
            PlaylistSelectionDialog(
                playlists = playlists,
                onPlaylistSelected = { playlistId ->
                    selectedTrackForPlaylist?.let { track ->
                        viewModel.addSingleTrackToPlaylist(track, playlistId)
                    }
                    showPlaylistDialog = false
                    selectedTrackForPlaylist = null
                },
                onCreateNewPlaylist = { name ->
                    selectedTrackForPlaylist?.let { track ->
                        viewModel.createPlaylistAndAddTracks(name, listOf(track))
                    }
                    showPlaylistDialog = false
                    selectedTrackForPlaylist = null
                },
                onDismiss = {
                    showPlaylistDialog = false
                    selectedTrackForPlaylist = null
                }
            )
        }

        if (showAlbumPlaylistDialog && selectedAlbumForPlaylist != null) {
            AlertDialog(
                onDismissRequest = {
                    showAlbumPlaylistDialog = false
                    selectedAlbumForPlaylist = null
                },
                title = { Text("Добавить альбом в плейлист") },
                text = { Text("Эта функция будет доступна скоро") },
                confirmButton = {
                    TextButton(onClick = {
                        showAlbumPlaylistDialog = false
                        selectedAlbumForPlaylist = null
                    }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
fun SearchResultItemWithMenu(
    result: SearchResult,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

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
                            Icon(Icons.Default.PlaylistAdd, "Add to playlist")
                        }
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun AlbumItemWithMenu(
    album: SearchItem.PlaylistResult,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!album.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = album.coverUrl,
                    contentDescription = album.name,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.QueueMusic,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (album.trackCount.isNotBlank()) {
                    Text(
                        text = "🎵 ${album.trackCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

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
                        text = { Text("Открыть альбом") },
                        onClick = {
                            showMenu = false
                            onClick()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.FolderOpen, "Open")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Добавить в плейлист") },
                        onClick = {
                            showMenu = false
                            onAddToPlaylist()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.PlaylistAdd, "Add to playlist")
                        }
                    )
                }
            }
        }
    }
}