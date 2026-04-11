package com.example.dystopia

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.dystopia.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MusicViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val state by viewModel.uiState.collectAsState()
    val isPlaying by viewModel.player.isPlaying.collectAsState()
    var showFullPlayer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.resetNavigation.collect {
            selectedTab = 0          // Переходим на вкладку "Поиск"
            showFullPlayer = false   // Закрываем полный плеер
        }
    }

    Scaffold(
        bottomBar = {
            Column {
                // ✅ Показываем мини-плеер если есть трек
                if (state.currentTrack != null) {
                    MiniPlayer(
                        viewModel = viewModel,
                        isPlaying = isPlaying,
                        onClick = { showFullPlayer = true }
                    )
                    HorizontalDivider()
                }

                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, "Home") },
                        label = { Text("Поиск") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.LibraryMusic, "Playlists") },
                        label = { Text("Плейлисты") }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> HomeScreen(viewModel)
                1 -> PlaylistsScreen(viewModel)
            }
        }
    }

    if (showFullPlayer) {
        FullPlayerScreen(
            viewModel = viewModel,
            onBack = { showFullPlayer = false }
        )
    }
}

// 🎵 MiniPlayer с обложкой из MediaMetadata
@Composable
fun MiniPlayer(
    viewModel: MusicViewModel,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val currentTrack = state.currentTrack ?: return

    // ✅ Берем обложку из MediaSession/ExoPlayer
    val coverUri by viewModel.player.currentCover.collectAsState()

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 📀 Обложка
            if (!coverUri.isNullOrBlank()) {
                AsyncImage(
                    model = coverUri,
                    contentDescription = "Cover",
                    modifier = Modifier.size(40.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                androidx.compose.material3.Icon(
                    Icons.Default.LibraryMusic,
                    "Cover",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 🎵 Информация о треке
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = currentTrack.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Dystopia Music",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ▶️ Кнопка Play/Pause
            IconButton(
                onClick = { viewModel.togglePlay() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}