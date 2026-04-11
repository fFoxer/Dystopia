package com.example.dystopia

import com.example.dystopia.data.SearchResult
import com.example.dystopia.data.TrackInfo

// ✅ Общий интерфейс для всех парсеров
interface MusicParser {
    val name: String  // Название для отображения в UI

    suspend fun searchTracks(query: String): List<SearchResult>
    suspend fun getTrackDetails(pageUrl: String): TrackInfo
}