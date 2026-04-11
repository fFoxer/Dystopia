package com.example.dystopia.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object FileDownloader {
    suspend fun download(context: Context, url: String, title: String): String = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) throw Exception("Ошибка сети: ${response.code}")

        val dir = File(context.filesDir, "offline_music")
        if (!dir.exists()) dir.mkdirs()

        val safeTitle = title.replace(Regex("[^a-zA-Z0-9а-яА-Я ]"), "").trim()
        val file = File(dir, "$safeTitle.mp3")

        response.body?.byteStream()?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }

        file.absolutePath
    }
}