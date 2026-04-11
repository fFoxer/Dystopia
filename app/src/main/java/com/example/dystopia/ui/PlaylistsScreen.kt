package com.example.dystopia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.dystopia.MusicViewModel
import com.example.dystopia.data.OfflineManager
import com.example.dystopia.data.Playlist

@Composable
fun PlaylistsScreen(viewModel: MusicViewModel) {
    val state by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    if (state.selectedPlaylist != null) {
        PlaylistDetailScreen(viewModel, state.selectedPlaylist!!, state.playlistTracks)
    } else {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, "Create playlist")
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    "Мои Плейлисты",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(state.playlists) { playlist ->
                        PlaylistCard(playlist = playlist, onClick = { viewModel.openPlaylist(playlist) })
                    }
                    if (state.playlists.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Нет плейлистов. Создайте первый!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (showCreateDialog) CreatePlaylistDialog(viewModel, onDismiss = { showCreateDialog = false })
}

@Composable
fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.height(180.dp).fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!playlist.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = playlist.name,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 2
                )
                if (!playlist.description.isNullOrBlank()) {
                    Text(
                        text = playlist.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    viewModel: MusicViewModel,
    playlist: Playlist,
    tracks: List<com.example.dystopia.data.PlaylistTrack>
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val stats = viewModel.getPlaylistStats(tracks)

    Box(modifier = Modifier.fillMaxSize()) {
        if (!playlist.coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = playlist.coverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(50.dp),
                contentScale = ContentScale.Crop,
                alpha = 0.3f
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Плейлист") },
                    navigationIcon = {
                        IconButton(onClick = viewModel::goBackToPlaylists) {
                            Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Menu", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(240.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!playlist.coverUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = playlist.coverUrl,
                                    contentDescription = playlist.name,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = playlist.name, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                            if (!playlist.description.isNullOrBlank()) {
                                Text(text = playlist.description, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.7f))
                            }
                            Text(text = "${stats.first} · ${stats.second}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.5f))
                        }
                    }
                }
                items(tracks) { track ->
                    TrackListItemSimple(
                        track = track,
                        viewModel = viewModel,
                        context = context,
                        playlist = playlist,
                        allTracks = tracks,
                        onRemove = { viewModel.removeTrackFromPlaylist(playlist.id, track.trackUrl) }
                    )
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Удалить плейлист?") },
                text = { Text("Все треки будут удалены из этого плейлиста.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.deletePlaylist(playlist); showDeleteDialog = false }) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") }
                }
            )
        }

        if (showEditDialog) {
            EditPlaylistDialog(playlist, viewModel) { showEditDialog = false }
        }

        if (showMenu) {
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Редактировать") }, onClick = { showMenu = false; showEditDialog = true }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                DropdownMenuItem(text = { Text("Удалить", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; showDeleteDialog = true }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) })
            }
        }
    }
}

@Composable
fun TrackListItemSimple(
    track: com.example.dystopia.data.PlaylistTrack,
    viewModel: MusicViewModel,
    context: android.content.Context,
    playlist: Playlist,
    allTracks: List<com.example.dystopia.data.PlaylistTrack>,
    onRemove: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isCurrentTrack = state.currentTrack?.title == track.title && state.selectedPlaylist?.id == playlist.id

    // ✅ Проверяем наличие файла при каждом рендере
    val isOffline by remember(track.title) {
        derivedStateOf {
            OfflineManager.isOffline(context, track.title)
        }
    }

    var showTrackMenu by remember { mutableStateOf(false) }
    var coverUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(track.title) {
        coverUrl = viewModel.getCoverUrl(track.title)
    }

    Card(
        onClick = {
            val index = allTracks.indexOfFirst { it.title == track.title && it.trackUrl == track.trackUrl }
            if (index >= 0) viewModel.playPlaylistTrack(playlist, allTracks, index, context)
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentTrack) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent
        )
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (coverUrl != null) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = track.title,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // ✅ Иконка скачанного трека
                if (isOffline) {
                    Icon(
                        Icons.Default.Download,
                        "Downloaded",
                        modifier = Modifier.size(16.dp).align(Alignment.BottomEnd),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCurrentTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (isCurrentTrack) {
                    Text(
                        text = "Сейчас играет ▶️",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (isOffline) {
                    Text(
                        text = "Скачан ✓",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = { showTrackMenu = true }) {
                Icon(Icons.Default.MoreVert, "Menu", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showTrackMenu) {
                DropdownMenu(expanded = showTrackMenu, onDismissRequest = { showTrackMenu = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (isOffline) "Удалить" else "Скачать",
                                color = if (isOffline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            showTrackMenu = false
                            if (isOffline) {
                                viewModel.deleteOfflineTrack(context, com.example.dystopia.data.TrackInfo(track.title, track.trackUrl))
                            } else {
                                viewModel.downloadTrack(context, com.example.dystopia.data.TrackInfo(track.title, track.trackUrl))
                            }
                        },
                        leadingIcon = {
                            Icon(
                                if (isOffline) Icons.Default.Delete else Icons.Default.Download,
                                null,
                                tint = if (isOffline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EditPlaylistDialog(playlist: Playlist, viewModel: MusicViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(playlist.name) }
    var desc by remember { mutableStateOf(playlist.description ?: "") }
    var cover by remember { mutableStateOf(playlist.coverUrl ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать плейлист") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = cover,
                    onValueChange = { cover = it },
                    label = { Text("Ссылка на обложку") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.updatePlaylist(playlist, name, desc, cover.ifBlank { null }); onDismiss() },
                enabled = name.isNotBlank()
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun CreatePlaylistDialog(viewModel: MusicViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый плейлист") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = cover,
                    onValueChange = { cover = it },
                    label = { Text("Ссылка на обложку (необяз.)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        viewModel.createPlaylist(name, desc, cover.ifBlank { null })
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Создать") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}