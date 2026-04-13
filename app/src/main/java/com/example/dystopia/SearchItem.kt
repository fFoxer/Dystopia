package com.example.dystopia.data

// ✅ Единый тип для любого результата поиска
sealed class SearchItem {
    data class TrackResult(val track: SearchResult) : SearchItem()
    data class PlaylistResult(
        val name: String,
        val pageUrl: String,
        val trackCount: String = "",
        val coverUrl: String? = null
    ) : SearchItem()
}