package com.example.dystopia.data

import androidx.room.Entity

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "trackUrl"])
data class PlaylistTrack(
    val playlistId: Long,
    val trackUrl: String,
    val title: String
)