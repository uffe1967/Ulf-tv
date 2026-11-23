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

enum class TvContentType {
    LIVE,
    MOVIE,
    SERIES
}

data class TvMediaItem(
    val id: String,
    val title: String,
    val category: String,
    val country: String = "Övriga",
    val subCategory: String = "Allmänt",
    val type: TvContentType,
    val streamUrl: String,
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val rating: String = "",
    val year: String = "",
    val addedTimestamp: Long = 0L,
    val plot: String = "",
    val seriesId: String = "",
    val qualityBadge: String = "",
    val epgNow: String = "",
    val epgNext: String = ""
)

data class TvEpisode(
    val id: String,
    val season: Int,
    val episodeNum: Int,
    val title: String,
    val streamUrl: String,
    val plot: String = ""
)

data class TvAccount(
    val id: String,
    val name: String,
    val mode: String,
    val m3uUrl: String = "",
    val server: String = "",
    val username: String = "",
    val password: String = ""
)

data class TvSettings(
    val userAgent: String = "IPTVSmartersPro",
    val largeBuffer: Boolean = true,
    val hardwareDecoding: Boolean = true,
    val hideAdult: Boolean = true,
    val parentalPin: String = "0000",
    val parentalEnabled: Boolean = false,
    val aspectMode: Int = 0
)

data class TvCatalog(
    val liveChannels: List<TvMediaItem> = emptyList(),
    val movies: List<TvMediaItem> = emptyList(),
    val series: List<TvMediaItem> = emptyList()
)

object TvEngine {
    private val countryMap = mapOf(
        "SE" to "Sverige", "SWE" to "Sverige", "SWEDEN" to "Sverige",
        "NO" to "Norge", "NOR" to "Norge",
        "DK" to "Danmark", "DEN" to "Danmark",
        "FI" to "Finland", "FIN" to "Finland",
        "UK" to "Storbritannien", "GB" to "Storbritannien",
        "US" to "USA", "USA" to "USA",
        "DE" to "Tyskland", "FR" to "Frankrike", "ES" to "Spanien", "IT" to "Italien",
        "TR" to "Turkiet", "AR" to "Arabiska", "NL" to "Nederländerna", "PL" to "Polen"
    )

    fun buildClient(userAgent: String): OkHttpClient = OkHttpClient.Builder()
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

    suspend fun loadCatalog(
        account: TvAccount?,
        settings: TvSettings,
        onStatus: (String) -> Unit
    ): TvCatalog = withContext(Dispatchers.IO) {
        val client = buildClient(settings.userAgent)
        when {
            account != null && account.mode.equals("XTREAM", ignoreCase = true) && account.server.isNotBlank() ->
                loadXtream(client, account, settings, onStatus)
            account != null && account.m3uUrl.isNotBlank() ->
                loadM3u(client, account.m3uUrl, settings, onStatus)
            else -> demoCatalog()
        }
    }

    private suspend fun loadXtream(
        client: OkHttpClient,
        acc: TvAccount,
        settings: TvSettings,
        onStatus: (String) -> Unit
    ): TvCatalog = withContext(Dispatchers.IO) {
        val base = acc.server.trim().trimEnd('/')
        val u = acc.username.trim()
        val p = acc.password.trim()
        val api = "$base/player_api.php?username=$u&password=$p"

        onStatus("Hämtar kategorier...")
        val liveCats = async { fetchCats(client, "$api&action=get_live_categories") }
        val vodCats = async { fetchCats(client, "$api&action=get_vod_categories") }
        val seriesCats = async { fetchCats(client, "$api&action=get_series_categories") }

        val lMap = liveCats.await()
        val vMap = vodCats.await()
        val sMap = seriesCats.await()

        onStatus("Laddar kanaler, filmer & serier...")
        val lDef = async { fetchLive(client, "$api&action=get_live_streams", base, u, p, lMap, settings.hideAdult) }
        val vDef = async { fetchVod(client, "$api&action=get_vod_streams", base, u, p, vMap, settings.hideAdult) }
        val sDef = async { fetchSeries(client, "$api&action=get_series", sMap, settings.hideAdult) }

        TvCatalog(lDef.await(), vDef.await(), sDef.await())
    }

