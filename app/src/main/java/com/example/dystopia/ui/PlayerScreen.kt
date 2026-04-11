package com.example.dystopia.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.dystopia.MusicViewModel
import com.example.dystopia.RepeatMode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.media3.common.C
import kotlinx.coroutines.delay

@Composable
fun MiniPlayer(viewModel: MusicViewModel, onClick: () -> Unit) {
    val isPlaying by viewModel.player.isPlaying.collectAsState()
    val state by viewModel.uiState.collectAsState()
    val currentTrack = state.currentTrack

    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying, currentTrack) {
        while (isPlaying && currentTrack != null) {
            val currentPosition = viewModel.player.player.currentPosition
            val totalDuration = viewModel.player.player.duration

            if (totalDuration > 0 && totalDuration != C.TIME_UNSET) {
                progress = currentPosition.toFloat() / totalDuration.toFloat()
            }
            delay(1000)
        }
    }

    LaunchedEffect(currentTrack) {
        progress = 0f
    }

    if (currentTrack == null) return

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(if (currentTrack.coverUrl != null) Color.Transparent else MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    if (!currentTrack.coverUrl.isNullOrBlank()) {
                        AsyncImage(model = currentTrack.coverUrl, contentDescription = "Cover", modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(text = currentTrack.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = if (isPlaying) "Воспроизведение..." else "На паузе", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { viewModel.togglePlay() }) {
                    Icon(imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (isPlaying) "Pause" else "Play", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp))
                }
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(viewModel: MusicViewModel, onBack: () -> Unit) {
    val isPlaying by viewModel.player.isPlaying.collectAsState()
    val state by viewModel.uiState.collectAsState()
    val currentTrack = state.currentTrack
    val context = LocalContext.current
    LaunchedEffect(currentTrack) {

    }
    if (currentTrack == null) { onBack(); return }

    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isPlaying, currentTrack) {
        while (true) {
            val player = viewModel.player.player
            val currentPosition = player.currentPosition
            val totalDuration = player.duration

            if (totalDuration > 0 && totalDuration != C.TIME_UNSET) {
                duration = totalDuration
                if (!isDragging) {
                    sliderPosition = currentPosition.toFloat() / totalDuration.toFloat()
                }
            }
            delay(1000)
        }
    }

    LaunchedEffect(currentTrack) {
        sliderPosition = 0f
        duration = 0L
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Сейчас играет") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") }
            })
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Spacer(Modifier.height(32.dp))
            Box(modifier = Modifier.size(280.dp).clip(RoundedCornerShape(16.dp)).background(if (currentTrack.coverUrl != null) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                if (!currentTrack.coverUrl.isNullOrBlank()) {
                    AsyncImage(model = currentTrack.coverUrl, contentDescription = "Cover", modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop, placeholder = painterResource(android.R.drawable.ic_menu_gallery), error = painterResource(android.R.drawable.ic_menu_gallery))
                } else {
                    Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(120.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.height(32.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = currentTrack.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(text = "Dystopia Music", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = formatTime((sliderPosition * duration).toLong()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = formatTime(duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Slider(value = sliderPosition, onValueChange = { sliderPosition = it; isDragging = true }, onValueChangeFinished = { isDragging = false; viewModel.player.player.seekTo((sliderPosition * duration).toLong()) }, modifier = Modifier.fillMaxWidth(), colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant))
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val playerState = viewModel.uiState.collectAsState().value
                IconButton(onClick = { viewModel.toggleRepeat() }) {
                    Icon(imageVector = when (playerState.repeatMode) { RepeatMode.NONE -> Icons.Default.Repeat; RepeatMode.ONE -> Icons.Default.RepeatOne; RepeatMode.ALL -> Icons.Default.Repeat }, contentDescription = "Repeat mode", tint = if (playerState.repeatMode != RepeatMode.NONE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { viewModel.playPrevious() }) { Icon(Icons.Default.SkipPrevious, "Previous", tint = MaterialTheme.colorScheme.onSurface) }
                FloatingActionButton(onClick = { viewModel.togglePlay() }, modifier = Modifier.size(72.dp), shape = CircleShape, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (isPlaying) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { viewModel.playNext() }) { Icon(Icons.Default.SkipNext, "Next", tint = MaterialTheme.colorScheme.onSurface) }
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(imageVector = Icons.Default.Shuffle, contentDescription = "Shuffle", tint = if (playerState.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(24.dp))

            // 🔘 Действия (только Скачать/Удалить и Плейлист)
            var showDeleteDialog by remember { mutableStateOf(false) }
            var showPlaylistDialog by remember { mutableStateOf(false) }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                // 🔄 Кнопка Скачать / Удалить
                IconButton(
                    onClick = {
                        if (currentTrack.isOffline) {
                            showDeleteDialog = true
                        } else {
                            Toast.makeText(context, "Скачивание...", Toast.LENGTH_SHORT).show()
                            viewModel.downloadTrack(context, currentTrack)
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (currentTrack.isOffline) Icons.Default.Delete else Icons.Default.Download,
                        contentDescription = if (currentTrack.isOffline) "Удалить" else "Скачать",
                        tint = if (currentTrack.isOffline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }

                // 📋 Добавить в плейлист
                IconButton(onClick = { showPlaylistDialog = true }) {
                    Icon(Icons.Default.PlaylistAdd, "Add to playlist", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            // ✅ Диалог подтверждения удаления
            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Удалить трек?") },
                    text = { Text("Файл будет удалён с устройства.") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteOfflineTrack(context, currentTrack)
                            showDeleteDialog = false
                        }) {
                            Text("Удалить", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") }
                    }
                )
            }

            // ✅ Диалог добавления в плейлист
            if (showPlaylistDialog) {
                AddToPlaylistDialog(viewModel, currentTrack, onDismiss = { showPlaylistDialog = false })
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun formatTime(timeMs: Long): String {
    if (timeMs < 0 || timeMs == C.TIME_UNSET) return "0:00"
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
fun AddToPlaylistDialog(viewModel: MusicViewModel, track: com.example.dystopia.data.TrackInfo, onDismiss: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Сохранить в плейлист") }, text = {
        Column {
            if (state.playlists.isEmpty()) Text("Нет созданных плейлистов", style = MaterialTheme.typography.bodyMedium)
            else state.playlists.forEach { playlist ->
                TextButton(onClick = { viewModel.saveTrackToPlaylist(playlist.id, track); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text(playlist.name) }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}