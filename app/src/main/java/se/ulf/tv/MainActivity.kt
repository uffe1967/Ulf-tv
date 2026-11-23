package se.ulf.tv

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.TimeUnit

private enum class TvSection(val label: String, val icon: ImageVector) {
    HOME("Hem", Icons.Default.Home),
    LIVE("Live TV", Icons.Default.LiveTv),
    MOVIES("Filmer", Icons.Default.Movie),
    SERIES("Serier", Icons.Default.Tv),
    FAVORITES("Favoriter", Icons.Default.Favorite),
    SEARCH("Sök", Icons.Default.Search),
    SETTINGS("Inställningar", Icons.Default.Settings)
}

private enum class SettingsTab(val label: String) {
    ACCOUNTS("Konton"),
    PLAYBACK("Uppspelning"),
    CONTENT("Innehåll & PIN")
}

private enum class TvContentType { LIVE, MOVIE, SERIES }

private data class TvMediaItem(
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
    val qualityBadge: String = ""
)

private data class TvEpisode(
    val id: String,
    val season: Int,
    val episodeNum: Int,
    val title: String,
    val streamUrl: String
)

private data class TvTrackOption(
    val label: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val isSelected: Boolean
)

private data class TvStreamInfo(
    val resolution: String = "",
    val fps: String = "",
    val videoCodec: String = "",
    val activeAudioLang: String = "",
    val activeSubtitleLang: String = "Av"
)

private data class TvAccount(
    val id: String,
    val name: String,
    val mode: String,
    val m3uUrl: String = "",
    val server: String = "",
    val username: String = "",
    val password: String = ""
)

private data class TvSettings(
    val userAgent: String = "IPTVSmartersPro",
    val largeBuffer: Boolean = true,
    val hardwareDecoding: Boolean = true,
    val hideAdult: Boolean = true,
    val parentalPin: String = "0000",
    val parentalEnabled: Boolean = false,
    val aspectMode: Int = 0
)

private data class TvCatalog(
    val liveChannels: List<TvMediaItem> = emptyList(),
    val movies: List<TvMediaItem> = emptyList(),
    val series: List<TvMediaItem> = emptyList()
)

private object TvEngine {
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
            account != null && account.mode == "XTREAM" && account.server.isNotBlank() ->
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

        val liveCatMap = liveCats.await()
        val vodCatMap = vodCats.await()
        val seriesCatMap = seriesCats.await()

        onStatus("Laddar kanaler, filmer & serier...")
        val liveDef = async { fetchXtreamLive(client, "$api&action=get_live_streams", base, u, p, liveCatMap, settings.hideAdult) }
        val vodDef = async { fetchXtreamVod(client, "$api&action=get_vod_streams", base, u, p, vodCatMap, settings.hideAdult) }
        val serDef = async { fetchXtreamSeries(client, "$api&action=get_series", seriesCatMap, settings.hideAdult) }

