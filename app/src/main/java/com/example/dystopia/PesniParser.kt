package com.example.dystopia.data

import com.example.dystopia.MusicParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

// 📜 Легкий результат поиска
data class SearchResult(
    val displayTitle: String,
    val cleanTitle: String,
    val pageUrl: String
)

// 🎵 Полный объект трека
data class TrackInfo(
    val title: String,
    val url: String,
    val coverUrl: String? = null,
    val isOffline: Boolean = false,
    val artist: String? = null  // ✅ В конце, с дефолтом
)

class PesniParser : MusicParser {
    override val name: String = "Pesni.me"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://music.pesni.me"

    private val headers = okhttp3.Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .add("Accept-Language", "ru-RU,ru;q=0.9")
        .add("Referer", "https://music.pesni.me/")
        .build()

    // 🔍 1. Быстрый поиск списка треков (с параметром limit)
    override suspend fun searchTracks(query: String, limit: Int): List<SearchResult>  = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder().url("$baseUrl/search/$encoded").headers(headers).build()
        val resp = client.newCall(req).execute()

        if (!resp.isSuccessful) throw Exception("Ошибка поиска: ${resp.code}")

        val doc = Jsoup.parse(resp.body?.string() ?: "")

        val results = doc.select("a[href*='/track/']")
            .map { el ->
                val originalTitle = el.attr("title").ifBlank { el.text().trim() }
                val cleaned = originalTitle.cleanTitle()

                val href = el.attr("href")
                val pageUrl = if (href.startsWith("http")) href else "$baseUrl$href"

                SearchResult(
                    displayTitle = originalTitle,
                    cleanTitle = cleaned,
                    pageUrl = pageUrl
                )
            }
            .distinctBy { it.cleanTitle }

        // ✅ Применяем лимит (0 = без ограничений)
        return@withContext if (limit > 0) results.take(limit) else results
    }

    // 🎵 2. Загрузка деталей конкретного трека
    override suspend fun getTrackDetails(pageUrl: String): TrackInfo = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(pageUrl).headers(headers).build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) throw Exception("Ошибка загрузки трека: ${resp.code}")

        val doc = Jsoup.parse(resp.body?.string() ?: "")

        // Пробуем разные селекторы для получения названия
        var title = doc.selectFirst("h1")?.text()
            ?: doc.selectFirst(".track-title")?.text()
            ?: doc.selectFirst("title")?.text()
            ?: "Unknown Track"

        // ✅ Очищаем название от мусора
        title = title.cleanTitle()

        // ✅ Пытаемся выделить исполнителя из названия (формат "Artist - Title")
        val artist = if (title.contains(" - ")) {
            title.substringBefore(" - ").trim().takeIf { it.length > 2 }
        } else {
            null
        }

        // Ищем MP3 ссылку
        var mp3 =
            doc.select("button[data-url], audio source, [data-mp3], a[href$=.mp3]").first()?.let {
                it.attr("data-url").ifBlank {
                    it.attr("src").ifBlank { it.attr("data-mp3").ifBlank { it.attr("href") } }
                }
            }

        if (mp3.isNullOrBlank()) {
            doc.select("script").forEach { script ->
                if (mp3 != null) return@forEach
                Regex("""['"]?(https?://[^'"\s\\]+\.mp3)""").find(script.data())
                    ?.let { mp3 = it.groupValues[1] }
            }
        }

        mp3 = mp3?.trim()?.removeSurrounding("'")?.removeSurrounding("\"")
            ?.replace(Regex("[/\\\\]+$"), "")

        if (mp3.isNullOrBlank() || !mp3.contains(".mp3")) throw Exception("Не удалось найти MP3")

        val finalUrl = if (!mp3.startsWith("http")) {
            if (mp3.startsWith("//")) "https:$mp3" else "$baseUrl$mp3"
        } else mp3

        // Обложка через iTunes API
        val coverUrl = fetchCoverFromITunes(title)

        // ✅ Создаём TrackInfo с полем artist
        return@withContext TrackInfo(
            title = title,
            url = finalUrl,
            coverUrl = coverUrl,
            isOffline = false,
            artist = artist
        )
    }

    private fun String.cleanTitle(): String {
        return this
            .replace(
                Regex(
                    """\s*(слушать|скачать|бесплатно|онлайн|mp3|песню|песня|текст).*$""",
                    RegexOption.IGNORE_CASE
                ), ""
            )
            .replace(Regex("""^(слушать|скачать|песня|песню)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*Dystopia\s*Music\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(
                Regex(
                    """\s*[\(\[][^)\]]*?(video|lyrics|official|audio)[^)\]]*?[\)\]]""",
                    RegexOption.IGNORE_CASE
                ), ""
            )
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .removeSuffix(".")
            .removeSuffix("-")
            .trim()
    }

    // 🖼️ Вспомогательная функция для обложек
    private fun fetchCoverFromITunes(trackName: String): String? {
        return try {
            val encoded = URLEncoder.encode(trackName, "UTF-8")
            val req = Request.Builder()
                .url("https://itunes.apple.com/search?term=$encoded&media=music&limit=1")
                .get()
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return null
            Regex("\"artworkUrl100\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                ?.replace("100x100bb", "600x600bb")
        } catch (e: Exception) {
            println("⚠️ Не удалось получить обложку для: $trackName")
            null
        }
    }

    suspend fun getTrack(query: String): TrackInfo {
        val results = searchTracks(query)
        if (results.isEmpty()) throw Exception("Треки не найдены")
        return getTrackDetails(results[0].pageUrl)
    }

    // ✅ Поиск всего контента артиста для Pesni.me
    override suspend fun searchArtistContent(artistName: String, limit: Int ): List<SearchItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchItem>()

        try {
            // 1. Треки
            val tracks = searchTracks(artistName, limit = limit / 2)
            results.addAll(tracks.map { SearchItem.TrackResult(it) })

            // 2. Плейлисты (Pesni.me может не иметь отдельного раздела, пробуем общий поиск)
            val encoded = URLEncoder.encode("$artistName плейлист", "UTF-8")
            val searchUrl = "$baseUrl/search/$encoded"

            val request = Request.Builder().url(searchUrl).headers(headers).build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val doc = Jsoup.parse(response.body?.string() ?: "", searchUrl)

                // Ищем ссылки на плейлисты (селекторы примерные)
                val playlistLinks = doc.select("a[href*='/playlist/'], a[href*='/album/']")

                for (link in playlistLinks.take(limit / 2)) {
                    val name = link.attr("title").ifBlank { link.text().trim() }
                    val href = link.attr("abs:href").ifBlank { link.attr("href") }

                    // Пропускаем если это трек, а не плейлист
                    if (href.contains("/track/")) continue

                    if (name.isNotBlank() && href.isNotBlank()) {
                        results.add(
                            SearchItem.PlaylistResult(
                                name = name.cleanTitle(),
                                pageUrl = href,
                                trackCount = "", // Pesni.me не всегда показывает количество
                                coverUrl = null
                            )
                        )
                    }
                }
            }

            return@withContext results.distinctBy {
                when (it) {
                    is SearchItem.TrackResult -> it.track.cleanTitle
                    is SearchItem.PlaylistResult -> it.pageUrl
                }
            }

        } catch (e: Exception) {
            println("⚠️ Pesni artist search error: ${e.message}")
            return@withContext searchTracks(artistName, limit).map { SearchItem.TrackResult(it) }
        }
    }
}