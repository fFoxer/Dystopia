package com.example.dystopia.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
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

    // ✅ Берём данные ПРЯМО из текущего снапшота состояния
    val tracks = state.artistSearchItems.filterIsInstance<SearchItem.TrackResult>().map { it.track }
    val playlists = state.artistSearchItems.filterIsInstance<SearchItem.PlaylistResult>()

    // 🐞 Отладка: выводим в Logcat что реально пришло
    LaunchedEffect(state.artistSearchItems) {
        Log.d("ArtistScreen", "📦 Total items: ${state.artistSearchItems.size}")
        Log.d("ArtistScreen", "🎵 Tracks: ${tracks.size}, 📁 Playlists: ${playlists.size}")
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
            // ✅ Вкладки
            TabRow(selectedTabIndex = if (state.activeArtistTab == ArtistTab.TRACKS) 0 else 1) {
                Tab(
                    selected = state.activeArtistTab == ArtistTab.TRACKS,
                    onClick = { viewModel.setArtistTab(ArtistTab.TRACKS) },
                    text = { Text("Треки (${tracks.size})") }
                )
                Tab(
                    selected = state.activeArtistTab == ArtistTab.PLAYLISTS,
                    onClick = { viewModel.setArtistTab(ArtistTab.PLAYLISTS) },
                    text = { Text("Альбомы (${playlists.size})") }
                )
            }

            // ✅ Контент с исправленной логикой
            when (state.activeArtistTab) {
                ArtistTab.TRACKS -> {
                    if (state.isLoading) {
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (tracks.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Треки не найдены 😔")
                        }
                    } else {
                        LazyColumn {
                            items(tracks, key = { it.cleanTitle }) { result ->
                                SearchResultItem(
                                    result = result,
                                    onClick = { onTrackClick(result) }
                                )
                            }
                        }
                    }
                }

                ArtistTab.PLAYLISTS -> {
                    if (state.isLoading) {
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (playlists.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.QueueMusic, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Text("Альбомы пока не парсятся", textAlign = TextAlign.Center)
                                Text("Доступны только треки", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyColumn {
                            items(playlists, key = { it.pageUrl }) { playlist ->
                                PlaylistSearchItem(
                                    playlist = playlist,
                                    onClick = { onAlbumClick(playlist) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistSearchItem(
    playlist: SearchItem.PlaylistResult,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!playlist.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.QueueMusic, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(text = playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (playlist.trackCount.isNotBlank()) {
                    Text(text = "🎵 ${playlist.trackCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SearchResultItem(result: SearchResult, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(result.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(result.cleanTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = { Icon(Icons.Default.PlayArrow, "Play") },
        modifier = Modifier.clickable(onClick = onClick)
    )
}