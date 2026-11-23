package se.ulf.tv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object IptvRepository {

    private val countryMap = mapOf(
        "SE" to "Sverige", "SWE" to "Sverige", "SWEDEN" to "Sverige", "SVERIGE" to "Sverige",
        "NO" to "Norge", "NOR" to "Norge", "NORWAY" to "Norge", "NORGE" to "Norge",
        "DK" to "Danmark", "DEN" to "Danmark", "DENMARK" to "Danmark", "DANMARK" to "Danmark",
        "FI" to "Finland", "FIN" to "Finland", "FINLAND" to "Finland",
        "UK" to "Storbritannien", "GB" to "Storbritannien", "ENGLAND" to "Storbritannien",
        "US" to "USA", "USA" to "USA",
        "DE" to "Tyskland", "GER" to "Tyskland", "GERMANY" to "Tyskland",
        "FR" to "Frankrike", "FRA" to "Frankrike", "FRANCE" to "Frankrike",
        "ES" to "Spanien", "ESP" to "Spanien", "SPAIN" to "Spanien",
        "IT" to "Italien", "ITA" to "Italien", "ITALY" to "Italien",
        "TR" to "Turkiet", "TUR" to "Turkiet", "TURKEY" to "Turkiet",
        "AR" to "Arabiska", "ARB" to "Arabiska", "ARABIC" to "Arabiska",
        "NL" to "Nederländerna", "NLD" to "Nederländerna",
        "PL" to "Polen", "POL" to "Polen",
        "PT" to "Portugal", "BR" to "Brasilien",
        "GR" to "Grekland", "EX-YU" to "Balkan", "EXYU" to "Balkan"
    )

    fun buildClient(userAgent: String): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", userAgent.ifBlank { "IPTVSmartersPro" })
                    .header("Accept", "*/*")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    suspend fun loadAccountCatalog(
        account: AccountProfile,
        settings: AppSettings,
        onStatus: (String) -> Unit
    ): CatalogData = withContext(Dispatchers.IO) {
        val client = buildClient(settings.userAgent)
        if (account.mode == "XTREAM" && account.server.isNotBlank()) {
            loadXtreamCatalog(client, account, settings, onStatus)
        } else if (account.m3uUrl.isNotBlank()) {
            loadM3uCatalog(client, account.m3uUrl, settings, onStatus)
        } else {
            demoCatalog()
        }
    }

    private suspend fun loadXtreamCatalog(
        client: OkHttpClient,
        acc: AccountProfile,
        settings: AppSettings,
        onStatus: (String) -> Unit
    ): CatalogData = withContext(Dispatchers.IO) {
        val base = acc.server.trim().trimEnd('/')
        val u = acc.username.trim()
        val p = acc.password.trim()
        val api = "$base/player_api.php?username=$u&password=$p"

        onStatus("Hämtar kategorier...")
        val liveCats = async { fetchCategoryMap(client, "$api&action=get_live_categories") }
        val vodCats = async { fetchCategoryMap(client, "$api&action=get_vod_categories") }
        val seriesCats = async { fetchCategoryMap(client, "$api&action=get_series_categories") }

        val liveCatMap = liveCats.await()
        val vodCatMap = vodCats.await()
        val seriesCatMap = seriesCats.await()

        onStatus("Laddar Live-kanaler, Filmer & Serier...")
        val liveDeferred = async {
            fetchXtreamLive(client, "$api&action=get_live_streams", base, u, p, liveCatMap, settings.hideAdult)
        }
        val vodDeferred = async {
            fetchXtreamVod(client, "$api&action=get_vod_streams", base, u, p, vodCatMap, settings.hideAdult)
        }
        val seriesDeferred = async {
            fetchXtreamSeries(client, "$api&action=get_series", seriesCatMap, settings.hideAdult)
        }

        CatalogData(
            liveChannels = liveDeferred.await(),
            movies = vodDeferred.await(),
            series = seriesDeferred.await()
        )
    }

    private fun fetchCategoryMap(client: OkHttpClient, url: String): Map<String, String> {
        return try {
            val body = executeGet(client, url) ?: return emptyMap()
            val arr = JSONArray(body)
            val map = HashMap<String, String>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("category_id")
                val name = obj.optString("category_name")
                if (id.isNotBlank() && name.isNotBlank()) {
                    map[id] = name
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun fetchXtreamLive(
        client: OkHttpClient,
        url: String,
        base: String,
        user: String,
        pass: String,
        catMap: Map<String, String>,
        hideAdult: Boolean
    ): List<MediaItem> {
        val body = executeGet(client, url) ?: return emptyList()
        return try {
            val arr = JSONArray(body)
            val list = ArrayList<MediaItem>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val streamId = o.optInt("stream_id", -1).takeIf { it > 0 }?.toString()
                    ?: o.optString("stream_id")
                if (streamId.isBlank()) continue
                val name = o.optString("name").trim()
                if (name.isBlank()) continue
                val catId = o.optString("category_id")
                val catName = catMap[catId] ?: "Live TV"
                if (hideAdult && isAdultContent(name, catName)) continue

                val (country, subCat) = parseCountryAndSubCategory(catName, name)
                val icon = o.optString("stream_icon").trim()
                val streamUrl = "$base/live/$user/$pass/$streamId.ts"
                list.add(
                    MediaItem(
                        id = "live_$streamId",
                        title = name,
                        category = catName,
                        country = country,
                        subCategory = subCat,
                        type = ContentType.LIVE,
                        streamUrl = streamUrl,
                        posterUrl = icon,
                        backdropUrl = icon,
                        qualityBadge = detectQualityFromTitle(name)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchXtreamVod(
        client: OkHttpClient,
        url: String,
        base: String,
        user: String,
        pass: String,
        catMap: Map<String, String>,
        hideAdult: Boolean
    ): List<MediaItem> {
        val body = executeGet(client, url) ?: return emptyList()
        return try {
            val arr = JSONArray(body)
            val list = ArrayList<MediaItem>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val streamId = o.optInt("stream_id", -1).takeIf { it > 0 }?.toString()
                    ?: o.optString("stream_id")
                if (streamId.isBlank()) continue
                val name = o.optString("name").trim()
                if (name.isBlank()) continue
                val catId = o.optString("category_id")
                val catName = catMap[catId] ?: "Filmer"
                if (hideAdult && isAdultContent(name, catName)) continue

                val ext = o.optString("container_extension", "mp4").ifBlank { "mp4" }
                val poster = o.optString("stream_icon").trim()
                val rating = o.optString("rating").trim()
                val added = o.optString("added").toLongOrNull() ?: 0L
                val year = extractYear(name)
                val genre = normalizeVodGenre(catName, name)
                val streamUrl = "$base/movie/$user/$pass/$streamId.$ext"

                list.add(
                    MediaItem(
                        id = "vod_$streamId",
                        title = name,
                        category = catName,
                        country = parseCountryAndSubCategory(catName, name).first,
                        subCategory = genre,
                        type = ContentType.MOVIE,
                        streamUrl = streamUrl,
                        posterUrl = poster,
                        backdropUrl = poster,
                        rating = rating,
                        year = year,
                        addedTimestamp = added,
                        extension = ext,
                        qualityBadge = detectQualityFromTitle(name)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchXtreamSeries(
        client: OkHttpClient,
        url: String,
        catMap: Map<String, String>,
        hideAdult: Boolean
    ): List<MediaItem> {
        val body = executeGet(client, url) ?: return emptyList()
        return try {
            val arr = JSONArray(body)
            val list = ArrayList<MediaItem>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val seriesId = o.optInt("series_id", -1).takeIf { it > 0 }?.toString()
                    ?: o.optString("series_id")
                if (seriesId.isBlank()) continue
                val name = o.optString("name").trim()
                if (name.isBlank()) continue
                val catId = o.optString("category_id")
                val catName = catMap[catId] ?: "Serier"
                if (hideAdult && isAdultContent(name, catName)) continue

                val cover = o.optString("cover").trim()
                val backdropArr = o.optJSONArray("backdrop_path")
                val backdrop = if (backdropArr != null && backdropArr.length() > 0) {
                    backdropArr.optString(0).trim()
                } else cover
                val relDate = o.optString("releaseDate")
                val year = relDate.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) } ?: extractYear(name)
                val added = o.optString("last_modified").toLongOrNull() ?: 0L
                val genre = normalizeVodGenre(catName, o.optString("genre").ifBlank { name })

                list.add(
                    MediaItem(
                        id = "series_$seriesId",
                        title = name,
                        category = catName,
                        country = parseCountryAndSubCategory(catName, name).first,
                        subCategory = genre,
                        type = ContentType.SERIES,
                        streamUrl = "",
                        posterUrl = cover,
                        backdropUrl = backdrop.ifBlank { cover },
                        rating = o.optString("rating").trim(),
                        year = year,
                        addedTimestamp = added,
                        plot = o.optString("plot").trim(),
                        seriesId = seriesId,
                        qualityBadge = detectQualityFromTitle(name)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchSeriesEpisodes(
        account: AccountProfile,
        seriesId: String,
        userAgent: String
    ): Map<Int, List<EpisodeItem>> = withContext(Dispatchers.IO) {
        if (account.mode != "XTREAM" || seriesId.isBlank()) return@withContext emptyMap()
        val client = buildClient(userAgent)
        val base = account.server.trim().trimEnd('/')
        val u = account.username.trim()
        val p = account.password.trim()
        val url = "$base/player_api.php?username=$u&password=$p&action=get_series_info&series_id=$seriesId"
        val body = executeGet(client, url) ?: return@withContext emptyMap()
        try {
            val root = JSONObject(body)
            val episodesObj = root.optJSONObject("episodes") ?: return@withContext emptyMap()
            val result = sortedMapOf<Int, List<EpisodeItem>>()
            val keys = episodesObj.keys()
            while (keys.hasNext()) {
                val seasonKey = keys.next()
                val seasonNum = seasonKey.toIntOrNull() ?: 1
                val arr = episodesObj.optJSONArray(seasonKey) ?: continue
                val epList = ArrayList<EpisodeItem>(arr.length())
                for (i in 0 until arr.length()) {
                    val ep = arr.optJSONObject(i) ?: continue
                    val epId = ep.optString("id")
                    if (epId.isBlank()) continue
                    val ext = ep.optString("container_extension", "mp4").ifBlank { "mp4" }
                    val epNum = ep.optInt("episode_num", i + 1)
                    val epTitle = ep.optString("title").ifBlank { "Avsnitt $epNum" }
                    val info = ep.optJSONObject("info")
                    val streamUrl = "$base/series/$u/$p/$epId.$ext"
                    epList.add(
                        EpisodeItem(
                            id = epId,
                            season = seasonNum,
                            episodeNum = epNum,
                            title = epTitle,
                            streamUrl = streamUrl,
                            duration = info?.optString("duration").orEmpty(),
                            plot = info?.optString("plot").orEmpty(),
                            thumbUrl = info?.optString("movie_image").orEmpty()
                        )
                    )
                }
                if (epList.isNotEmpty()) {
                    result[seasonNum] = epList
                }
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private suspend fun loadM3uCatalog(
        client: OkHttpClient,
        m3uUrl: String,
        settings: AppSettings,
        onStatus: (String) -> Unit
    ): CatalogData = withContext(Dispatchers.IO) {
        onStatus("Laddar ner M3U-spellista...")
        val req = Request.Builder().url(m3uUrl.trim()).build()
        val live = ArrayList<MediaItem>()
        val movies = ArrayList<MediaItem>()
        val series = ArrayList<MediaItem>()

        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext CatalogData()
                val stream = resp.body?.byteStream() ?: return@withContext CatalogData()
                val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8), 65536)

                var pendingTitle = ""
                var pendingGroup = "Allmänt"
                var pendingLogo = ""
                var index = 0

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line!!.trim()
                    if (trimmed.isEmpty()) continue
                    if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                        pendingTitle = trimmed.substringAfterLast(",").trim()
                        pendingGroup = extractAttr(trimmed, "group-title").ifBlank { "Allmänt" }
                        pendingLogo = extractAttr(trimmed, "tvg-logo")
                    } else if (!trimmed.startsWith("#")) {
                        val url = trimmed
                        val title = pendingTitle.ifBlank { "Kanal ${index + 1}" }
                        val group = pendingGroup
                        if (!settings.hideAdult || !isAdultContent(title, group)) {
                            val lowerUrl = url.lowercase()
                            val lowerGroup = group.lowercase()
                            val isSeries = lowerUrl.contains("/series/") ||
                                lowerGroup.contains("serie") ||
                                Regex("""S\d{1,2}\s*E\d{1,2}""", RegexOption.IGNORE_CASE).containsMatchIn(title)
                            val isMovie = !isSeries && (
                                lowerUrl.contains("/movie/") ||
                                lowerGroup.contains("film") ||
                                lowerGroup.contains("vod") ||
                                lowerGroup.contains("movie") ||
                                lowerUrl.endsWith(".mp4") ||
                                lowerUrl.endsWith(".mkv") ||
                                lowerUrl.endsWith(".avi")
                            )

                            val type = when {
                                isSeries -> ContentType.SERIES
                                isMovie -> ContentType.MOVIE
                                else -> ContentType.LIVE
                            }

                            val (country, subCat) = parseCountryAndSubCategory(group, title)
                            val item = MediaItem(
                                id = "m3u_$index",
                                title = title,
                                category = group,
                                country = country,
                                subCategory = if (type == ContentType.LIVE) subCat else normalizeVodGenre(group, title),
                                type = type,
                                streamUrl = url,
                                posterUrl = pendingLogo,
                                backdropUrl = pendingLogo,
                                year = extractYear(title),
                                addedTimestamp = index.toLong(),
                                qualityBadge = detectQualityFromTitle(title)
                            )

                            when (type) {
                                ContentType.LIVE -> live.add(item)
                                ContentType.MOVIE -> movies.add(item)
                                ContentType.SERIES -> series.add(item)
                            }
                            index++
                        }
                        pendingTitle = ""
                        pendingLogo = ""
                    }
                }
            }
        } catch (_: Exception) {
        }

        CatalogData(liveChannels = live, movies = movies, series = series)
    }

    fun parseCountryAndSubCategory(category: String, title: String): Pair<String, String> {
        val raw = "$category$title".trim()
        val upperCat = category.uppercase().trim()
        val upperTitle = title.uppercase().trim()

        var detectedCountry = "Övriga"
        val prefixMatch = Regex("""^\[?([A-Z]{2,5})\]?\s*[\|:\-/\]]""").find(upperCat)
            ?: Regex("""^\[?([A-Z]{2,5})\]?\s*[\|:\-/\]]""").find(upperTitle)

        if (prefixMatch != null) {
    val code = prefixMatch.groupValues[1]
    detectedCountry = countryMap[code] ?: code
} else {
    for ((key, label) in countryMap) {
        if (upperCat.startsWith("$key ") || upperCat.contains(" $key ") || upperCat.contains(label.uppercase())) {
            detectedCountry = label
            break
        }
    }
}

        val lower = raw.lowercase()
        val sub = when {
            lower.contains("sport") || lower.contains("fotboll") || lower.contains("football") ||
                lower.contains("hockey") || lower.contains("viaplay") || lower.contains("bein") ||
                lower.contains("sky sports") || lower.contains("eurosport") || lower.contains("f1") -> "Sport"
            lower.contains("film") || lower.contains("movie") || lower.contains("cinema") ||
                lower.contains("hbo") || lower.contains("c more") || lower.contains("sf ") -> "Film & Serier"
            lower.contains("nyhet") || lower.contains("news") || lower.contains("cnn") ||
                lower.contains("bbc world") || lower.contains("aljazeera") || lower.contains("haber") -> "Nyheter"
            lower.contains("barn") || lower.contains("kids") || lower.contains("disney") ||
                lower.contains("cartoon") || lower.contains("nick") || lower.contains("cocuk") -> "Barn"
            lower.contains("dok") || lower.contains("doc") || lower.contains("discovery") ||
                lower.contains("nat geo") || lower.contains("animal") || lower.contains("history") -> "Dokumentär"
            lower.contains("musik") || lower.contains("music") || lower.contains("mtv") -> "Musik"
            else -> "Allmänt"
        }

        return detectedCountry to sub
    }

    private fun normalizeVodGenre(category: String, text: String): String {
        val l = "$category$text".lowercase()
        return when {
            l.contains("action") -> "Action"
            l.contains("komedi") || l.contains("comedy") -> "Komedi"
            l.contains("drama") -> "Drama"
            l.contains("thriller") || l.contains("kriminal") || l.contains("crime") -> "Thriller & Kriminal"
            l.contains("sci-fi") || l.contains("science") || l.contains("fantasy") -> "Sci-Fi & Fantasy"
            l.contains("skräck") || l.contains("horror") -> "Skräck"
            l.contains("barn") || l.contains("familj") || l.contains("family") || l.contains("anim") -> "Barn & Familj"
            l.contains("dok") || l.contains("doc") -> "Dokumentär"
            l.contains("romantik") || l.contains("romance") -> "Romantik"
            else -> category.substringAfter("|").substringAfter(":").trim().ifBlank { "Övrigt" }
        }
    }

    private fun extractYear(text: String): String {
        val m = Regex("""\b(19\d{2}|20[0-2]\d)\b""").find(text)
        return m?.groupValues?.get(1).orEmpty()
    }

    private fun detectQualityFromTitle(title: String): String {
        val u = title.uppercase()
        return when {
            u.contains("4K") || u.contains("UHD") || u.contains("2160") -> "4K"
            u.contains("FHD") || u.contains("1080") -> "1080p"
            u.contains("HD") || u.contains("720") -> "720p"
            u.contains("SD") -> "SD"
            else -> ""
        }
    }

    private fun extractAttr(line: String, attr: String): String {
        val key = "$attr=\""
        val start = line.indexOf(key, ignoreCase = true)
        if (start == -1) return ""
        val valStart = start + key.length
        val end = line.indexOf("\"", valStart)
        return if (end > valStart) line.substring(valStart, end).trim() else ""
    }

    private fun isAdultContent(title: String, category: String): Boolean {
        val combined = "$title$category".lowercase()
        return combined.contains("xxx") ||
            combined.contains("adult") ||
            combined.contains("18+") ||
            combined.contains("porn") ||
            combined.contains("erotic")
    }

    private fun executeGet(client: OkHttpClient, url: String): String? {
        return try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }

   fun demoCatalog(): CatalogData {
        val live = listOf(
            MediaItem(
                id = "demo_1",
                title = "SE | SVT1 HD",
                category = "SE | Allmänt",
                country = "Sverige",
                subCategory = "Allmänt",
                type = ContentType.LIVE,
                streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                epgTitle = "Direktsändning • Testström",
                epgNext = "Nästa: Aktuellt",
                qualityBadge = "1080p"
            ),
            MediaItem(
                id = "demo_2",
                title = "SE | Sport 4K",
                category = "SE | Sport",
                country = "Sverige",
                subCategory = "Sport",
                type = ContentType.LIVE,
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                epgTitle = "Direktsänd Sport",
                epgNext = "Nästa: Studion",
                qualityBadge = "4K"
            )
        )
        return CatalogData(
            live,
            movies = emptyList(),
            series = emptyList()
        )
    }
}
