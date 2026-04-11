package com.example.dystopia.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update

@Database(entities = [Playlist::class, PlaylistTrack::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "music_db")
                    .fallbackToDestructiveMigration() // Стирает БД при изменении структуры
                    .build().also { instance = it }
            }
        }
    }
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylists(): List<Playlist>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrackToPlaylist(track: PlaylistTrack)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getTracksByPlaylist(playlistId: Long): List<PlaylistTrack>

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    // ✅ Для удаления трека из плейлиста
    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackUrl = :trackUrl")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackUrl: String)
}