    private fun fetchCats(client: OkHttpClient, url: String): Map<String, String> = try {
        val arr = JSONArray(getText(client, url) ?: return emptyMap())
        buildMap(arr.length()) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("category_id")
                val name = o.optString("category_name")
                if (id.isNotBlank() && name.isNotBlank()) put(id, name)
            }
        }
    } catch (_: Exception) {
        emptyMap()
    }

    private fun fetchLive(
        client: OkHttpClient,
        url: String,
        base: String,
        u: String,
        p: String,
        catMap: Map<String, String>,
        hideAdult: Boolean
    ): List<TvMediaItem> {
        val arr = JSONArray(getText(client, url) ?: return emptyList())
        return ArrayList<TvMediaItem>(arr.length()).apply {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optInt("stream_id", -1).takeIf { it > 0 }?.toString() ?: o.optString("stream_id")
                val name = o.optString("name").trim()
                if (id.isBlank() || name.isBlank()) continue
                val cat = catMap[o.optString("category_id")] ?: "Live TV"
                if (hideAdult && isAdult(name, cat)) continue
                val (country, sub) = parseCountryAndSub(cat, name)
                val icon = o.optString("stream_icon").trim()
                add(
                    TvMediaItem(
                        id = "live_$id",
                        title = name,
                        category = cat,
                        country = country,
                        subCategory = sub,
                        type = TvContentType.LIVE,
                        streamUrl = "$base/live/$u/$p/$id.ts",
                        posterUrl = icon,
                        backdropUrl = icon,
                        qualityBadge = detectQuality(name)
                    )
                )
            }
        }
    }

    private fun fetchVod(
        client: OkHttpClient,
        url: String,
        base: String,
        u: String,
        p: String,
        catMap: Map<String, String>,
        hideAdult: Boolean
    ): List<TvMediaItem> {
        val arr = JSONArray(getText(client, url) ?: return emptyList())
        return ArrayList<TvMediaItem>(arr.length()).apply {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optInt("stream_id", -1).takeIf { it > 0 }?.toString() ?: o.optString("stream_id")
                val name = o.optString("name").trim()
                if (id.isBlank() || name.isBlank()) continue
                val cat = catMap[o.optString("category_id")] ?: "Filmer"
                if (hideAdult && isAdult(name, cat)) continue
                val ext = o.optString("container_extension", "mp4").ifBlank { "mp4" }
                val poster = o.optString("stream_icon").trim()
                add(
                    TvMediaItem(
                        id = "vod_$id",
                        title = name,
                        category = cat,
                        country = parseCountryAndSub(cat, name).first,
                        subCategory = normalizeGenre(cat, name),
                        type = TvContentType.MOVIE,
                        streamUrl = "$base/movie/$u/$p/$id.$ext",
                        posterUrl = poster,
                        backdropUrl = poster,
                        rating = o.optString("rating").trim(),
                        year = extractYear(name),
                        addedTimestamp = o.optString("added").toLongOrNull() ?: 0L,
                        qualityBadge = detectQuality(name)
                    )
                )
            }
        }
    }

    private fun fetchSeries(
        client: OkHttpClient,
        url: String,
        catMap: Map<String, String>,
        hideAdult: Boolean
    ): List<TvMediaItem> {
        val arr = JSONArray(getText(client, url) ?: return emptyList())
        return ArrayList<TvMediaItem>(arr.length()).apply {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optInt("series_id", -1).takeIf { it > 0 }?.toString() ?: o.optString("series_id")
                val name = o.optString("name").trim()
                if (id.isBlank() || name.isBlank()) continue
                val cat = catMap[o.optString("category_id")] ?: "Serier"
                if (hideAdult && isAdult(name, cat)) continue
                val cover = o.optString("cover").trim()
                val bdArr = o.optJSONArray("backdrop_path")
                val backdrop = if (bdArr != null && bdArr.length() > 0) bdArr.optString(0).trim() else cover
                val yr = o.optString("releaseDate").take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }
                    ?: extractYear(name)
                add(
                    TvMediaItem(
                        id = "series_$id",
                        title = name,
                        category = cat,
                        country = parseCountryAndSub(cat, name).first,
                        subCategory = normalizeGenre(cat, o.optString("genre").ifBlank { name }),
                        type = TvContentType.SERIES,
                        streamUrl = "",
                        posterUrl = cover,
                        backdropUrl = backdrop.ifBlank { cover },
                        rating = o.optString("rating").trim(),
                        year = yr,
                        addedTimestamp = o.optString("last_modified").toLongOrNull() ?: 0L,
                        plot = o.optString("plot").trim(),
                        seriesId = id,
                        qualityBadge = detectQuality(name)
                    )
                )
            }
        }
    }

    suspend fun loadEpisodes(
        account: TvAccount?,
        seriesId: String,
        userAgent: String
    ): Map<Int, List<TvEpisode>> = withContext(Dispatchers.IO) {
        if (account == null || !account.mode.equals("XTREAM", ignoreCase = true) || seriesId.isBlank()) {
            return@withContext emptyMap()
        }
        val client = buildClient(userAgent)
        val base = account.server.trim().trimEnd('/')
        val u = account.username.trim()
        val p = account.password.trim()
        val body = getText(client, "$base/player_api.php?username=$u&password=$p&action=get_series_info&series_id=$seriesId")
            ?: return@withContext emptyMap()
        try {
            val epsObj = JSONObject(body).optJSONObject("episodes") ?: return@withContext emptyMap()
            val out = sortedMapOf<Int, List<TvEpisode>>()
            val keys = epsObj.keys()
            while (keys.hasNext()) {
                val sKey = keys.next()
                val sNum = sKey.toIntOrNull() ?: 1
                val arr = epsObj.optJSONArray(sKey) ?: continue
                val list = ArrayList<TvEpisode>(arr.length())
                for (i in 0 until arr.length()) {
                    val ep = arr.optJSONObject(i) ?: continue
                    val epId = ep.optString("id")
                    if (epId.isBlank()) continue
                    val ext = ep.optString("container_extension", "mp4").ifBlank { "mp4" }
                    val epNum = ep.optInt("episode_num", i + 1)
                    val epTitle = ep.optString("title").ifBlank { "Avsnitt $epNum" }
                    val infoObj = ep.optJSONObject("info")
                    val epPlot = infoObj?.optString("plot").orEmpty().trim()
                    list.add(
                        TvEpisode(
                            id = epId,
                            season = sNum,
                            episodeNum = epNum,
                            title = epTitle,
                            streamUrl = "$base/series/$u/$p/$epId.$ext",
                            plot = epPlot
                        )
                    )
                }
                if (list.isNotEmpty()) out[sNum] = list
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private suspend fun loadM3u(
        client: OkHttpClient,
        url: String,
        settings: TvSettings,
        onStatus: (String) -> Unit
    ): TvCatalog = withContext(Dispatchers.IO) {
        onStatus("Laddar ner M3U...")
        val live = ArrayList<TvMediaItem>()
        val movies = ArrayList<TvMediaItem>()
        val series = ArrayList<TvMediaItem>()
        try {
            client.newCall(Request.Builder().url(url.trim()).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext TvCatalog()
                val stream = resp.body?.byteStream() ?: return@withContext TvCatalog()
                val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8), 65536)
                var title = ""
                var group = "Allmänt"
                var logo = ""
                var idx = 0
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val t = line!!.trim()
                    if (t.isEmpty()) continue
                    if (t.startsWith("#EXTINF:", ignoreCase = true)) {
                        title = t.substringAfterLast(",").trim()
                        group = extractAttr(t, "group-title").ifBlank { "Allmänt" }
                        logo = extractAttr(t, "tvg-logo")
                    } else if (!t.startsWith("#")) {
                        val chTitle = title.ifBlank { "Kanal ${idx + 1}" }
                        if (!settings.hideAdult || !isAdult(chTitle, group)) {
                            val lu = t.lowercase()
                            val lg = group.lowercase()
                            val type = when {
                                lu.contains("/series/") || lg.contains("serie") ||
                                    Regex("""S\d{1,2}\s*E\d{1,2}""", RegexOption.IGNORE_CASE).containsMatchIn(chTitle) -> TvContentType.SERIES
                                lu.contains("/movie/") || lg.contains("film") || lg.contains("vod") ||
                                    lg.contains("movie") || lu.endsWith(".mp4") || lu.endsWith(".mkv") -> TvContentType.MOVIE
                                else -> TvContentType.LIVE
                            }
                            val (country, sub) = parseCountryAndSub(group, chTitle)
                            val item = TvMediaItem(
                                id = "m3u_$idx",
                                title = chTitle,
                                category = group,
                                country = country,
                                subCategory = if (type == TvContentType.LIVE) sub else normalizeGenre(group, chTitle),
                                type = type,
                                streamUrl = t,
                                posterUrl = logo,
                                backdropUrl = logo,
                                year = extractYear(chTitle),
                                addedTimestamp = idx.toLong(),
                                qualityBadge = detectQuality(chTitle)
                            )
                            when (type) {
                                TvContentType.LIVE -> live.add(item)
                                TvContentType.MOVIE -> movies.add(item)
                                TvContentType.SERIES -> series.add(item)
                            }
                            idx++
                        }
                        title = ""
                        logo = ""
                    }
                }
            }
        } catch (_: Exception) {
        }
        TvCatalog(live, movies, series)
    }

        fun parseCountryAndSub(category: String, title: String): Pair<String, String> {
        val upCat = category.uppercase().trim()
        val upTitle = title.uppercase().trim()
        var country = "Övriga"
        val match = Regex("""^\[?([A-Z]{2,5})\]?\s*[\|:\-/\]]""").find(upCat)
            ?: Regex("""^\[?([A-Z]{2,5})\]?\s*[\|:\-/\]]""").find(upTitle)
        if (match != null) {
            country = countryMap[match.groupValues[1]] ?: match.groupValues[1]
        } else {
            for ((k, v) in countryMap) {
                if (upCat.startsWith("$k ") || upCat.contains(" $k ") || upCat.contains(v.uppercase())) {
                    country = v
                    break
                }
            }
        }
        val l = "$category $title".lowercase()
        val sub = when {
            l.contains("sport") || l.contains("fotboll") || l.contains("hockey") || l.contains("viaplay") -> "Sport"
            l.contains("film") || l.contains("movie") || l.contains("cinema") -> "Film & Serier"
            l.contains("nyhet") || l.contains("news") -> "Nyheter"
            l.contains("barn") || l.contains("kids") || l.contains("disney") -> "Barn"
            l.contains("dok") || l.contains("doc") -> "Dokumentär"
            else -> "Allmänt"
        }
        return country to sub
    }

    private fun normalizeGenre(cat: String, text: String): String {
        val l = "$cat $text".lowercase()
        return when {
            l.contains("action") -> "Action"
            l.contains("komedi") || l.contains("comedy") -> "Komedi"
            l.contains("drama") -> "Drama"
            l.contains("thriller") || l.contains("crime") -> "Thriller & Kriminal"
            l.contains("sci-fi") || l.contains("fantasy") -> "Sci-Fi & Fantasy"
            l.contains("barn") || l.contains("familj") || l.contains("anim") -> "Barn & Familj"
            else -> cat.substringAfter("|").substringAfter(":").trim().ifBlank { "Övrigt" }
        }
    }

    private fun extractYear(text: String): String =
        Regex("""\b(19\d{2}|20[0-2]\d)\b""").find(text)?.groupValues?.get(1).orEmpty()

    private fun detectQuality(title: String): String {
        val u = title.uppercase()
        return when {
            u.contains("4K") || u.contains("UHD") -> "4K"
            u.contains("FHD") || u.contains("1080") -> "1080p"
            u.contains("HD") || u.contains("720") -> "720p"
            else -> ""
        }
    }

    private fun extractAttr(line: String, attr: String): String {
        val key = "$attr=\""
        val s = line.indexOf(key, ignoreCase = true)
        if (s == -1) return ""
        val vs = s + key.length
        val e = line.indexOf("\"", vs)
        return if (e > vs) line.substring(vs, e).trim() else ""
    }

    private fun isAdult(title: String, cat: String): Boolean {
        val c = "$title $cat".lowercase()
        return c.contains("xxx") || c.contains("adult") || c.contains("18+") || c.contains("porn")
    }

    private fun getText(client: OkHttpClient, url: String): String? = try {
        client.newCall(Request.Builder().url(url).build()).execute().use {
            if (it.isSuccessful) it.body?.string() else null
        }
    } catch (_: Exception) {
        null
    }

    fun demoCatalog(): TvCatalog = TvCatalog(
        liveChannels = listOf(
            TvMediaItem(
                id = "demo_1",
                title = "SE | SVT1 HD",
                category = "SE | Allmänt",
                country = "Sverige",
                subCategory = "Allmänt",
                type = TvContentType.LIVE,
                streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                qualityBadge = "1080p",
                epgNow = "Aktuellt nyheter",
                epgNext = "Kulturnyheterna"
            ),
            TvMediaItem(
                id = "demo_2",
                title = "SE | Sport 4K",
                category = "SE | Sport",
                country = "Sverige",
                subCategory = "Sport",
                type = TvContentType.LIVE,
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                qualityBadge = "4K",
                epgNow = "Direktsändning Studio",
                epgNext = "Höjdpunkter"
            )
        ),
        movies = listOf(
            TvMediaItem(
                id = "demo_m1",
                title = "Big Buck Bunny (2025)",
                category = "Barn & Familj",
                country = "USA",
                subCategory = "Barn & Familj",
                type = TvContentType.MOVIE,
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                posterUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Big_buck_bunny_poster_big.jpg/440px-Big_buck_bunny_poster_big.jpg",
                backdropUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Big_buck_bunny_poster_big.jpg/800px-Big_buck_bunny_poster_big.jpg",
                rating = "8.4",
                year = "2025",
                addedTimestamp = 200L,
                plot = "En godhjärtad jättekanin i skogen.",
                qualityBadge = "1080p"
            )
        ),
        series = listOf(
            TvMediaItem(
                id = "demo_s1",
                title = "For Bigger Escapes",
                category = "Äventyr",
                country = "USA",
                subCategory = "Sci-Fi & Fantasy",
                type = TvContentType.SERIES,
                streamUrl = "",
                posterUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerEscapes.jpg",
                backdropUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerEscapes.jpg",
                rating = "8.1",
                year = "2025",
                addedTimestamp = 150L,
                plot = "Korta äventyr i olika miljöer.",
                seriesId = "demo_series_1",
                qualityBadge = "1080p"
            )
        )
    )
}
