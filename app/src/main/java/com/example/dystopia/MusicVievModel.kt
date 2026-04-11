package com.example.dystopia

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dystopia.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

enum class RepeatMode { NONE, ONE, ALL }

data class UiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val isFetchingDetails: Boolean = false,
    val error: String? = null,
    val currentTrack: TrackInfo? = null,
    val playlists: List<Playlist> = emptyList(),
    val selectedPlaylist: Playlist? = null,
    val playlistTracks: List<PlaylistTrack> = emptyList(),
    val downloadMessage: String? = null,
    val isDownloading: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.NONE,
    val shuffleEnabled: Boolean = false,
    val currentPlaylistIndex: Int = -1,
    val shuffledPlaylistIndices: List<Int> = emptyList(),
    val playedIndices: MutableSet<Int> = mutableSetOf(),
    val searchResults: List<SearchResult> = emptyList()
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val parser = PesniParser()
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.playlistDao()

    val player: MusicPlayer = MusicPlayer(application)
    private val coverCache = mutableMapOf<String, String>()

    private var isServiceStarted = false
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadPlaylists()
        setupPlayerListener()
    }

    // ✅ Запуск MediaSession сервиса


    // ✅ Обновление метаданных трека для уведомления
    fun updateMediaMetadata(title: String, artist: String = "Dystopia Music", coverUrl: String? = null) {
        // MediaSession автоматически обновит уведомление при изменении трека
        // ExoPlayer сам обрабатывает это через MediaMetadata
    }
    private fun setupPlayerListener() {
        player.player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.common.Player.STATE_ENDED) {
                    handleTrackEnded()
                }
            }
        })
    }

    // === НАВИГАЦИЯ И АВТОПЛЕЙ ===

    private fun getNavigationQueue(state: UiState): List<Int> {
        return if (state.shuffleEnabled && state.shuffledPlaylistIndices.isNotEmpty()) {
            state.shuffledPlaylistIndices
        } else {
            state.playlistTracks.indices.toList()
        }
    }

    private fun findNextIndex(state: UiState, currentIndex: Int, skipPlayed: Boolean = false): Int? {
        val queue = getNavigationQueue(state)
        val currentPos = queue.indexOf(currentIndex)
        for (i in currentPos + 1 until queue.size) {
            val idx = queue[i]
            if (!skipPlayed || idx !in state.playedIndices) return idx
        }
        return null
    }

    private fun findPrevIndex(state: UiState, currentIndex: Int): Int? {
        val queue = getNavigationQueue(state)
        val currentPos = queue.indexOf(currentIndex)
        return if (currentPos > 0) queue[currentPos - 1] else null
    }

    private fun handleTrackEnded() {
        val state = _uiState.value
        val tracks = state.playlistTracks
        if (tracks.isEmpty() || state.currentPlaylistIndex < 0) return

        val played = state.playedIndices.toMutableSet()
        played.add(state.currentPlaylistIndex)

        var nextIndex = if (state.repeatMode == RepeatMode.ONE) {
            state.currentPlaylistIndex
        } else {
            findNextIndex(state, state.currentPlaylistIndex, skipPlayed = true)
        }

        if (nextIndex == null) {
            if (state.repeatMode == RepeatMode.ALL) {
                played.clear()
                nextIndex = getNavigationQueue(state).firstOrNull() ?: 0
            } else {
                _uiState.value = state.copy(playedIndices = played)
                return
            }
        }

        _uiState.value = state.copy(currentPlaylistIndex = nextIndex, playedIndices = played)
        val track = tracks[nextIndex]
        playTrack(TrackInfo(track.title, track.trackUrl), getApplication())
    }

    fun playNext() {
        val state = _uiState.value
        val tracks = state.playlistTracks
        if (tracks.isEmpty() || state.currentPlaylistIndex < 0) return

        var nextIndex = if (state.repeatMode == RepeatMode.ONE) {
            state.currentPlaylistIndex
        } else {
            findNextIndex(state, state.currentPlaylistIndex, skipPlayed = false)
        }

        if (nextIndex == null) {
            if (state.repeatMode == RepeatMode.ALL) {
                _uiState.value = state.copy(playedIndices = mutableSetOf())
                nextIndex = getNavigationQueue(state).firstOrNull() ?: 0
            } else {
                return
            }
        }

        _uiState.value = state.copy(currentPlaylistIndex = nextIndex)
        val track = tracks[nextIndex]
        playTrack(TrackInfo(track.title, track.trackUrl), getApplication())
    }

    fun playPrevious() {
        val state = _uiState.value
        val tracks = state.playlistTracks
        if (tracks.isEmpty() || state.currentPlaylistIndex < 0) return

        if (player.player.currentPosition > 3000) {
            player.player.seekTo(0)
            return
        }

        var prevIndex = if (state.repeatMode == RepeatMode.ONE) {
            state.currentPlaylistIndex
        } else {
            findPrevIndex(state, state.currentPlaylistIndex)
        }

        if (prevIndex == null) {
            if (state.repeatMode == RepeatMode.ALL) {
                prevIndex = getNavigationQueue(state).lastOrNull() ?: tracks.size - 1
            } else {
                return
            }
        }

        _uiState.value = state.copy(currentPlaylistIndex = prevIndex)
        val track = tracks[prevIndex]
        playTrack(TrackInfo(track.title, track.trackUrl), getApplication())
    }

    // === ПУБЛИЧНЫЕ МЕТОДЫ ===

    fun updateQuery(q: String) {
        _uiState.value = _uiState.value.copy(query = q)
    }

    fun searchTrack() {
        val q = _uiState.value.query.trim()
        if (q.length < 2) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val track = parser.getTrack(q)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentTrack = track,
                    error = null,
                    currentPlaylistIndex = -1,
                    selectedPlaylist = null
                )
                player.play(track.url)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun searchTracks(query: String) {
        val q = query.trim()
        if (q.length < 2) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, searchResults = emptyList(), error = null)
            try {
                val results = parser.searchTracks(q)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    searchResults = results,
                    error = if (results.isEmpty()) "Ничего не найдено" else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun playSearchResult(result: SearchResult, context: Context? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isFetchingDetails = true, error = null)
            try {
                val trackInfo = parser.getTrackDetails(result.pageUrl)
                var finalTrack = trackInfo

                if (context != null) {
                    val localPath = OfflineManager.getOfflinePath(context, trackInfo.title)
                    if (localPath != null && File(localPath).exists()) {
                        finalTrack = trackInfo.copy(url = localPath, isOffline = true)
                        println("🎵 Playing OFFLINE: $localPath")
                    } else {
                        println("🌐 Playing ONLINE: ${trackInfo.url}")
                    }
                }

                _uiState.value = _uiState.value.copy(isFetchingDetails = false, currentTrack = finalTrack)
                player.play(finalTrack.url)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isFetchingDetails = false,
                    error = "Ошибка загрузки: ${e.message}"
                )
            }
        }
    }

    fun togglePlay() {
        if (player.isPlaying.value) player.pause() else player.resume()
    }

    fun toggleRepeat() {
        val newMode = when (_uiState.value.repeatMode) {
            RepeatMode.NONE -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.NONE
        }
        _uiState.value = _uiState.value.copy(repeatMode = newMode)
    }

    fun toggleShuffle() {
        val state = _uiState.value
        val newShuffleEnabled = !state.shuffleEnabled
        val newShuffledIndices = if (newShuffleEnabled && state.playlistTracks.isNotEmpty()) {
            state.playlistTracks.indices.shuffled()
        } else {
            emptyList()
        }

        _uiState.value = state.copy(
            shuffleEnabled = newShuffleEnabled,
            shuffledPlaylistIndices = newShuffledIndices,
            playedIndices = mutableSetOf(),
            currentPlaylistIndex = if (newShuffleEnabled && newShuffledIndices.isNotEmpty()) newShuffledIndices[0] else 0
        )
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            val list = dao.getAllPlaylists()
            _uiState.value = _uiState.value.copy(playlists = list)
        }
    }

    fun createPlaylist(name: String, desc: String, cover: String?) {
        viewModelScope.launch {
            val newPlaylist = Playlist(name = name, description = desc, coverUrl = cover)
            dao.insertPlaylist(newPlaylist)
            loadPlaylists()
        }
    }

    fun updatePlaylist(playlist: Playlist, newName: String, newDesc: String, newCover: String?) {
        viewModelScope.launch {
            val updated = playlist.copy(
                name = newName,
                description = newDesc,
                coverUrl = newCover
            )
            dao.updatePlaylist(updated)
            loadPlaylists()
            if (_uiState.value.selectedPlaylist?.id == playlist.id) {
                _uiState.value = _uiState.value.copy(selectedPlaylist = updated)
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            dao.deletePlaylist(playlist)
            loadPlaylists()
            if (_uiState.value.selectedPlaylist?.id == playlist.id) {
                goBackToPlaylists()
            }
        }
    }

    fun openPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val tracks = dao.getTracksByPlaylist(playlist.id)

            // ✅ Проверяем какие треки скачаны
            val context = getApplication<Application>().applicationContext
            val tracksWithOfflineStatus = tracks.map { track ->
                val isOffline = OfflineManager.isOffline(context, track.title)
                track // В базе нет поля isOffline, но мы можем проверить наличие файла
            }

            _uiState.value = _uiState.value.copy(
                selectedPlaylist = playlist,
                playlistTracks = tracks,
                currentPlaylistIndex = 0,
                shuffledPlaylistIndices = emptyList(),
                shuffleEnabled = false,
                playedIndices = mutableSetOf()
            )
        }
    }

    fun goBackToPlaylists() {
        _uiState.value = _uiState.value.copy(
            selectedPlaylist = null,
            playlistTracks = emptyList(),
            currentPlaylistIndex = -1,
            shuffledPlaylistIndices = emptyList(),
            playedIndices = mutableSetOf()
        )
    }

    fun saveTrackToPlaylist(playlistId: Long, track: TrackInfo) {
        viewModelScope.launch {
            dao.addTrackToPlaylist(PlaylistTrack(playlistId, track.url, track.title))
            if (_uiState.value.selectedPlaylist?.id == playlistId) {
                openPlaylist(_uiState.value.selectedPlaylist!!)
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackUrl: String) {
        viewModelScope.launch {
            dao.removeTrackFromPlaylist(playlistId, trackUrl)
            if (_uiState.value.selectedPlaylist?.id == playlistId) {
                openPlaylist(_uiState.value.selectedPlaylist!!)
            }
        }
    }

    fun playPlaylistTrack(playlist: Playlist, tracks: List<PlaylistTrack>, index: Int, context: Context) {
        val track = tracks.getOrNull(index) ?: return

        viewModelScope.launch {
            val localPath = OfflineManager.getOfflinePath(context, track.title)

            var finalUrl = track.trackUrl
            var isOffline = false

            if (localPath != null && File(localPath).exists()) {
                finalUrl = localPath
                isOffline = true
                println("✅ Playing OFFLINE: $localPath")
            } else {
                println("❌ Playing ONLINE: ${track.trackUrl}")
            }

            // ✅ ЗАГРУЖАЕМ ОБЛОЖКУ
            val coverUrl = getCoverUrl(track.title)

            val trackInfo = TrackInfo(
                title = track.title,
                url = finalUrl,
                coverUrl = coverUrl,
                isOffline = isOffline
            )

            _uiState.value = _uiState.value.copy(
                selectedPlaylist = playlist,
                playlistTracks = tracks,
                currentTrack = trackInfo,
                currentPlaylistIndex = index,
                playedIndices = mutableSetOf()
            )
            player.play(finalUrl)
        }
    }

    fun playTrack(track: TrackInfo, context: Context? = null) {
        viewModelScope.launch {
            var finalTrack = track

            if (context != null) {
                val localPath = OfflineManager.getOfflinePath(context, track.title)
                if (localPath != null && File(localPath).exists()) {
                    finalTrack = track.copy(url = localPath, isOffline = true)
                    println("🎵 Playing OFFLINE: $localPath")
                } else {
                    println("🌐 Playing ONLINE: ${track.url}")
                }
            }

            if (finalTrack.coverUrl == null) {
                val cover = getCoverUrl(finalTrack.title)
                if (cover != null) finalTrack = finalTrack.copy(coverUrl = cover)
            }

            _uiState.value = _uiState.value.copy(currentTrack = finalTrack, error = null)
            player.play(finalTrack.url)
        }
    }

    suspend fun getCoverUrl(trackName: String): String? {
        if (coverCache.containsKey(trackName)) return coverCache[trackName]

        return withContext(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(trackName, "UTF-8")
                val itunesUrl = "https://itunes.apple.com/search?term=$encoded&media=music&limit=1"
                val request = Request.Builder().url(itunesUrl).get().build()
                val response = OkHttpClient().newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null

                val artworkUrl100 = Regex("\"artworkUrl100\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                val finalUrl = artworkUrl100?.replace("100x100bb", "600x600bb")

                if (finalUrl != null) coverCache[trackName] = finalUrl
                finalUrl
            } catch (e: Exception) { null }
        }
    }

    private suspend fun getTrackInfo(title: String, url: String, context: Context): TrackInfo {
        val coverUrl = getCoverUrl(title)
        val isOffline = OfflineManager.isOffline(context, title)
        return TrackInfo(title, url, coverUrl, isOffline)
    }

    fun getPlaylistStats(tracks: List<PlaylistTrack>): Pair<String, String> {
        val trackCount = tracks.size
        val totalMinutes = tracks.size * 3
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val duration = if (hours > 0) "${hours}ч ${minutes}мин" else "${minutes}мин"
        return Pair("$trackCount трек${if (trackCount % 10 == 1 && trackCount != 11) "" else "а"}", duration)
    }

    // === СКАЧИВАНИЕ ===

    fun downloadTrack(context: Context, track: TrackInfo) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isDownloading = true, downloadMessage = null)
                val localPath = FileDownloader.download(context, track.url, track.title)
                OfflineManager.saveOfflineTrack(context, track.title, localPath)
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    downloadMessage = "✅ Скачано: ${track.title}",
                    currentTrack = track.copy(isOffline = true)
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    downloadMessage = "❌ Ошибка: ${e.message}"
                )
            }
        }
    }

    fun deleteOfflineTrack(context: Context, track: TrackInfo) {
        viewModelScope.launch {
            val success = OfflineManager.removeOfflineTrack(context, track.title)
            _uiState.value = _uiState.value.copy(
                downloadMessage = if (success) "🗑️ Трек удалён" else "❌ Не удалось удалить",
                currentTrack = track.copy(isOffline = !success)
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}