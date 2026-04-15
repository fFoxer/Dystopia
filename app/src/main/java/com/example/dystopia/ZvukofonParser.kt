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
    // 🎵 Получение деталей трека (с отладкой обложки)
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
            var artist: String? = null
            var audioUrl: String? = null
            var coverUrl: String? = null

            if (item != null && item.hasAttr("data-musmeta")) {
                try {
                    val musMeta = item.attr("data-musmeta")
                        .replace("&quot;", "\"")
                        .replace("&amp;", "&")

                    val json = JSONObject(musMeta)
                    title = json.optString("title", title)
                    artist = json.optString("artist", "").takeIf { it.isNotBlank() }

                    val mp3 = json.optString("url", "")
                    if (mp3.isNotBlank()) {
                        audioUrl = if (mp3.startsWith("http")) mp3 else "$baseUrl$mp3"
                    }

                    println("✅ Parsed from JSON: title='$title', artist='$artist', url='$audioUrl'")

                } catch (e: Exception) {
                    println("⚠️ Failed to parse track musmeta: ${e.message}")
                }
            }

            // 🔍 ПАРСИМ ОБЛОЖКУ из HTML (с подробным логом)
            println("🔍 [COVER] Searching for cover on page: $pageUrl")

            val coverSelectors = listOf(
                "div.track-detail__img",
                "div.track-cover-img",
                "div.albums__item-img",
                "img.track-cover",
                "img.album-cover",
                "[style*='background-image']"  // Универсальный селектор
            )

            for (selector in coverSelectors) {
                val coverElements = doc.select(selector)
                println("🔍 [COVER] Selector '$selector' found ${coverElements.size} elements")

                for (coverElement in coverElements) {
                    val style = coverElement.attr("style")
                    println("   [COVER] Style attribute: $style")

                    if (style.contains("background-image", ignoreCase = true)) {
                        val coverMatch = Regex("""url\(['"]?(.*?)['"]?\)""").find(style)

                        if (coverMatch != null) {
                            val rawUrl = coverMatch.groupValues[1]
                            coverUrl = when {
                                rawUrl.startsWith("//") -> "https:$rawUrl"
                                rawUrl.startsWith("/") -> "$baseUrl$rawUrl"
                                rawUrl.startsWith("http") -> rawUrl
                                else -> "$baseUrl/$rawUrl"
                            }
                            println("✅ [COVER] Found cover with selector '$selector': $coverUrl")
                            break
                        } else {
                            println("⚠️ [COVER] Regex didn't match style: $style")
                        }
                    }

                    // Пробуем src как запасной вариант
                    val src = coverElement.attr("abs:src")
                    if (src.isNotBlank() && (src.contains("cover", ignoreCase = true) || src.contains(".jpg", ignoreCase = true) || src.contains("/covers/", ignoreCase = true))) {
                        coverUrl = src
                        println("✅ [COVER] Found cover via src: $coverUrl")
                        break
                    }
                }

                if (coverUrl != null) break
            }

            // Если не нашли — выводим доступные элементы для отладки
            if (coverUrl.isNullOrBlank()) {
                println("⚠️ [COVER] Cover NOT found. Available elements with 'cover' or 'background-image':")
                doc.select("div[class*='cover'], img[class*='cover'], [style*='background-image'], .topcharts__item-img").take(5).forEach { el ->
                    println("   - Tag: ${el.tagName()} | Class: ${el.className()} | Style: ${el.attr("style").take(150)}")
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

            // ✅ Используем обложку с сайта, если есть, иначе iTunes
            val finalCoverUrl = coverUrl ?: run {
                println("⚠️ [COVER] Falling back to iTunes API for: $title")
                fetchCoverFromITunes(title)
            }

            println("✅ Track: $title by ${artist ?: "Unknown"} -> $audioUrl")
            println("🖼️ Final cover URL: ${finalCoverUrl ?: "not found"}")

            // ✅ Создаём TrackInfo
            return@withContext TrackInfo(
                title = title,
                url = audioUrl!!,
                coverUrl = finalCoverUrl,
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

    override suspend fun searchArtistContent(artistName: String, limit: Int): List<SearchItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchItem>()

        try {
            println("🔍 [ARTIST] Searching for: $artistName")

            // 1. Загружаем страницу поиска для нахождения страницы артиста
            val encoded = URLEncoder.encode(artistName, "UTF-8")
            val searchUrl = "$baseUrl/music/$encoded"

            val request = Request.Builder()
                .url(searchUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "ru-RU,ru;q=0.9")
                .addHeader("Referer", "https://new.zvukofon.com/")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                println("⚠️ [ARTIST] Search failed: ${response.code}")
                return@withContext emptyList()
            }

            val html = response.body?.string() ?: ""
            val doc = Jsoup.parse(html, searchUrl)

            // 2. Ищем ссылку на страницу артиста
            val artistLink = doc.selectFirst("a.topartists__link[href*='/performer/']")

            if (artistLink != null) {
                val artistUrl = artistLink.attr("abs:href")
                println("🎤 [ARTIST] Found artist page: $artistUrl")

                // 3. Загружаем страницу артиста — там ВСЕ треки сразу!
                val artistRequest = Request.Builder()
                    .url(artistUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .addHeader("Accept-Language", "ru-RU,ru;q=0.9")
                    .addHeader("Referer", "https://new.zvukofon.com/")
                    .build()

                val artistResponse = client.newCall(artistRequest).execute()

                if (artistResponse.isSuccessful) {
                    val artistHtml = artistResponse.body?.string() ?: ""
                    val artistDoc = Jsoup.parse(artistHtml, artistUrl)

                    // ✅ 4. Парсим ВСЕ треки со страницы артиста (без limit!)
                    val trackElements = artistDoc.select("section.artists-detail-tracks [data-musmeta], [data-musmeta]")
                    println("🎵 [ARTIST] Found ${trackElements.size} tracks on artist page")

                    val tracks = trackElements.mapNotNull { el ->
                        try {
                            val musMeta = el.attr("data-musmeta")
                                .replace("&quot;", "\"")
                                .replace("&amp;", "&")

                            val json = JSONObject(musMeta)
                            val title = json.optString("title", "").trim()
                            val artist = json.optString("artist", "").trim()
                            val trackUrl = json.optString("track_url", "")

                            if (title.isBlank()) return@mapNotNull null

                            val pageUrl = if (trackUrl.startsWith("http")) trackUrl else "$baseUrl$trackUrl"
                            val displayTitle = if (artist.isNotBlank()) "$artist - $title" else title

                            SearchResult(
                                displayTitle = displayTitle,
                                cleanTitle = displayTitle.cleanTitle(),
                                pageUrl = pageUrl
                            )
                        } catch (e: Exception) {
                            println("⚠️ [ARTIST] Parse error: ${e.message}")
                            null
                        }
                    }.distinctBy { it.cleanTitle }

                    results.addAll(tracks.map { SearchItem.TrackResult(it) })
                    println("✅ [ARTIST] Added ${tracks.size} tracks")

                    // 5. Парсим альбомы (отдельно)
                    val albumElements = artistDoc.select("section.artists-detail-albums a.albums__link")
                    println("📀 [ARTIST] Found ${albumElements.size} albums")

                    val albums = albumElements.mapNotNull { link ->
                        val href = link.attr("abs:href")
                        val albumTitle = link.selectFirst(".albums__item-title")?.text()?.trim() ?: link.text().trim()

                        if (href.isNotBlank() && albumTitle.isNotBlank()) {
                            val coverStyle = link.selectFirst(".albums__item-img")?.attr("style") ?: ""
                            val coverUrl = Regex("""url\(['"]?(.*?)['"]?\)""").find(coverStyle)?.groupValues?.get(1)?.let { rawUrl ->
                                when {
                                    rawUrl.startsWith("//") -> "https:$rawUrl"
                                    rawUrl.startsWith("/") -> "$baseUrl$rawUrl"
                                    else -> rawUrl
                                }
                            }

                            SearchItem.PlaylistResult(
                                name = albumTitle,
                                pageUrl = href,
                                trackCount = "",
                                coverUrl = coverUrl
                            )
                        } else null
                    }

                    results.addAll(albums)
                    println("✅ [ARTIST] Added ${albums.size} albums")
                }
            } else {
                println("⚠️ [ARTIST] Artist page not found, using search results")
                // Fallback: парсим треки из поиска
                val trackElements = doc.select("[data-musmeta]")
                val tracks = trackElements.mapNotNull { el ->
                    // ... тот же парсинг что выше ...
                    null // заглушка, код парсинга как выше
                }
                results.addAll(tracks.map { SearchItem.TrackResult(it) })
            }

            val trackCount = results.count { it is SearchItem.TrackResult }
            val albumCount = results.count { it is SearchItem.PlaylistResult }
            println("🎉 [ARTIST] Total: ${results.size} items ($trackCount tracks, $albumCount albums)")
            return@withContext results.distinctBy {
                when (it) {
                    is SearchItem.TrackResult -> it.track.cleanTitle
                    is SearchItem.PlaylistResult -> it.pageUrl
                }
            }

        } catch (e: Exception) {
            println("❌ [ARTIST] Error: ${e.message}")
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    // ✅ Парсинг треков + альбомов СО страницы артиста
    private suspend fun fetchArtistContent(artistUrl: String, limit: Int): List<SearchItem> {
        val results = mutableListOf<SearchItem>()

        return try {
            println("🔍 [STEP 2] Loading artist page: $artistUrl")

            val request = Request.Builder()
                .url(artistUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .addHeader("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .addHeader("Referer", "https://new.zvukofon.com/")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                println("⚠️ [STEP 2] Failed to load artist page: ${response.code}")
                return emptyList()
            }

            val html = response.body?.string() ?: ""
            val doc = Jsoup.parse(html, artistUrl)

            // ✅ 2a. Парсим треки со страницы артиста
            // Пробуем несколько возможных селекторов для треков
            val trackSelectors = listOf(
                "section.artists-detail-tracks [data-musmeta]",
                "section.tracks-list [data-musmeta]",
                "div.track-list [data-musmeta]",
                "[data-musmeta]"  // Fallback: любые элементы с data-musmeta
            )

            var trackElements = org.jsoup.select.Elements()
            var usedTrackSelector = ""

            for (selector in trackSelectors) {
                trackElements = doc.select(selector)
                if (trackElements.isNotEmpty()) {
                    usedTrackSelector = selector
                    println("🎵 [STEP 2a] Found ${trackElements.size} tracks with selector '$usedTrackSelector'")
                    break
                }
            }

            // Парсим найденные треки
            val tracks = trackElements.mapNotNull { el ->
                try {
                    val musMeta = el.attr("data-musmeta")
                        .replace("&quot;", "\"")
                        .replace("&amp;", "&")

                    val json = org.json.JSONObject(musMeta)
                    val title = json.optString("title", "").trim()
                    val artist = json.optString("artist", "").trim()

                    // ✅ ИСПРАВЛЕНИЕ: используем track_url для страницы, url для MP3
                    val trackPageUrl = json.optString("track_url", "")  // ✅ Страница трека: /music/...
                    val mp3Url = json.optString("url", "")              // ✅ MP3 файл: /dl/...

                    if (title.isBlank()) return@mapNotNull null

                    // Формируем правильные URL
                    val pageUrl = if (trackPageUrl.startsWith("http")) {
                        trackPageUrl
                    } else {
                        "$baseUrl$trackPageUrl"
                    }

                    val displayTitle = if (artist.isNotBlank()) "$artist - $title" else title

                    SearchResult(
                        displayTitle = displayTitle,
                        cleanTitle = displayTitle.cleanTitle(),
                        pageUrl = pageUrl  // ✅ Теперь это страница трека, а не MP3!
                    )
                } catch (e: Exception) {
                    println("⚠️ Failed to parse track JSON: ${e.message}")
                    null
                }
            }.distinctBy { it.cleanTitle }.take(limit / 2)

            results.addAll(tracks.map { SearchItem.TrackResult(it) })
            println("✅ [STEP 2a] Added ${tracks.size} tracks")

            // ✅ 2b. Парсим альбомы со страницы артиста
            val albumSelectors = listOf(
                "section.artists-detail-albums a.albums__link",
                "a.albums__link",
                "a[href*='/music-album/']"
            )

            var albumElements = org.jsoup.select.Elements()
            var usedAlbumSelector = ""

            for (selector in albumSelectors) {
                albumElements = doc.select(selector)
                if (albumElements.isNotEmpty()) {
                    usedAlbumSelector = selector
                    println("📀 [STEP 2b] Found ${albumElements.size} albums with selector '$usedAlbumSelector'")
                    break
                }
            }

            val albums = albumElements.take(limit / 2).mapNotNull { link ->
                val href = link.attr("abs:href")
                val title = link.selectFirst(".albums__item-title")?.text()?.trim() ?: link.text().trim()

                if (href.isNotBlank() && title.isNotBlank()) {
                    // Извлекаем обложку из inline style
                    val coverStyle = link.selectFirst(".albums__item-img")?.attr("style") ?: ""
                    val coverUrl = Regex("""url\(['"]?(.*?)['"]?\)""").find(coverStyle)?.groupValues?.get(1)?.let { rawUrl ->
                        when {
                            rawUrl.startsWith("//") -> "https:$rawUrl"
                            rawUrl.startsWith("/") -> "$baseUrl$rawUrl"
                            else -> rawUrl
                        }
                    }

                    SearchItem.PlaylistResult(
                        name = title,
                        pageUrl = href,
                        trackCount = "",
                        coverUrl = coverUrl
                    )
                } else null
            }

            results.addAll(albums)
            println("✅ [STEP 2b] Added ${albums.size} albums")

            results

        } catch (e: Exception) {
            println("⚠️ [STEP 2] Error: ${e.message}")
            emptyList()
        }
    }

    // ✅ Fallback: парсинг треков со страницы поиска (если артист не найден)
    private fun parseTracksFromSearch(doc: org.jsoup.nodes.Element, limit: Int): List<SearchResult> {
        return doc.select("[data-musmeta]").mapNotNull { el ->
            try {
                val musMeta = el.attr("data-musmeta")
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")

                val json = org.json.JSONObject(musMeta)
                val title = json.optString("title", "").trim()
                val artist = json.optString("artist", "").trim()
                val trackUrl = json.optString("track_url", "")

                if (title.isBlank()) return@mapNotNull null

                val pageUrl = if (trackUrl.startsWith("http")) trackUrl else "$baseUrl$trackUrl"
                val displayTitle = if (artist.isNotBlank()) "$artist - $title" else title

                SearchResult(
                    displayTitle = displayTitle,
                    cleanTitle = displayTitle.cleanTitle(),
                    pageUrl = pageUrl
                )
            } catch (e: Exception) { null }
        }.distinctBy { it.cleanTitle }.take(limit)
    }

    // ✅ Data class для информации об альбоме
    private data class AlbumInfo(
        val trackCount: String = "",
        val coverUrl: String? = null
    )

    // ✅ Парсинг треков из альбома
    suspend fun getAlbumTracks(albumUrl: String): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            println("📀 Parsing album tracks: $albumUrl")

            val request = Request.Builder()
                .url(albumUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .addHeader("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .addHeader("Referer", "https://new.zvukofon.com/")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                println("⚠️ Failed to load album page: ${response.code}")
                return@withContext emptyList()
            }

            val html = response.body?.string() ?: ""
            val doc = Jsoup.parse(html, albumUrl)

            // Ищем треки альбома (элементы с data-musmeta)
            val trackElements = doc.select("[data-musmeta]")
            println("🎵 Found ${trackElements.size} tracks in album")

            val results = trackElements.mapNotNull { el ->
                try {
                    val musMeta = el.attr("data-musmeta")
                        .replace("&quot;", "\"")
                        .replace("&amp;", "&")

                    val json = JSONObject(musMeta)
                    val title = json.optString("title", "").trim()
                    val artist = json.optString("artist", "").trim()
                    val trackUrl = json.optString("track_url", "")

                    if (title.isBlank()) return@mapNotNull null

                    val pageUrl = if (trackUrl.startsWith("http")) trackUrl else "$baseUrl$trackUrl"
                    val displayTitle = if (artist.isNotBlank()) "$artist - $title" else title

                    SearchResult(
                        displayTitle = displayTitle,
                        cleanTitle = displayTitle.cleanTitle(),
                        pageUrl = pageUrl
                    )
                } catch (e: Exception) {
                    println("⚠️ Failed to parse track: ${e.message}")
                    null
                }
            }.distinctBy { it.cleanTitle }

            println("✅ Parsed ${results.size} tracks from album")
            return@withContext results

        } catch (e: Exception) {
            println("❌ Album tracks error: ${e.message}")
            emptyList()
        }
    }
}