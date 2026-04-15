package com.example.dystopia

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.MediaController
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
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import android.widget.Toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.example.dystopia.ArtistTab
import com.example.dystopia.data.SearchItem


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
    val searchResults: List<SearchResult> = emptyList(),
    val isRecommendationMode: Boolean = false,
    val artistSearchItems: List<SearchItem> = emptyList(),  // ✅ Для поиска артиста
    val activeArtistTab: ArtistTab = ArtistTab.TRACKS
// ✅ Текущая вкладка
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
        fun setupPlayerListener() {
            viewModelScope.launch {
                // Ждём подключения к сервису
                kotlinx.coroutines.delay(500)

                val controller = player.controller ?: return@launch


                // Устанавливаем слушателя на кнопки
                controller.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                        if (events.contains(androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY)) {
                            // Можно добавить логику автоперехода
                        }
                    }
                })
            }
        }

        syncFromService()
    }

    // ✅ Поиск ВСЕХ треков исполнителя (без лимита)
    fun searchAllArtistTracks(artistName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                searchResults = emptyList(),
                query = "🎤 $artistName (все треки)",
                isRecommendationMode = false
            )

            try {
                // ✅ Передаем 0 как лимит = без ограничений
                val results = selectedParser.searchTracks(artistName, limit = 0)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    searchResults = results
                )

                println("🎵 Found ${results.size} tracks for $artistName")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Ошибка поиска: ${e.message}"
                )
            }
        }
    }

    // ✅ Функция для получения имени исполнителя
    private fun getArtistFromTitle(fullTitle: String): String {
        // Если есть разделитель " - ", берем первую часть
        return if (fullTitle.contains(" - ")) {
            fullTitle.substringBefore(" - ").trim()
        } else {
            "Неизвестный исполнитель"
        }
    }

    // ✅ Функция просмотра треков исполнителя
    fun openArtistTracks(artistName: String) {
        // Переходим на вкладку поиска (индекс 0)
        // Примечание: чтобы это работало, нам нужно управлять selectedTab из ViewModel
        // Но пока проще просто запустить поиск и показать результаты

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            searchResults = emptyList(),
            query = "🎤 $artistName", // Показываем, что ищем артиста
            currentTrack = null // Скрываем мини-плеер, чтобы видеть список
        )

        viewModelScope.launch {
            try {
                // Ищем треки по имени артиста
                val results = selectedParser.searchTracks(artistName)

                // Опционально: Фильтруем результаты, оставляя только те, где есть имя артиста
                val filteredResults = results.filter {
                    it.displayTitle.contains(artistName, ignoreCase = true)
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    searchResults = if (filteredResults.isNotEmpty()) filteredResults else results
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Не удалось загрузить треки: ${e.message}"
                )
            }
        }
    }

    fun syncFromService() {
        val controller = player.controller ?: return

        viewModelScope.launch {
            val item = controller.currentMediaItem
            if (item != null) {
                val metadata = item.mediaMetadata
                val title = metadata.title?.toString() ?: return@launch

                _uiState.value = _uiState.value.copy(
                    currentTrack = TrackInfo(
                        title = title,
                        url = item.localConfiguration?.uri?.toString() ?: "",
                        coverUrl = metadata.artworkUri?.toString(),
                        isOffline = false
                    )
                )
            }
        }
    }


    fun startMediaService(context: Context) {
        if (!isServiceStarted) {
            val intent = android.content.Intent(context, MusicService::class.java)
            ContextCompat.startForegroundService(context, intent)
            isServiceStarted = true
        }
    }
    private var selectedParser: MusicParser = ZvukofonParser()


    fun setParser(parserName: String) {
        selectedParser = when (parserName) {
            "Zvukofon.com" -> ZvukofonParser()
            else -> PesniParser()
        }
        println("🔄 Parser changed to: ${selectedParser.name}")
    }

    fun getAvailableParsers(): List<String> {
        return listOf("Zvukofon.com", "Pesni.me")
    }

    fun updateMediaMetadata(title: String, artist: String = "Dystopia Music", coverUrl: String? = null) {
    }
    private fun setupPlayerListener() {

        viewModelScope.launch {

        }
    }



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

        if (player.currentPosition > 3000) {
            player.seekTo(0)
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
                player.play(
                    url = track.url,
                    title = track.title,
                    artist = "Dystopia Music",

                )
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
                val results = selectedParser.searchTracks(q, limit = 20)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    searchResults = results,
                    error = if (results.isEmpty()) "Ничего не найдено в ${selectedParser.name}" else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Ошибка ${selectedParser.name}: ${e.message}"
                )
            }
        }
    }

    fun playSearchResult(result: SearchResult, context: Context? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isFetchingDetails = true, error = null)

            try {
                println("🎵 Playing search result: ${result.displayTitle}")

                // ✅ 1. Загружаем детали трека (только для URL и возможной обложки)
                val trackDetails = selectedParser.getTrackDetails(result.pageUrl)

                // ✅ 2. Извлекаем название и артиста ИЗ SearchResult (они уже правильные!)
                val displayTitle = result.displayTitle
                val (artistFromTitle, titleFromResult) = if (displayTitle.contains(" - ")) {
                    val parts = displayTitle.split(" - ", limit = 2)
                    parts[0].trim() to parts[1].trim()
                } else {
                    null to displayTitle
                }

                println("📝 Parsed: Title='$titleFromResult', Artist='$artistFromTitle'")

                // ✅ 3. Определяем финальный URL (офлайн или онлайн)
                var finalUrl = trackDetails.url
                var isOffline = false

                if (context != null) {
                    val localPath = OfflineManager.getOfflinePath(context, titleFromResult)
                    if (localPath != null && File(localPath).exists()) {
                        finalUrl = localPath
                        isOffline = true
                        println("💾 Using offline track: $localPath")
                    }
                }

                // ✅ 4. Получаем обложку с приоритетами:
                //    1. Из trackDetails (если парсер нашёл)
                //    2. Из iTunes API (по displayTitle для точности)
                //    3. null (если ничего не найдено)
                val coverUrl = trackDetails.coverUrl
                    ?.takeIf { it.isNotBlank() }
                    ?: getCoverUrl(displayTitle)

                println("🖼️ Cover: ${coverUrl ?: "not found"}")

                // ✅ 5. Создаём финальный TrackInfo
                val finalTrack = TrackInfo(
                    title = titleFromResult,      // ✅ Чистое название
                    url = finalUrl,                // ✅ Правильный URL
                    artist = artistFromTitle,      // ✅ Исполнитель
                    coverUrl = coverUrl,           // ✅ Обложка
                    isOffline = isOffline          // ✅ Флаг офлайн
                )

                // ✅ 6. Запускаем сервис если нужно
                if (context != null) {
                    startMediaService(context)
                }

                // ✅ 7. Обновляем UI состояние
                _uiState.value = _uiState.value.copy(
                    isFetchingDetails = false,
                    currentTrack = finalTrack
                )

                // ✅ 8. Запускаем воспроизведение с ПРАВИЛЬНЫМИ метаданными
                player.play(
                    url = finalTrack.url,
                    title = finalTrack.title,           // ✅ Правильное название
                    artist = finalTrack.artist ?: "Dystopia Music",  // ✅ Правильный артист
                    coverUrl = finalTrack.coverUrl      // ✅ Правильная обложка
                )

                println("✅ Now playing: '${finalTrack.title}' by ${finalTrack.artist ?: "Unknown"}")

            } catch (e: Exception) {
                println("❌ Error in playSearchResult: ${e.message}")
                e.printStackTrace()

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


            val context = getApplication<Application>().applicationContext
            val tracksWithOfflineStatus = tracks.map { track ->
                val isOffline = OfflineManager.isOffline(context, track.title)
                track
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
            }

            val coverUrl = getCoverUrl(track.title)


            val mediaItems: List<MediaItem> = tracks.map { t ->
                val itemUrl = if (OfflineManager.isOffline(context, t.title)) {
                    OfflineManager.getOfflinePath(context, t.title) ?: t.trackUrl
                } else {
                    t.trackUrl
                }

                val trackCover = getCoverUrl(t.title)

                MediaItem.Builder()
                    .setUri(itemUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(t.title)
                            .setArtworkUri(if (trackCover != null) Uri.parse(trackCover) else null)
                            .setArtist("Dystopia Music")
                            .build()
                    )
                    .build()
            }

            startMediaService(context)

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


            player.play(
                url = finalUrl,
                title = track.title,
                artist = "Dystopia Music",
                coverUrl = coverUrl,
                playlist = mediaItems

            )
        }
    }

    fun playTrack(track: TrackInfo, context: Context? = null) {
        viewModelScope.launch {
            var finalTrack = track

            if (context != null) {
                val localPath = OfflineManager.getOfflinePath(context, track.title)
                if (localPath != null && File(localPath).exists()) {
                    finalTrack = track.copy(url = localPath, isOffline = true)
                }
            }

            if (context != null) startMediaService(context)

            if (finalTrack.coverUrl == null) {
                val cover = getCoverUrl(finalTrack.title)
                if (cover != null) finalTrack = finalTrack.copy(coverUrl = cover)
            }

            _uiState.value = _uiState.value.copy(currentTrack = finalTrack, error = null)

            // ✅ Передаем метаданные для уведомления
            player.play(
                url = finalTrack.url,
                title = finalTrack.title,
                artist = "Dystopia Music",
                coverUrl = finalTrack.coverUrl

            )
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

    fun syncUiWithMediaSession() {
        viewModelScope.launch {
            var controller = player.controller

            // Ждём подключения
            for (i in 0 until 15) {
                if (controller != null) break
                delay(100)
                controller = player.controller
            }

            if (controller == null) return@launch
            if (controller.playbackState == androidx.media3.common.Player.STATE_IDLE) return@launch

            val item = controller.currentMediaItem ?: return@launch
            val meta = item.mediaMetadata


            val currentCoverUri = player.currentCover.value


            val coverUrl = currentCoverUri ?: meta.artworkUri?.toString()


            val finalCoverUrl = coverUrl ?: getCoverUrl(meta.title?.toString() ?: "")

            _uiState.value = _uiState.value.copy(
                currentTrack = TrackInfo(
                    title = meta.title?.toString() ?: "Unknown Track",
                    url = item.localConfiguration?.uri?.toString() ?: "",
                    coverUrl = finalCoverUrl,  // ✅ Сохраняем обложку
                    isOffline = false
                )
            )

            println("🔄 UI synced: ${meta.title} | Cover: $finalCoverUrl")
        }
    }

    private val _resetNavigation = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val resetNavigation: SharedFlow<Unit> = _resetNavigation.asSharedFlow()

    fun requestNavigationReset() {
        viewModelScope.launch { _resetNavigation.emit(Unit) }
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
    // ✅ Поиск всего контента артиста (треки + плейлисты)
    fun searchArtistFull(artistName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                artistSearchItems = emptyList(),
                activeArtistTab = ArtistTab.TRACKS,
                query = "🎤 $artistName",
                isRecommendationMode = false
            )

            try {
                // ✅ Передаём большой лимит (сайт всё равно отдаст все треки)
                val items = selectedParser.searchArtistContent(artistName, limit = 500)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    artistSearchItems = items
                )

                println("🎵 Loaded ${items.size} items for $artistName")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Ошибка поиска: ${e.message}"
                )
            }
        }
    }

    // ✅ Переключение вкладок
    fun setArtistTab(tab: ArtistTab) {
        _uiState.value = _uiState.value.copy(activeArtistTab = tab)
    }

    // ✅ Получение треков для текущей вкладки (удобно для UI)
    fun getArtistTracks(): List<SearchResult> {
        return _uiState.value.artistSearchItems
            .filterIsInstance<SearchItem.TrackResult>()
            .map { it.track }
    }

    fun getArtistPlaylists(): List<SearchItem.PlaylistResult> {
        return _uiState.value.artistSearchItems
            .filterIsInstance<SearchItem.PlaylistResult>()
    }

    // ✅ Загрузка треков альбома
    fun loadAlbumTracks(albumUrl: String, albumName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                searchResults = emptyList(),
                query = "💿 $albumName"
            )

            try {
                val tracks = (selectedParser as? ZvukofonParser)?.getAlbumTracks(albumUrl) ?: emptyList()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    searchResults = tracks
                )

                println("✅ Loaded ${tracks.size} tracks from album: $albumName")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Ошибка загрузки альбома: ${e.message}"
                )
            }
        }
    }

    // ✅ Выход из режима просмотра альбома
    fun exitAlbumView() {
        _uiState.value = _uiState.value.copy(
            searchResults = emptyList(),
            query = "",
            isLoading = false
        )
    }

    // ✅ Добавить все треки альбома в плейлист
    // ✅ Добавить все треки альбома в плейлист
    // ✅ Добавить все треки альбома в плейлист (с отладкой)
    fun addAllTracksToPlaylist(tracks: List<SearchResult>, playlistName: String = "Мой плейлист") {
        viewModelScope.launch {
            println("📥 [ADD ALL] Starting... Total tracks: ${tracks.size}")
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // ✅ 1. Проверяем или создаём плейлист
                var currentPlaylist = _uiState.value.selectedPlaylist

                if (currentPlaylist == null) {
                    println("📁 [ADD ALL] Creating new playlist: $playlistName")
                    currentPlaylist = Playlist(
                        id = 0,
                        name = playlistName,
                        description = "Автоматически создан",
                        coverUrl = null
                    )

                    dao.insertPlaylist(currentPlaylist)

                    delay(100)
                    val playlists = dao.getAllPlaylists()
                    currentPlaylist = playlists.find { it.name == playlistName }
                        ?: playlists.maxByOrNull { it.id }

                    println("📁 [ADD ALL] Playlist created with ID: ${currentPlaylist?.id}")

                    if (currentPlaylist == null) {
                        throw Exception("Не удалось создать плейлист")
                    }

                    loadPlaylists()
                } else {
                    println("📁 [ADD ALL] Using existing playlist: ${currentPlaylist.name} (ID: ${currentPlaylist.id})")
                }

                var addedCount = 0
                var skippedCount = 0
                var failedCount = 0

                // ✅ 2. Добавляем треки с получением MP3 URL
                for ((index, track) in tracks.withIndex()) {
                    println("🎵 [ADD ALL] Processing track $index: ${track.displayTitle}")

                    try {
                        // ✅ Получаем детали трека чтобы взять прямой MP3 URL
                        val trackDetails = selectedParser.getTrackDetails(track.pageUrl)
                        val mp3Url = trackDetails.url

                        println("🔗 [ADD ALL] MP3 URL: $mp3Url")

                        // Проверяем что это действительно MP3 ссылка
                        if (!mp3Url.contains(".mp3", ignoreCase = true)) {
                            println("⚠️ [ADD ALL] Not an MP3 URL, skipping: $mp3Url")
                            failedCount++
                            continue
                        }

                        val newTrack = PlaylistTrack(
                            playlistId = currentPlaylist.id,
                            trackUrl = mp3Url,  // ✅ Сохраняем ПРЯМОЙ MP3 URL
                            title = track.displayTitle
                        )

                        // Проверяем на дубликаты
                        val exists = _uiState.value.playlistTracks.any {
                            it.title == newTrack.title || it.trackUrl == newTrack.trackUrl
                        }

                        if (exists) {
                            println("⏭️ [ADD ALL] Skipping (already exists): ${track.displayTitle}")
                            skippedCount++
                            continue
                        }

                        // Добавляем в БД
                        dao.addTrackToPlaylist(newTrack)
                        println("✅ [ADD ALL] Added: ${track.displayTitle}")
                        addedCount++

                        // Небольшая задержка чтобы не блокировать
                        delay(50)

                    } catch (e: Exception) {
                        println("❌ [ADD ALL] Failed to add track: ${track.displayTitle} - ${e.message}")
                        failedCount++
                    }
                }

                // ✅ 3. Обновляем отображение
                println("🔄 [ADD ALL] Refreshing playlist view...")

                if (_uiState.value.selectedPlaylist?.id == currentPlaylist.id) {
                    openPlaylist(currentPlaylist)
                }

                val message = "✅ Добавлено $addedCount треков${if (skippedCount > 0) " (пропущено $skippedCount)" else ""}${if (failedCount > 0) " (ошибок $failedCount)" else ""}"

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    downloadMessage = message
                )

                println("🎉 [ADD ALL] Complete! Added: $addedCount, Skipped: $skippedCount, Failed: $failedCount")

                val context = getApplication<Application>().applicationContext
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                println("❌ [ADD ALL] Error: ${e.message}")
                e.printStackTrace()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Ошибка: ${e.message}"
                )

                val context = getApplication<Application>().applicationContext
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "❌ Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    // ✅ Получить все плейлисты для диалога
    fun getAllPlaylistsForSelection(): List<Playlist> {
        return _uiState.value.playlists
    }

    // ✅ Добавить треки в выбранный плейлист
    fun addTracksToSelectedPlaylist(playlistId: Long, tracks: List<SearchResult>, playlistName: String = "") {
        viewModelScope.launch {
            println("📥 [ADD TO PLAYLIST] Starting... Playlist ID: $playlistId, Tracks: ${tracks.size}")
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val playlists = dao.getAllPlaylists()
                val currentPlaylist = playlists.find { it.id == playlistId }
                    ?: throw Exception("Плейлист не найден")

                var addedCount = 0
                var skippedCount = 0
                var failedCount = 0

                for ((index, track) in tracks.withIndex()) {
                    println("🎵 [ADD] Processing track $index: ${track.displayTitle}")

                    try {
                        val trackDetails = selectedParser.getTrackDetails(track.pageUrl)
                        val mp3Url = trackDetails.url

                        if (!mp3Url.contains(".mp3", ignoreCase = true)) {
                            println("⚠️ [ADD] Not an MP3 URL, skipping")
                            failedCount++
                            continue
                        }

                        val coverUrl = trackDetails.coverUrl ?: getCoverUrl(track.displayTitle)

                        val newTrack = PlaylistTrack(
                            playlistId = currentPlaylist.id,
                            trackUrl = mp3Url,
                            title = track.displayTitle,
                            coverUrl = coverUrl
                        )

                        val exists = _uiState.value.playlistTracks.any {
                            it.title == newTrack.title || it.trackUrl == newTrack.trackUrl
                        }

                        if (exists) {
                            println("⏭️ [ADD] Skipping (already exists): ${track.displayTitle}")
                            skippedCount++
                            continue
                        }

                        dao.addTrackToPlaylist(newTrack)
                        println("✅ [ADD] Added: ${track.displayTitle}")
                        addedCount++

                        delay(50)

                    } catch (e: Exception) {
                        println("❌ [ADD] Failed: ${track.displayTitle} - ${e.message}")
                        failedCount++
                    }
                }

                if (_uiState.value.selectedPlaylist?.id == currentPlaylist.id) {
                    openPlaylist(currentPlaylist)
                }

                val message = "✅ Добавлено $addedCount треков${if (skippedCount > 0) " (пропущено $skippedCount)" else ""}${if (failedCount > 0) " (ошибок $failedCount)" else ""}"

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    downloadMessage = message
                )

                println("🎉 [ADD] Complete! Added: $addedCount, Skipped: $skippedCount, Failed: $failedCount")

                val context = getApplication<Application>().applicationContext
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                println("❌ [ADD] Error: ${e.message}")
                e.printStackTrace()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Ошибка: ${e.message}"
                )

                val context = getApplication<Application>().applicationContext
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "❌ Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ✅ Создать новый плейлист и добавить треки
    fun createPlaylistAndAddTracks(playlistName: String, tracks: List<SearchResult>) {
        viewModelScope.launch {
            println("📁 [CREATE & ADD] Creating playlist: $playlistName")

            try {
                val newPlaylist = Playlist(
                    id = 0,
                    name = playlistName,
                    description = "Добавлено из трека",
                    coverUrl = null
                )

                dao.insertPlaylist(newPlaylist)

                delay(100)
                val playlists = dao.getAllPlaylists()
                val createdPlaylist = playlists.find { it.name == playlistName }
                    ?: playlists.maxByOrNull { it.id }

                if (createdPlaylist != null) {
                    println("📁 [CREATE & ADD] Playlist created with ID: ${createdPlaylist.id}")
                    loadPlaylists()

                    // Добавляем треки в новый плейлист
                    addTracksToSelectedPlaylist(createdPlaylist.id, tracks, playlistName)
                } else {
                    throw Exception("Не удалось создать плейлист")
                }

            } catch (e: Exception) {
                println("❌ [CREATE & ADD] Error: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Ошибка: ${e.message}"
                )
            }
        }
    }

    // ✅ Добавить один трек в выбранный плейлист
    // ✅ Добавить один трек в выбранный плейлист
    fun addSingleTrackToPlaylist(track: SearchResult, playlistId: Long) {
        viewModelScope.launch {
            println("📥 [ADD SINGLE] Adding: ${track.displayTitle} to playlist $playlistId")

            try {
                val playlists = dao.getAllPlaylists()
                val playlist = playlists.find { it.id == playlistId }
                    ?: throw Exception("Плейлист не найден")

                // Получаем MP3 URL и обложку
                val trackDetails = selectedParser.getTrackDetails(track.pageUrl)
                val mp3Url = trackDetails.url
                val coverUrl = trackDetails.coverUrl ?: getCoverUrl(track.displayTitle)

                if (!mp3Url.contains(".mp3", ignoreCase = true)) {
                    throw Exception("Неверный формат: не MP3")
                }

                val newTrack = PlaylistTrack(
                    playlistId = playlist.id,
                    trackUrl = mp3Url,
                    title = track.displayTitle,
                    coverUrl = coverUrl
                )

                // Проверяем на дубликаты
                val allPlaylistTracks = dao.getTracksByPlaylist(playlist.id)
                val exists = allPlaylistTracks.any {
                    it.title == newTrack.title || it.trackUrl == newTrack.trackUrl
                }

                if (exists) {
                    _uiState.value = _uiState.value.copy(
                        downloadMessage = "⚠️ Трек уже есть в плейлисте"
                    )
                } else {
                    dao.addTrackToPlaylist(newTrack)
                    _uiState.value = _uiState.value.copy(
                        downloadMessage = "✅ Добавлено в '${playlist.name}'"
                    )
                    println("✅ [ADD SINGLE] Track added successfully")
                }

                // Показываем уведомление
                val context = getApplication<Application>().applicationContext
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, _uiState.value.downloadMessage, Toast.LENGTH_SHORT).show()
                }

                delay(2000)
                clearMessage()

            } catch (e: Exception) {
                println("❌ [ADD SINGLE] Error: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    error = "Ошибка: ${e.message}"
                )

                val context = getApplication<Application>().applicationContext
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "❌ Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ✅ Сброс сообщения
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(downloadMessage = null)
    }
}