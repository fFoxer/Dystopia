package com.example.dystopia

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

    // 🔍 Поиск треков (ПРАВИЛЬНЫЙ URL)
    override suspend fun searchTracks(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")

            // ✅ ПРАВИЛЬНЫЙ ФОРМАТ: /music/{query}
            val searchUrl = "$baseUrl/music/$encoded"

            println("🔍 Searching Zvukofon: $searchUrl")

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

            return@withContext parseSearchResults(response, baseUrl)

        } catch (e: Exception) {
            println("❌ Zvukofon search error: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    // ✅ Функция парсинга результатов
    private fun parseSearchResults(response: okhttp3.Response, baseUrl: String): List<SearchResult> {
        try {
            val html = response.body?.string() ?: return emptyList()
            val doc = Jsoup.parse(html, baseUrl)
            val results = mutableListOf<SearchResult>()

            // ✅ Ищем элементы с data-musmeta
            val items = doc.select("[data-musmeta]")

            println("📦 Found ${items.size} items with data-musmeta")

            for (item in items) {
                val musMeta = item.attr("data-musmeta")
                if (musMeta.isBlank()) continue

                try {
                    // Декодируем HTML-entities
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

                    // Формируем pageUrl
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

                    if (results.size >= 20) break

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

            if (item != null && item.hasAttr("data-musmeta")) {
                try {
                    val musMeta = item.attr("data-musmeta")
                        .replace("&quot;", "\"")
                        .replace("&amp;", "&")

                    val json = JSONObject(musMeta)
                    title = json.optString("title", title)
                    val mp3 = json.optString("url", "")
                    if (mp3.isNotBlank()) {
                        audioUrl = if (mp3.startsWith("http")) mp3 else "$baseUrl$mp3"
                    }
                } catch (e: Exception) {
                    println("⚠️ Failed to parse track musmeta: ${e.message}")
                }
            }

            // Fallback
            if (title == "Unknown Track") {
                title = doc.selectFirst("h1, .track-title, meta[property='og:title']")
                    ?.text()?.trim() ?: "Unknown Track"
            }

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

            println("✅ Track: $title -> $audioUrl")

            TrackInfo(
                title = title,
                url = audioUrl!!,
                coverUrl = coverUrl,
                isOffline = false
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
}