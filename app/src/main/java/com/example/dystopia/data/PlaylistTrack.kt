package com.example.dystopia.data

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackUrl"],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaylistTrack(
    val playlistId: Long,
    val trackUrl: String,
    val title: String,
    val coverUrl: String? = null  // ✅ Добавили поле для обложки
)
