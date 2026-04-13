package com.example.dystopia

import com.example.dystopia.data.SearchItem
import com.example.dystopia.data.SearchResult
import com.example.dystopia.data.TrackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ZvukofonParser : MusicParser {
    override val name: String = "Zvukofon.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val baseUrl = "https://new.zvukofon.com"

    // 🔍 Поиск треков (с параметром limit)
    override suspend fun searchTracks(query: String, limit: Int): List<SearchResult>   = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "$baseUrl/music/$encoded"

            println("🔍 Searching Zvukofon: $searchUrl (limit: $limit)")

            val request = Request.Builder()
                .url(searchUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .addHeader("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .addHeader("Referer", "https://new.zvukofon.com/")
                .build()

            val response = client.newCall(request).execute()

            println("📡 Response code: ${response.code}")

            if (!response.isSuccessful) {
                println("❌ Zvukofon search failed: ${response.code}")
                return@withContext emptyList()
            }

            // ✅ Передаём limit в парсер
            return@withContext parseSearchResults(response, baseUrl, limit)

        } catch (e: Exception) {
            println("❌ Zvukofon search error: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    // ✅ Функция парсинга результатов (с параметром limit)
    private fun parseSearchResults(response: okhttp3.Response, baseUrl: String, limit: Int = 20): List<SearchResult> {
        try {
            val html = response.body?.string() ?: return emptyList()
            val doc = Jsoup.parse(html, baseUrl)
            val results = mutableListOf<SearchResult>()

            val items = doc.select("[data-musmeta]")
            println("📦 Found ${items.size} items with data-musmeta")

            for (item in items) {
                val musMeta = item.attr("data-musmeta")
                if (musMeta.isBlank()) continue

                try {
                    val decodedJson = musMeta
                        .replace("&quot;", "\"")
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")

                    val json = JSONObject(decodedJson)

                    val artist = json.optString("artist", "").trim()
                    val title = json.optString("title", "").trim()
                    val trackUrl = json.optString("track_url", "")

                    if (title.isBlank()) continue

                    val pageUrl = if (trackUrl.startsWith("http")) {
                        trackUrl
                    } else {
                        "$baseUrl$trackUrl"
                    }

                    val displayTitle = if (artist.isNotBlank()) "$artist - $title" else title
                    val cleanTitle = displayTitle.cleanTitle()

                    println("✅ Found: $displayTitle")

                    results.add(
                        SearchResult(
                            displayTitle = displayTitle,
                            cleanTitle = cleanTitle,
                            pageUrl = pageUrl
                        )
                    )

                    // ✅ Используем параметр limit (0 = без ограничений)
                    if (limit > 0 && results.size >= limit) {
                        println("⏹️ Limit reached: $limit")
                        break
                    }

                } catch (e: Exception) {
                    println("⚠️ Failed to parse item: ${e.message}")
                    continue
                }
            }

            println("🎵 Final results: ${results.size}")
            return results.distinctBy { it.cleanTitle }

        } catch (e: Exception) {
            println("❌ Parse error: ${e.message}")
            return emptyList()
        }
    }

    // 🎵 Получение деталей трека
    override suspend fun getTrackDetails(pageUrl: String): TrackInfo = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(pageUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "ru-RU,ru;q=0.9")
                .addHeader("Referer", "https://new.zvukofon.com/")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                throw Exception("Ошибка загрузки трека: ${response.code}")
            }

            val html = response.body?.string() ?: throw Exception("Empty response")
            val doc = Jsoup.parse(html, pageUrl)

            // 🎯 Ищем data-musmeta
            val item = doc.selectFirst("[data-musmeta]")
            var title = "Unknown Track"
            var audioUrl: String? = null
            var artist: String? = null

            if (item != null && item.hasAttr("data-musmeta")) {
                try {
                    val musMeta = item.attr("data-musmeta")
                        .replace("&quot;", "\"")
                        .replace("&amp;", "&")

                    val json = JSONObject(musMeta)
                    title = json.optString("title", title)

                    // ✅ Извлекаем artist
                    artist = json.optString("artist", "").takeIf { it.isNotBlank() }

                    val mp3 = json.optString("url", "")
                    if (mp3.isNotBlank()) {
                        audioUrl = if (mp3.startsWith("http")) mp3 else "$baseUrl$mp3"
                    }
                } catch (e: Exception) {
                    println("⚠️ Failed to parse track musmeta: ${e.message}")
                }
            }

            // Fallback для title
            if (title == "Unknown Track") {
                title = doc.selectFirst("h1, .track-title, meta[property='og:title']")
                    ?.text()?.trim() ?: "Unknown Track"
            }

            // Fallback для audioUrl
            if (audioUrl.isNullOrBlank()) {
                audioUrl = doc.selectFirst("a[href*='.mp3'], button[data-url]")
                    ?.attr("abs:href")
            }

            // Ищем в скриптах
            if (audioUrl.isNullOrBlank()) {
                val scripts = doc.select("script")
                for (script in scripts) {
                    val data = script.data()
                    val mp3Match = Regex("""['"]?(https?://[^'"\s\\]+\.mp3[^'"\s\\]*)['"]?""").find(data)
                    if (mp3Match != null) {
                        audioUrl = mp3Match.groupValues[1]
                        break
                    }
                }
            }

            // Приводим к абсолютному URL
            if (!audioUrl.isNullOrBlank() && !audioUrl.startsWith("http")) {
                audioUrl = when {
                    audioUrl.startsWith("//") -> "https:$audioUrl"
                    audioUrl.startsWith("/") -> "$baseUrl$audioUrl"
                    else -> audioUrl
                }
            }

            if (audioUrl.isNullOrBlank() || !audioUrl.contains(".mp3", ignoreCase = true)) {
                println("⚠️ MP3 URL not found")
                audioUrl = pageUrl
            }

            title = title.cleanTitle()
            val coverUrl = fetchCoverFromITunes(title)

            println("✅ Track: $title by ${artist ?: "Unknown"} -> $audioUrl")

            // ✅ Создаём TrackInfo с именованными параметрами
            return@withContext TrackInfo(
                title = title,
                url = audioUrl!!,
                coverUrl = coverUrl,
                isOffline = false,
                artist = artist
            )

        } catch (e: Exception) {
            println("❌ Zvukofon details error: ${e.message}")
            throw e
        }
    }

    private fun String.cleanTitle(): String {
        return this
            .replace(Regex("""\s*(слушать|скачать|бесплатно|онлайн|mp3|песню|песня|текст).*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^(слушать|скачать|песня|песню)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*Dystopia\s*Music\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*[\(\[][^)\]]*?(video|lyrics|official|audio)[^)\]]*?[\)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .removeSuffix(".")
            .removeSuffix("-")
            .trim()
    }

    private fun fetchCoverFromITunes(trackName: String): String? {
        return try {
            val encoded = URLEncoder.encode(trackName, "UTF-8")
            val request = Request.Builder()
                .url("https://itunes.apple.com/search?term=$encoded&media=music&limit=1")
                .get()
                .build()
            val response = OkHttpClient().newCall(request).execute()
            val body = response.body?.string() ?: return null
            Regex("\"artworkUrl100\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                ?.replace("100x100bb", "600x600bb")
        } catch (e: Exception) {
            println("⚠️ Не удалось получить обложку: $trackName")
            null
        }
    }
    // ✅ Поиск всего контента артиста (треки + плейлисты)
    override suspend fun searchArtistContent(artistName: String, limit: Int): List<SearchItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchItem>()

        try {
            // 1. Ищем треки (используем базовый поиск)
            val tracks = searchTracks(artistName, limit = limit / 2)
            results.addAll(tracks.map { SearchItem.TrackResult(it) })

            // 2. Ищем плейлисты артиста (специальный запрос)
            val encoded = URLEncoder.encode(artistName, "UTF-8")
            val playlistUrl = "$baseUrl/playlists?q=$encoded"

            val request = Request.Builder()
                .url(playlistUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "ru-RU,ru;q=0.9")
                .addHeader("Referer", baseUrl)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val doc = Jsoup.parse(response.body?.string() ?: "", playlistUrl)

                // Ищем карточки плейлистов (селекторы могут отличаться)
                val playlistCards = doc.select("a[href*='/playlist/'], .playlist-card, [data-playlist]")

                for (card in playlistCards.take(limit / 2)) {
                    val name = card.selectFirst(".playlist-title, .title, h3")?.text()?.trim() ?: continue
                    val href = card.attr("abs:href").ifBlank { card.attr("href") }
                    val trackCount = card.selectFirst(".track-count, .meta")?.text()?.trim() ?: ""
                    val cover = card.selectFirst("img")?.attr("abs:src")

                    if (href.isNotBlank() && name.isNotBlank()) {
                        results.add(
                            SearchItem.PlaylistResult(
                                name = name,
                                pageUrl = href,
                                trackCount = trackCount,
                                coverUrl = cover
                            )
                        )
                    }

                    if (results.size >= limit) break
                }
            }

            println("🎵 Artist search: ${results.size} items found for $artistName")
            return@withContext results.distinctBy {
                when (it) {
                    is SearchItem.TrackResult -> it.track.cleanTitle
                    is SearchItem.PlaylistResult -> it.pageUrl
                }
            }

        } catch (e: Exception) {
            println("⚠️ Artist search error: ${e.message}")
            // Fallback: возвращаем только треки если плейлисты не нашли
            return@withContext searchTracks(artistName, limit).map { SearchItem.TrackResult(it) }
        }
    }
}