        TvCatalog(liveDef.await(), vodDef.await(), serDef.await())
    }

    private fun fetchCats(client: OkHttpClient, url: String): Map<String, String> = try {
        val body = getText(client, url) ?: return emptyMap()
        val arr = JSONArray(body)
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

    private fun fetchXtreamLive(
        client: OkHttpClient,
        url: String,
        base: String,
        u: String,
        p: String,
        catMap: Map<String, String>,
        hideAdult: Boolean
    ): List<TvMediaItem> {
        val body = getText(client, url) ?: return emptyList()
        return try {
            val arr = JSONArray(body)
            ArrayList<TvMediaItem>(arr.length()).apply {
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
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchXtreamVod(
        client: OkHttpClient,
        url: String,
        base: String,
        u: String,
        p: String,
        catMap: Map<String, String>,
        hideAdult: Boolean
    ): List<TvMediaItem> {
        val body = getText(client, url) ?: return emptyList()
        return try {
            val arr = JSONArray(body)
            ArrayList<TvMediaItem>(arr.length()).apply {
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
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchXtreamSeries(
        client: OkHttpClient,
        url: String,
        catMap: Map<String, String>,
        hideAdult: Boolean
    ): List<TvMediaItem> {
        val body = getText(client, url) ?: return emptyList()
        return try {
            val arr = JSONArray(body)
            ArrayList<TvMediaItem>(arr.length()).apply {
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
                    val rel = o.optString("releaseDate")
                    val yr = rel.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) } ?: extractYear(name)
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
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun loadEpisodes(
        account: TvAccount?,
        seriesId: String,
        userAgent: String
    ): Map<Int, List<TvEpisode>> = withContext(Dispatchers.IO) {
        if (account == null || account.mode != "XTREAM" || seriesId.isBlank()) return@withContext emptyMap()
        val client = buildClient(userAgent)
        val base = account.server.trim().trimEnd('/')
        val u = account.username.trim()
        val p = account.password.trim()
        val body = getText(client, "$base/player_api.php?username=$u&password=$p&action=get_series_info&series_id=$seriesId")
            ?: return@withContext emptyMap()
        try {
            val root = JSONObject(body)
            val epsObj = root.optJSONObject("episodes") ?: return@withContext emptyMap()
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
                    list.add(
                        TvEpisode(
                            id = epId,
                            season = sNum,
                            episodeNum = epNum,
                            title = epTitle,
                            streamUrl = "$base/series/$u/$p/$epId.$ext"
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
                val body = resp.body?.byteStream() ?: return@withContext TvCatalog()
                val reader = BufferedReader(InputStreamReader(body, Charsets.UTF_8), 65536)
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

    private fun parseCountryAndSub(category: String, title: String): Pair<String, String> {
        val raw = "$category$title".trim()
        val upCat = category.uppercase().trim()
        val upTitle = title.uppercase().trim()
        var country = "Övriga"
        val match = Regex("""^\[?([A-Z]{2,5})\]?\s*[\|:\-/\]]""").find(upCat)
            ?: Regex("""^\[?([A-Z]{2,5})\]?\s*[\|:\-/\]]""").find(upTitle)
        if (match != null) {
            val code = match.groupValues[1]
            country = countryMap[code] ?: code
        } else {
            for ((k, v) in countryMap) {
                if (upCat.startsWith("$k ") \vert{}\vert{} upCat.contains(" $k ") || upCat.contains(v.uppercase())) {
                    country = v
                    break
                }
            }
        }
        val l = raw.lowercase()
        val sub = when {
            l.contains("sport") || l.contains("fotboll") || l.contains("football") || l.contains("hockey") ||
                l.contains("viaplay") || l.contains("eurosport") || l.contains("bein") -> "Sport"
            l.contains("film") || l.contains("movie") || l.contains("cinema") || l.contains("hbo") -> "Film & Serier"
            l.contains("nyhet") || l.contains("news") || l.contains("cnn") || l.contains("bbc world") -> "Nyheter"
            l.contains("barn") || l.contains("kids") || l.contains("disney") || l.contains("cartoon") -> "Barn"
            l.contains("dok") || l.contains("doc") || l.contains("discovery") || l.contains("nat geo") -> "Dokumentär"
            l.contains("musik") || l.contains("music") || l.contains("mtv") -> "Musik"
            else -> "Allmänt"
        }
        return country to sub
    }

    private fun normalizeGenre(cat: String, text: String): String {
        val l = "$cat$text".lowercase()
        return when {
            l.contains("action") -> "Action"
            l.contains("komedi") || l.contains("comedy") -> "Komedi"
            l.contains("drama") -> "Drama"
            l.contains("thriller") || l.contains("kriminal") || l.contains("crime") -> "Thriller & Kriminal"
            l.contains("sci-fi") || l.contains("science") || l.contains("fantasy") -> "Sci-Fi & Fantasy"
            l.contains("skräck") || l.contains("horror") -> "Skräck"
            l.contains("barn") || l.contains("familj") || l.contains("family") || l.contains("anim") -> "Barn & Familj"
            l.contains("dok") || l.contains("doc") -> "Dokumentär"
            else -> cat.substringAfter("|").substringAfter(":").trim().ifBlank { "Övrigt" }
        }
    }

    private fun extractYear(text: String): String =
        Regex("""\b(19\d{2}|20[0-2]\d)\b""").find(text)?.groupValues?.get(1).orEmpty()

    private fun detectQuality(title: String): String {
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
        val vs = start + key.length
        val end = line.indexOf("\"", vs)
        return if (end > vs) line.substring(vs, end).trim() else ""
    }

    private fun isAdult(title: String, cat: String): Boolean {
        val c = "$title$cat".lowercase()
        return c.contains("xxx") || c.contains("adult") || c.contains("18+") || c.contains("porn")
    }

    private fun getText(client: OkHttpClient, url: String): String? = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (r.isSuccessful) r.body?.string() else null
        }
    } catch (_: Exception) {
        null
    }

    fun demoCatalog(): TvCatalog = TvCatalog(
        liveChannels = listOf(
            TvMediaItem("demo_1", "SE | SVT1 HD", "SE | Allmänt", "Sverige", "Allmänt", TvContentType.LIVE, "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", qualityBadge = "1080p"),
            TvMediaItem("demo_2", "SE | Sport 4K", "SE | Sport", "Sverige", "Sport", TvContentType.LIVE, "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4", qualityBadge = "4K")
        ),
        movies = listOf(
            TvMediaItem("demo_m1", "Big Buck Bunny (2025)", "Barn & Familj", "USA", "Barn & Familj", TvContentType.MOVIE, "
