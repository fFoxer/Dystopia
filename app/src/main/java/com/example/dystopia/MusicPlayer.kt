package com.example.dystopia

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicPlayer(context: Context) {
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentCover = MutableStateFlow<String?>(null)
    val currentCover: StateFlow<String?> = _currentCover.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    internal var controller: MediaController? = null

    init {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken)
            .buildAsync()
            .also { future ->
                future.addListener({
                    controller = future.get()
                    setupPlayerListener()
                    setupMetadataListener()
                }, ContextCompat.getMainExecutor(context))
            }
    }

    private fun setupPlayerListener() {
        controller?.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }
        })
    }

    private fun setupMetadataListener() {
        controller?.addListener(object : androidx.media3.common.Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                // ExoPlayer извлёк встроенную обложку из MP3
                val embeddedUri = mediaMetadata.artworkUri?.toString()

                if (!embeddedUri.isNullOrBlank()) {
                    _currentCover.value = embeddedUri
                }
            }
        })
    }

    fun play(
        url: String,
        title: String,
        artist: String = "Dystopia Music",
        coverUrl: String? = null,
        playlist: List<MediaItem>? = null
    ) {
        val controller = controller ?: return


        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)

        if (coverUrl != null) {
            metadataBuilder.setArtworkUri(Uri.parse(coverUrl))
        }

        val metadata = metadataBuilder.build()

        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(metadata)
            .build()

        if (playlist != null && playlist.isNotEmpty()) {
            controller.setMediaItems(playlist)
            val currentIndex = playlist.indexOf(mediaItem)
            if (currentIndex >= 0) controller.seekTo(currentIndex, 0)
        } else {
            controller.setMediaItem(mediaItem)
        }

        controller.prepare()
        controller.play()


        if (coverUrl != null) {
            _currentCover.value = coverUrl
        }
    }

    fun pause() = controller?.pause()
    fun resume() = controller?.play()
    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs)

    val currentPosition: Long get() = controller?.currentPosition ?: 0L
    val duration: Long get() = controller?.duration ?: 0L

    fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}