package com.example.dystopia

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection  // ✅ Добавь это
import com.example.dystopia.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MusicViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val state by viewModel.uiState.collectAsState()
    var showFullPlayer by remember { mutableStateOf(false) }
    val layoutDirection = LocalLayoutDirection.current  // ✅ Получаем направление

    Scaffold(
        bottomBar = {
            Column {
                state.currentTrack?.let {
                    MiniPlayer(
                        viewModel = viewModel,
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
    ) { innerPadding ->
        Box(
            modifier = Modifier.padding(
                start = innerPadding.calculateLeftPadding(layoutDirection),  // ✅ Теперь работает
                end = innerPadding.calculateRightPadding(layoutDirection),    // ✅ Теперь работает
                bottom = innerPadding.calculateBottomPadding()
            )
        ) {
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