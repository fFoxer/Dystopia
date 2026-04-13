package com.example.dystopia

import com.example.dystopia.data.SearchItem
import com.example.dystopia.data.SearchResult
import com.example.dystopia.data.TrackInfo

interface MusicParser {
    val name: String

    // ✅ Значение по умолчанию ТОЛЬКО здесь
    suspend fun searchTracks(query: String, limit: Int = 20): List<SearchResult>
    suspend fun getTrackDetails(pageUrl: String): TrackInfo
    suspend fun searchArtistContent(artistName: String, limit: Int = 50): List<SearchItem>
}