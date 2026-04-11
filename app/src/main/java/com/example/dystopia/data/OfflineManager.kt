package com.example.dystopia.data

import android.content.Context
import android.os.Environment
import java.io.File

object OfflineManager {
    private const val PREFS_NAME = "offline_tracks"
    private const val KEY_PREFIX = "track_"
    private const val MUSIC_FOLDER = "offline_music"  // ✅ Правильная папка

    fun getOfflinePath(context: Context, trackTitle: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 🔍 Пробуем найти по ключу track_Название
        val fileName = prefs.getString(KEY_PREFIX + trackTitle, null)

        println("📂 OfflineManager lookup:")
        println("   Track title: $trackTitle")
        println("   Key: ${KEY_PREFIX + trackTitle}")
        println("   File name from prefs: $fileName")

        if (fileName != null) {
            // ✅ Используем правильную папку offline_music
            val musicDir = File(context.filesDir, MUSIC_FOLDER)
            println("   Music dir: $musicDir")

            val file = File(musicDir, fileName)
            println("   Full path: ${file.absolutePath}")
            println("   Exists: ${file.exists()}")

            return if (file.exists()) file.absolutePath else null
        }

        return null
    }

    fun isOffline(context: Context, trackTitle: String): Boolean {
        return getOfflinePath(context, trackTitle) != null
    }

    fun saveOfflineTrack(context: Context, trackTitle: String, filePath: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val fileName = File(filePath).name
        prefs.edit().putString(KEY_PREFIX + trackTitle, fileName).apply()
        println("💾 Saved offline track: $trackTitle -> $fileName")
    }

    fun removeOfflineTrack(context: Context, trackTitle: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val fileName = prefs.getString(KEY_PREFIX + trackTitle, null) ?: return false

        val musicDir = File(context.filesDir, MUSIC_FOLDER)
        val file = File(musicDir, fileName)
        val deleted = file.delete()

        if (deleted) {
            prefs.edit().remove(KEY_PREFIX + trackTitle).apply()
            println("🗑️ Deleted offline track: $trackTitle")
        }

        return deleted
    }
}