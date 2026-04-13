package com.example.dystopia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.dystopia.MusicViewModel
import com.example.dystopia.RepeatMode
import com.example.dystopia.data.Playlist
import androidx.media3.common.C
import android.widget.Toast
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(viewModel: MusicViewModel, onBack: () -> Unit, onArtistClick: (String) -> Unit = {}) {
    val isPlaying by viewModel.player.isPlaying.collectAsState()
    val state by viewModel.uiState.collectAsState()
    val currentTrack = state.currentTrack


    val coverUri by viewModel.player.currentCover.collectAsState()

    val context = LocalContext.current

    if (currentTrack == null) { onBack(); return }

    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isPlaying, currentTrack) {
        while (true) {
            val currentPosition = viewModel.player.currentPosition
            val totalDuration = viewModel.player.duration

            if (totalDuration > 0 && totalDuration != C.TIME_UNSET) {
                duration = totalDuration
                if (!isDragging) {
                    sliderPosition = currentPosition.toFloat() / totalDuration.toFloat()
                }
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    LaunchedEffect(currentTrack) {
        sliderPosition = 0f
        duration = 0L
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сейчас играет") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            item {
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (!coverUri.isNullOrBlank())
                                MaterialTheme.colorScheme.surface
                            else
                                MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!coverUri.isNullOrBlank()) {
                        AsyncImage(
                            model = coverUri,
                            contentDescription = "Cover",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            placeholder = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_gallery),
                            error = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_gallery)
                        )
                    } else {
                        Icon(
                            Icons.Default.MusicNote,
                            null,
                            modifier = Modifier.size(120.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            item {
                val currentArtist = currentTrack.artist
                    ?: if (currentTrack.title.contains(" - ")) currentTrack.title.substringBefore(" - ")
                    else "Неизвестный"

                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = currentTrack.title,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )

                    // ✅ Кликабельное имя исполнителя
                    Text(
                        text = "🎤 $currentArtist",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            onArtistClick(currentArtist)  // ✅ Вызываем колбэк
                        }
                    )
                }
            }


            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime((sliderPosition * duration).toLong()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatTime(duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value = sliderPosition,
                        onValueChange = {
                            sliderPosition = it
                            isDragging = true
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            viewModel.player.seekTo((sliderPosition * duration).toLong())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }


            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val playerState = viewModel.uiState.collectAsState().value

                    // Повтор
                    IconButton(
                        onClick = { viewModel.toggleRepeat() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = when (playerState.repeatMode) {
                                RepeatMode.NONE -> Icons.Default.Repeat
                                RepeatMode.ONE -> Icons.Default.RepeatOne
                                RepeatMode.ALL -> Icons.Default.Repeat
                            },
                            contentDescription = "Repeat mode",
                            tint = if (playerState.repeatMode != RepeatMode.NONE)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }


                    IconButton(
                        onClick = { viewModel.playPrevious() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            "Previous",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    FloatingActionButton(
                        onClick = { viewModel.togglePlay() },
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }


                    IconButton(
                        onClick = { viewModel.playNext() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            "Next",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(40.dp)
                        )
                    }


                    IconButton(
                        onClick = { viewModel.toggleShuffle() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playerState.shuffleEnabled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }


            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var showDeleteDialog by remember { mutableStateOf(false) }
                    var showPlaylistDialog by remember { mutableStateOf(false) }


                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            onClick = {
                                if (currentTrack.isOffline) {
                                    showDeleteDialog = true
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Скачивание...",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    viewModel.downloadTrack(context, currentTrack)
                                }
                            },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = if (currentTrack.isOffline)
                                    Icons.Default.Delete
                                else
                                    Icons.Default.Download,
                                contentDescription = if (currentTrack.isOffline)
                                    "Удалить"
                                else
                                    "Скачать",
                                tint = if (currentTrack.isOffline)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            text = if (currentTrack.isOffline) "Удалить" else "Скачать",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }


                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            onClick = { showPlaylistDialog = true },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                Icons.Default.PlaylistAdd,
                                "Add to playlist",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            text = "В плейлист",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (showPlaylistDialog) {
                        AddToPlaylistDialog(
                            viewModel,
                            currentTrack,
                            onDismiss = { showPlaylistDialog = false }
                        )
                    }

                    if (showDeleteDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteDialog = false },
                            title = { Text("Удалить трек?") },
                            text = { Text("Файл будет удалён с устройства.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.deleteOfflineTrack(context, currentTrack)
                                        showDeleteDialog = false
                                    }
                                ) {
                                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteDialog = false }) {
                                    Text("Отмена")
                                }
                            }
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

fun formatTime(ms: Long): String {
    if (ms < 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${if (seconds < 10) "0$seconds" else seconds}"
}

@Composable
fun AddToPlaylistDialog(
    viewModel: MusicViewModel,
    track: com.example.dystopia.data.TrackInfo,
    onDismiss: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val playlists = state.playlists

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить в плейлист") },
        text = {
            LazyColumn {
                items(playlists) { playlist ->
                    TextButton(
                        onClick = {
                            viewModel.saveTrackToPlaylist(playlist.id, track)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(playlist.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}