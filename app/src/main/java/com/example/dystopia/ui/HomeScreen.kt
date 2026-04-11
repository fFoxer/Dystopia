package com.example.dystopia.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
fun HomeScreen(viewModel: MusicViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val parsers = viewModel.getAvailableParsers()
    var expanded by remember { mutableStateOf(false) }
    var selectedParser by remember { mutableStateOf("Zvukofon.com") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(48.dp))


        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedParser,
                onValueChange = {},
                label = { Text("Источник поиска") },
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                parsers.forEach { parser ->
                    DropdownMenuItem(
                        text = { Text(parser) },
                        onClick = {
                            selectedParser = parser
                            viewModel.setParser(parser)
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Строка поиска (без изменений)
        OutlinedTextField(
            value = state.query,
            onValueChange = { viewModel.updateQuery(it) },
            label = { Text("Название трека или исполнитель") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )


        Button(
            onClick = { viewModel.searchTracks(state.query) },
            enabled = !state.isLoading && state.query.trim().length >= 2,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isLoading) "Поиск..." else "Найти")
        }

        if (state.error != null) {
            Text(
                state.error!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (state.searchResults.isNotEmpty()) {
            Text(
                "Результаты:",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.searchResults) { result ->
                    SearchResultItem(
                        result = result,
                        onClick = { viewModel.playSearchResult(result, context) },
                        isLoading = state.isFetchingDetails,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(
    result: SearchResult,
    onClick: () -> Unit,
    isLoading: Boolean,
    viewModel: MusicViewModel
) {
    var coverUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(result.cleanTitle) {
        coverUrl = viewModel.getCoverUrl(result.cleanTitle)
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Icon(Icons.Default.PlayArrow, "Play", modifier = Modifier.size(32.dp))
            }

            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = result.displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}