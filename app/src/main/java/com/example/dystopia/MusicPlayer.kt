package com.example.dystopia

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class MusicPlayer(context: Context) {
    private val httpDataSourceFactory = DefaultHttpDataSource.Factory().apply {
        setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        setDefaultRequestProperties(mapOf("Referer" to "https://music.pesni.me/"))
    }

    private val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }
        })
    }

    fun play(url: String) {
        val mediaItem = if (url.startsWith("file://") || File(url).exists()) {
            // Локальный файл
            println("📁 Loading local file: $url")
            MediaItem.fromUri(android.net.Uri.fromFile(File(url)))
        } else {
            // Сетевой URL
            println("🌐 Loading network URL: $url")
            MediaItem.fromUri(url)
        }

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    fun pause() = player.pause()

    fun resume() = player.play()

    fun release() = player.release()
}