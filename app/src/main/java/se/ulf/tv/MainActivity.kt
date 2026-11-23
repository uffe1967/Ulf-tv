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
        val raw = "$category $title".trim()
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
                if (upCat.startsWith("$k ") || upCat.contains(" $k ") || upCat.contains(v.uppercase())) {
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
        val l = "$cat $text".lowercase()
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
        val c = "$title $cat".lowercase()
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
            TvMediaItem("demo_m1", "Big Buck Bunny (2025)", "Barn & Familj", "USA", "Barn & Familj", TvContentType.MOVIE, "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", posterUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Big_buck_bunny_poster_big.jpg/440px-Big_buck_bunny_poster_big.jpg", backdropUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Big_buck_bunny_poster_big.jpg/800px-Big_buck_bunny_poster_big.jpg", rating = "8.4", year = "2025", addedTimestamp = 200L, plot = "En godhjärtad jättekanin i skogen.", qualityBadge = "1080p"),
            TvMediaItem("demo_m2", "Tears of Steel (2026)", "Sci-Fi & Fantasy", "Nederländerna", "Sci-Fi & Fantasy", TvContentType.MOVIE, "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4", posterUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/70/Tears_of_Steel_poster.jpg/440px-Tears_of_Steel_poster.jpg", backdropUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/70/Tears_of_Steel_poster.jpg/800px-Tears_of_Steel_poster.jpg", rating = "7.9", year = "2026", addedTimestamp = 300L, plot = "Futuristiskt äventyr.", qualityBadge = "4K")
        ),
        series = emptyList()
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { UlfTvApp() }
    }
}

@Composable
private fun UlfTvApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(loadSettings(context)) }
    var accounts by remember { mutableStateOf(loadAccounts(context)) }
    var activeAccountId by remember { mutableStateOf(loadActiveAccountId(context)) }
    var favorites by remember { mutableStateOf(loadFavorites(context)) }
    var catalog by remember { mutableStateOf(TvEngine.demoCatalog()) }
    var statusText by remember { mutableStateOf("Demoläge") }
    var isLoading by remember { mutableStateOf(false) }

    var section by remember { mutableStateOf(TvSection.HOME) }
    var playingItem by remember { mutableStateOf<TvMediaItem?>(null) }
    var selectedSeries by remember { mutableStateOf<TvMediaItem?>(null) }
    var pinUnlocked by remember { mutableStateOf(false) }
    var pendingPlay by remember { mutableStateOf<TvMediaItem?>(null) }

    val activeAccount = accounts.firstOrNull { it.id == activeAccountId }

    fun reload(acc: TvAccount?, st: TvSettings) {
        scope.launch {
            isLoading = true
            catalog = TvEngine.loadCatalog(acc, st) { statusText = it }
            statusText = if (acc == null) "Demoläge • ${catalog.liveChannels.size} live"
            else "${acc.name}: ${catalog.liveChannels.size} live • ${catalog.movies.size} filmer • ${catalog.series.size} serier"
            isLoading = false
        }
    }

    LaunchedEffect(activeAccountId, settings.userAgent, settings.hideAdult) {
        reload(activeAccount, settings)
    }

    fun toggleFav(id: String) {
        favorites = if (favorites.contains(id)) favorites - id else favorites + id
        saveFavorites(context, favorites)
    }

    fun startItem(item: TvMediaItem) {
        if (item.type == TvContentType.SERIES) {
            selectedSeries = item
        } else if (settings.parentalEnabled && !pinUnlocked && isAdultItem(item)) {
            pendingPlay = item
        } else {
            playingItem = item
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A0D14), Color(0xFF06070B), Color(0xFF040508))))
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
                    when {
                        playingItem != null -> { playingItem = null; true }
                        selectedSeries != null -> { selectedSeries = null; true }
                        section != TvSection.HOME -> { section = TvSection.HOME; true }
                        else -> false
                    }
                } else false
            }
    ) {
        when {
            playingItem != null -> PlayerViewScreen(
                item = playingItem!!,
                liveChannels = catalog.liveChannels,
                settings = settings,
                isFavorite = favorites.contains(playingItem!!.id),
                onToggleFav = { toggleFav(playingItem!!.id) },
                onZap = { playingItem = it },
                onExit = { playingItem = null }
            )

            selectedSeries != null -> SeriesScreen(
                series = selectedSeries!!,
                account = activeAccount,
                userAgent = settings.userAgent,
                isFavorite = favorites.contains(selectedSeries!!.id),
                onToggleFav = { toggleFav(selectedSeries!!.id) },
                onPlayEp = { ep ->
                    playingItem = TvMediaItem(
                        id = "ep_${ep.id}",
                        title = "${selectedSeries!!.title} • S${ep.season}E${ep.episodeNum} - ${ep.title}",
                        category = selectedSeries!!.category,
                        country = selectedSeries!!.country,
                        subCategory = selectedSeries!!.subCategory,
                        type = TvContentType.SERIES,
                        streamUrl = ep.streamUrl,
                        posterUrl = selectedSeries!!.posterUrl,
                        backdropUrl = selectedSeries!!.backdropUrl,
                        plot = selectedSeries!!.plot,
                        qualityBadge = selectedSeries!!.qualityBadge
                    )
                },
                onBack = { selectedSeries = null }
            )

            else -> Column(modifier = Modifier.fillMaxSize()) {
                TopBar(section, statusText, isLoading, { section = it }, { reload(activeAccount, settings) })
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (section) {
                        TvSection.HOME -> HomeScreen(catalog, favorites, ::startItem) { section = it }
                        TvSection.LIVE -> LiveCountryScreen(catalog.liveChannels, favorites, ::toggleFav, ::startItem)
                        TvSection.MOVIES -> VodScreen("Filmer", catalog.movies, favorites, ::toggleFav, ::startItem)
                        TvSection.SERIES -> VodScreen("Serier", catalog.series, favorites, ::toggleFav, ::startItem)
                        TvSection.FAVORITES -> {
                            val all = catalog.liveChannels + catalog.movies + catalog.series
                            VodScreen("Favoriter", all.filter { favorites.contains(it.id) }, favorites, ::toggleFav, ::startItem)
                        }
                        TvSection.SEARCH -> SearchScreen(catalog, ::startItem)
                        TvSection.SETTINGS -> SettingsScreen(
                            settings = settings,
                            accounts = accounts,
                            activeId = activeAccountId,
                            onSaveSettings = { settings = it; saveSettings(context, it) },
                            onAddAccount = { acc ->
                                val list = accounts + acc
                                accounts = list; saveAccounts(context, list)
                                activeAccountId = acc.id; saveActiveAccountId(context, acc.id)
                                reload(acc, settings); section = TvSection.HOME
                            },
                            onSelectAccount = { id ->
                                activeAccountId = id; saveActiveAccountId(context, id)
                                reload(accounts.firstOrNull { it.id == id }, settings)
                            },
                            onDeleteAccount = { id ->
                                val list = accounts.filterNot { it.id == id }
                                accounts = list; saveAccounts(context, list)
                                if (activeAccountId == id) {
                                    val next = list.firstOrNull()?.id.orEmpty()
                                    activeAccountId = next; saveActiveAccountId(context, next)
                                    reload(list.firstOrNull(), settings)
                                }
                            }
                        )
                    }
                }
            }
        }

        if (pendingPlay != null) {
            PinDialog(settings.parentalPin, {
                pinUnlocked = true
                val target = pendingPlay
                pendingPlay = null
                target?.let { playingItem = it }
            }) { pendingPlay = null }
        }
    }
}

@Composable
private fun TopBar(
    current: TvSection,
    status: String,
    loading: Boolean,
    onSelect: (TvSection) -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 42.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                Text("U", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text("ULF TV", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        Row(
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x26FFFFFF)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TvSection.entries.forEach { tab ->
                val sel = current == tab
                var focused by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "nav")
                Box(
                    modifier = Modifier.scale(scale).clip(RoundedCornerShape(50))
                        .background(when { sel -> Color.White; focused -> Color(0x44FFFFFF); else -> Color.Transparent })
                        .onFocusChanged { focused = it.isFocused }.focusable().clickable { onSelect(tab) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(tab.icon, contentDescription = null, tint = if (sel) Color.Black else Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tab.label, color = if (sel) Color.Black else Color.White, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (loading) "Synkar..." else status, color = Color(0xFF9CA3AF), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(210.dp))
            var rf by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(if (rf) Color.White else Color(0x22FFFFFF)).onFocusChanged { rf = it.isFocused }.focusable().clickable { onRefresh() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = if (rf) Color.Black else Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun HomeScreen(catalog: TvCatalog, favorites: Set<String>, onPlay: (TvMediaItem) -> Unit, onMore: (TvSection) -> Unit) {
    val hero = catalog.movies.firstOrNull() ?: catalog.series.firstOrNull() ?: catalog.liveChannels.firstOrNull()
    val favs = (catalog.liveChannels + catalog.movies + catalog.series).filter { favorites.contains(it.id) }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        if (hero != null) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(300.dp).padding(horizontal = 42.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF131826))) {
                    if (hero.backdropUrl.isNotBlank()) AsyncImage(hero.backdropUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xF206080E), Color(0xB806080E), Color(0x3306080E)))))
                    Column(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(0.65f).padding(30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Badge(hero.country, Color(0xFF1E3A8A))
                            Badge(hero.subCategory, Color(0xFF374151))
                            if (hero.qualityBadge.isNotBlank()) Badge(hero.qualityBadge, Color(0xFF065F46))
                        }
                        Text(hero.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Button(onClick = { onPlay(hero) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (hero.type == TvContentType.SERIES) "Visa säsonger" else "Spela nu", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        if (favs.isNotEmpty()) item { Shelf("Favoriter", favs.take(24), false, onPlay) { onMore(TvSection.FAVORITES) } }
        if (catalog.liveChannels.isNotEmpty()) item { Shelf("Live TV • Länder (${catalog.liveChannels.size})", catalog.liveChannels.take(30), false, onPlay) { onMore(TvSection.LIVE) } }
        if (catalog.movies.isNotEmpty()) item { Shelf("Senaste Filmer (${catalog.movies.size})", catalog.movies.sortedByDescending { it.addedTimestamp }.take(30), true, onPlay) { onMore(TvSection.MOVIES) } }
        if (catalog.series.isNotEmpty()) item { Shelf("Senaste Serier (${catalog.series.size})", catalog.series.sortedByDescending { it.addedTimestamp }.take(30), true, onPlay) { onMore(TvSection.SERIES) } }
    }
}

@Composable
private fun LiveCountryScreen(channels: List<TvMediaItem>, favorites: Set<String>, onToggleFav: (String) -> Unit, onSelect: (TvMediaItem) -> Unit) {
    val countries = remember(channels) {
        val g = channels.groupBy { it.country }
        listOf("Alla länder") + g.keys.sortedByDescending { g[it]?.size ?: 0 }
    }
    var selCountry by remember { mutableStateOf("Alla länder") }
    val cChans = remember(channels, selCountry) { if (selCountry == "Alla länder") channels else channels.filter { it.country == selCountry } }
    val subs = remember(cChans) {
        val order = listOf("Sport", "Film & Serier", "Nyheter", "Barn", "Dokumentär", "Musik", "Allmänt")
        val present = cChans.map { it.subCategory }.toSet()
        listOf("Alla") + order.filter { present.contains(it) }
    }
    var selSub by remember { mutableStateOf("Alla") }
    LaunchedEffect(selCountry) { selSub = "Alla" }
    val visible = remember(cChans, selSub) { if (selSub == "Alla") cChans else cChans.filter { it.subCategory == selSub } }
    var focused by remember { mutableStateOf(visible.firstOrNull()) }
    LaunchedEffect(visible) { if (visible.isNotEmpty() && !visible.contains(focused)) focused = visible.first() }

    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 42.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        LazyColumn(modifier = Modifier.width(230.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Text("Länder", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp)) }
            items(countries) { c ->
                val count = if (c == "Alla länder") channels.size else channels.count { it.country == c }
                val active = selCountry == c
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (active) Color(0xFF2563EB) else Color(0x12FFFFFF)).focusable().clickable { selCountry = c }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(c, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("$count", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                    }
                }
            }
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(subs) { s ->
                    val active = selSub == s
                    Button(onClick = { selSub = s }, colors = ButtonDefaults.buttonColors(containerColor = if (active) Color.White else Color(0x20FFFFFF), contentColor = if (active) Color.Black else Color.White), shape = RoundedCornerShape(50)) {
                        Text(s, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
            focused?.let { info ->
                Surface(color = Color(0x18FFFFFF), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0x26FFFFFF)), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(info.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Badge("${info.country} • ${info.subCategory}", Color(0xFF1E3A8A))
                                if (info.qualityBadge.isNotBlank()) Badge(info.qualityBadge, Color(0xFF065F46))
                            }
                            Text("Kategori: ${info.category} • Ljudspår & undertexter i spelaren", color = Color(0xFF9CA3AF), fontSize = 13.sp, maxLines = 1)
                        }
                        OutlinedButton(onClick = { onToggleFav(info.id) }, border = BorderStroke(1.dp, Color(0x55FFFFFF))) {
                            Icon(if (favorites.contains(info.id)) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null, tint = Color(0xFFF87171))
                            Spacer(Modifier.width(6.dp))
                            Text(if (favorites.contains(info.id)) "Favorit" else "Spara", color = Color.White)
                        }
                    }
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(visible.take(250), key = { it.id }) { ch ->
                    Box(Modifier.onFocusChanged { if (it.isFocused) focused = ch }) {
                        MediaCard(ch, false) { onSelect(ch) }
                    }
                }
            }
        }
    }
}

@Composable
private fun VodScreen(title: String, items: List<TvMediaItem>, favorites: Set<String>, onToggleFav: (String) -> Unit, onSelect: (TvMediaItem) -> Unit) {
    val filters = remember(items) {
        listOf("Senast tillagda", "Nysläppta (2025–2026)", "Topprankade", "Alla") + items.map { it.subCategory }.filter { it.isNotBlank() }.distinct().sorted()
    }
    var selFilter by remember { mutableStateOf("Senast tillagda") }
    val list = remember(items, selFilter) {
        when (selFilter) {
            "Senast tillagda" -> items.sortedByDescending { it.addedTimestamp }
            "Nysläppta (2025–2026)" -> items.filter { it.year == "2026" || it.year == "2025" }.ifEmpty { items.sortedByDescending { it.year } }
            "Topprankade" -> items.sortedByDescending { it.rating.toDoubleOrNull() ?: 0.0 }
            "Alla" -> items
            else -> items.filter { it.subCategory == selFilter }
        }
    }
    var focused by remember { mutableStateOf(list.firstOrNull()) }
    LaunchedEffect(list) { if (list.isNotEmpty() && !list.contains(focused)) focused = list.first() }

    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 42.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        LazyColumn(modifier = Modifier.width(240.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp)) }
            items(filters) { f ->
                val active = selFilter == f
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (active) Color(0xFF2563EB) else Color(0x12FFFFFF)).focusable().clickable { selFilter = f }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(f, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            focused?.let { info ->
                Surface(color = Color(0x18FFFFFF), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0x26FFFFFF)), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(info.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (info.year.isNotBlank()) Badge(info.year, Color(0xFF1E3A8A))
                                if (info.qualityBadge.isNotBlank()) Badge(info.qualityBadge, Color(0xFF065F46))
                                if (info.rating.isNotBlank()) Badge("★ ${info.rating}", Color(0xFF78350F))
                            }
                            Text(info.plot.ifBlank { "${info.subCategory} • ${info.category}" }, color = Color(0xFF9CA3AF), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(onClick = { onToggleFav(info.id) }, border = BorderStroke(1.dp, Color(0x55FFFFFF))) {
                            Icon(if (favorites.contains(info.id)) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null, tint = Color(0xFFF87171))
                            Spacer(Modifier.width(6.dp))
                            Text(if (favorites.contains(info.id)) "Favorit" else "Spara", color = Color.White)
                        }
                    }
                }
            }
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Inga titlar hittades.", color = Color(0xFF9CA3AF)) }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(list.take(250), key = { it.id }) { item ->
                        Box(Modifier.onFocusChanged { if (it.isFocused) focused = item }) {
                            MediaCard(item, item.type != TvContentType.LIVE) { onSelect(item) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Shelf(title: String, items: List<TvMediaItem>, poster: Boolean, onPlay: (TvMediaItem) -> Unit, onMore: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 42.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("Visa alla ›", color = Color(0xFF9CA3AF), fontSize = 14.sp, modifier = Modifier.focusable().clickable { onMore() }.padding(8.dp))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 42.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items(items, key = { it.id }) { item -> MediaCard(item, poster) { onPlay(item) } }
        }
    }
}

@Composable
private fun MediaCard(item: TvMediaItem, poster: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "card")
    Column(modifier = Modifier.width(if (poster) 150.dp else 230.dp).scale(scale).onFocusChanged { focused = it.isFocused }.focusable().clickable { onClick() }) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(if (poster) 2f / 3f else 16f / 9f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF141926))
                .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color(0x22FFFFFF), RoundedCornerShape(16.dp))
        ) {
            if (item.posterUrl.isNotBlank()) {
                AsyncImage(item.posterUrl, contentDescription = null, contentScale = if (poster) ContentScale.Crop else ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(if (poster) 0.dp else 10.dp))
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(item.title.take(2).uppercase(), color = Color(0xFF93C5FD), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            }
            if (item.qualityBadge.isNotBlank()) {
                Box(Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xCC065F46)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(item.qualityBadge, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, color = if (focused) Color.White else Color(0xFFE5E7EB), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${item.country} • ${item.subCategory}", color = Color(0xFF9CA3AF), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Badge(text: String, bg: Color) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SeriesScreen(series: TvMediaItem, account: TvAccount?, userAgent: String, isFavorite: Boolean, onToggleFav: () -> Unit, onPlayEp: (TvEpisode) -> Unit, onBack: () -> Unit) {
    var seasons by remember { mutableStateOf>>(emptyMap()) }
    var selSeason by remember { mutableStateOf(1) }
    LaunchedEffect(series.id) {
        val loaded = TvEngine.loadEpisodes(account, series.seriesId, userAgent)
        seasons = loaded
        selSeason = loaded.keys.firstOrNull() ?: 1
    }
    val eps = seasons[selSeason].orEmpty()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (series.posterUrl.isNotBlank()) AsyncImage(series.posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.width(130.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(series.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text("${series.subCategory} • ${series.year}", color = Color(0xFF93C5FD), fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onToggleFav) { Text(if (isFavorite) "I Favoriter" else "Spara", color = Color.White) }
                    OutlinedButton(onClick = onBack) { Text("Tillbaka", color = Color.White) }
                }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(seasons.keys.sorted()) { s ->
                Button(onClick = { selSeason = s }, colors = ButtonDefaults.buttonColors(containerColor = if (selSeason == s) Color.White else Color(0x22FFFFFF), contentColor = if (selSeason == s) Color.Black else Color.White)) {
                    Text("Säsong $s")
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(eps, key = { it.id }) { ep ->
                Surface(color = Color(0x18FFFFFF), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().focusable().clickable { onPlayEp(ep) }) {
                    Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Avsnitt ${ep.episodeNum}: ${ep.title}", color = Color.White, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(catalog: TvCatalog, onSelect: (TvMediaItem) -> Unit) {
    var q by remember { mutableStateOf("") }
    val all = catalog.liveChannels + catalog.movies + catalog.series
    val hits = remember(q, all) {
        val t = q.trim().lowercase()
        if (t.length < 2) emptyList() else all.filter { it.title.lowercase().contains(t) || it.country.lowercase().contains(t) || it.subCategory.lowercase().contains(t) }.take(80)
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(value = q, onValueChange = { q = it }, label = { Text("Sök kanal, land, film eller serie") }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White), modifier = Modifier.fillMaxWidth(0.6f))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(hits, key = { it.id }) { MediaCard(it, it.type != TvContentType.LIVE) { onSelect(it) } }
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: TvSettings,
    accounts: List<TvAccount>,
    activeId: String,
    onSaveSettings: (TvSettings) -> Unit,
    onAddAccount: (TvAccount) -> Unit,
    onSelectAccount: (String) -> Unit,
    onDeleteAccount: (String) -> Unit
) {
    var tab by remember { mutableStateOf(SettingsTab.ACCOUNTS) }
    var name by remember { mutableStateOf("Mitt IPTV") }
    var m3u by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 42.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(modifier = Modifier.width(230.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsTab.entries.forEach { t ->
                Button(onClick = { tab = t }, colors = ButtonDefaults.buttonColors(containerColor = if (tab == t) Color.White else Color(0x18FFFFFF), contentColor = if (tab == t) Color.Black else Color.White), modifier = Modifier.fillMaxWidth()) {
                    Text(t.label, fontWeight = FontWeight.Bold)
                }
            }
        }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 36.dp)) {
            when (tab) {
                SettingsTab.ACCOUNTS -> {
                    item {
                        accounts.forEach { acc ->
                            Surface(color = if (acc.id == activeId) Color(0x332563EB) else Color(0x16FFFFFF), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(acc.name, color = Color.White, fontWeight = FontWeight.Bold)
                                        Text("${acc.mode} • ${if (acc.mode == "XTREAM") acc.server else acc.m3uUrl}", color = Color(0xFF9CA3AF), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { onSelectAccount(acc.id) }) { Icon(Icons.Default.Check, contentDescription = null); Spacer(Modifier.width(4.dp)); Text(if (acc.id == activeId) "Aktivt" else "Välj") }
                                        OutlinedButton(onClick = { onDeleteAccount(acc.id) }) { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFF87171)) }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Kontonamn") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = m3u, onValueChange = { m3u = it }, label = { Text("M3U-URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { if (m3u.isNotBlank()) { onAddAccount(TvAccount("acc_${System.currentTimeMillis()}", name.ifBlank { "M3U" }, "M3U", m3uUrl = m3u.trim())); m3u = "" } }) { Text("Spara M3U") }
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(value = server, onValueChange = { server = it }, label = { Text("Xtream Server-URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Användarnamn") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Lösenord") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { if (server.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) { onAddAccount(TvAccount("acc_${System.currentTimeMillis()}", name.ifBlank { "Xtream" }, "XTREAM", server = server.trim(), username = user.trim(), password = pass.trim())); server = ""; user = ""; pass = "" } }) { Text("Spara Xtream") }
                    }
                }
                SettingsTab.PLAYBACK -> {
                    item {
                        ToggleRow("Stor buffert", "Bättre stabilitet för Live TV", settings.largeBuffer) { onSaveSettings(settings.copy(largeBuffer = it)) }
                        ToggleRow("Hårdvaruavkodning", "Använder TV:ns hårdvaruavkodare", settings.hardwareDecoding) { onSaveSettings(settings.copy(hardwareDecoding = it)) }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("IPTVSmartersPro", "VLC/3.0.20", "TiviMate/4.7.0").forEach { ua ->
                                Button(onClick = { onSaveSettings(settings.copy(userAgent = ua)) }, colors = ButtonDefaults.buttonColors(containerColor = if (settings.userAgent == ua) Color.White else Color(0x22FFFFFF), contentColor = if (settings.userAgent == ua) Color.Black else Color.White)) { Text(ua) }
                            }
                        }
                    }
                }
                SettingsTab.CONTENT -> {
                    item {
                        ToggleRow("Dölj vuxeninnehåll", "Filtrerar bort 18+ / XXX", settings.hideAdult) { onSaveSettings(settings.copy(hideAdult = it)) }
                        ToggleRow("PIN-skydd", "Kräver PIN vid låst innehåll", settings.parentalEnabled) { onSaveSettings(settings.copy(parentalEnabled = it)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(sub, color = Color(0xFF9CA3AF), fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color.White))
    }
}

@Composable
private fun PlayerViewScreen(
    item: TvMediaItem,
    liveChannels: List<TvMediaItem>,
    settings: TvSettings,
    isFavorite: Boolean,
    onToggleFav: () -> Unit,
    onZap: (TvMediaItem) -> Unit,
    onExit: () -> Unit
) {
    val ctx = LocalContext.current
    var overlay by remember { mutableStateOf(true) }
    var aspect by remember { mutableStateOf(settings.aspectMode) }
    var info by remember { mutableStateOf(TvStreamInfo(resolution = item.qualityBadge)) }
    var audios by remember { mutableStateOf<List<TvTrackOption>>(emptyList()) }
    var subs by remember { mutableStateOf<List<TvTrackOption>>(emptyList()) }
    var menu by remember { mutableStateOf<String?>(null) }

    val player = remember(item.streamUrl, settings.largeBuffer, settings.hardwareDecoding) {
        val ds = OkHttpDataSource.Factory(TvEngine.buildClient(settings.userAgent))
        val lc = DefaultLoadControl.Builder().setBufferDurationsMs(15000, 60000, 2500, 5000).build()
        val rf = DefaultRenderersFactory(ctx).setExtensionRendererMode(
            if (settings.hardwareDecoding) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
        )
        ExoPlayer.Builder(ctx).setRenderersFactory(rf).setMediaSourceFactory(DefaultMediaSourceFactory(ds)).setLoadControl(lc).build().apply {
            setMediaItem(ExoMediaItem.fromUri(item.streamUrl))
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                var res = item.qualityBadge
                var fps = ""
                var vc = ""
                var aLang = ""
                var sLang = "Av"
                val aList = mutableListOf<TvTrackOption>()
                val sList = mutableListOf<TvTrackOption>()
                for (gi in tracks.groups.indices) {
                    val g = tracks.groups[gi]
                    for (ti in 0 until g.length) {
                        val fmt = g.getTrackFormat(ti)
                        val sel = g.isTrackSelected(ti)
                        when (g.type) {
                            C.TRACK_TYPE_VIDEO -> if (sel && fmt.height > 0) {
                                res = when {
                                    fmt.height >= 2160 -> "4K (${fmt.width}×${fmt.height})"
                                    fmt.height >= 1080 -> "1080p (${fmt.width}×${fmt.height})"
                                    fmt.height >= 720 -> "720p (${fmt.width}×${fmt.height})"
                                    else -> "${fmt.height}p"
                                }
                                if (fmt.frameRate > 0f) fps = "${fmt.frameRate.toInt()} fps"
                                vc = fmt.sampleMimeType?.substringAfter("/")?.uppercase().orEmpty()
                            }
                            C.TRACK_TYPE_AUDIO -> {
                                val lbl = langLabel(fmt.language, fmt.label, "Ljud ${aList.size + 1}")
                                aList.add(TvTrackOption(lbl, gi, ti, sel))
                                if (sel) aLang = lbl
                            }
                            C.TRACK_TYPE_TEXT -> {
                                val lbl = langLabel(fmt.language, fmt.label, "Undertext ${sList.size + 1}")
                                sList.add(TvTrackOption(lbl, gi, ti, sel))
                                if (sel) sLang = lbl
                            }
                        }
                    }
                }
                audios = aList; subs = sList
                info = TvStreamInfo(res.ifBlank { "Auto" }, fps, vc, aLang.ifBlank { "Standard" }, sLang)
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }

    LaunchedEffect(overlay, menu, item.id) { if (overlay && menu == null) { delay(6500); overlay = false } }

    fun zap(delta: Int) {
        if (item.type == TvContentType.LIVE && liveChannels.isNotEmpty()) {
            val i = liveChannels.indexOfFirst { it.id == item.id }
            if (i != -1) onZap(liveChannels[(i + delta + liveChannels.size) % liveChannels.size])
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black).focusable().onPreviewKeyEvent { ev ->
            if (ev.type == KeyEventType.KeyDown) {
                when (ev.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { if (menu != null) menu = null else overlay = !overlay; true }
                    KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_UP -> { if (menu == null) zap(1); true }
                    KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> { if (menu == null) zap(-1); true }
                    KeyEvent.KEYCODE_BACK -> { if (menu != null) menu = null else onExit(); true }
                    else -> false
                }
            } else false
        }
    ) {
        AndroidView(
            factory = { c -> PlayerView(c).apply { this.player = player; useController = false; resizeMode = if (aspect == 1) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT } },
            update = { v -> v.player = player; v.resizeMode = if (aspect == 1) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT },
            modifier = Modifier.fillMaxSize()
        )

        if (menu != null) {
            Surface(color = Color(0xEB0E1320), shape = RoundedCornerShape(20.dp), modifier = Modifier.align(Alignment.Center).width(400.dp)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (menu == "AUDIO") "Välj ljudspråk" else "Välj undertext", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    if (menu == "SUB") {
                        Button(onClick = { player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build(); menu = null }, modifier = Modifier.fillMaxWidth()) { Text("Av") }
                    }
                    val list = if (menu == "AUDIO") audios else subs
                    if (list.isEmpty()) Text("Inga extra spår hittades.", color = Color(0xFF9CA3AF))
                    list.forEach { opt ->
                        Button(onClick = {
                            val grp = player.currentTracks.groups.getOrNull(opt.groupIndex)
                            if (grp != null) {
                                val b = player.trackSelectionParameters.buildUpon()
                                if (menu == "SUB") b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                player.trackSelectionParameters = b.setOverrideForType(TrackSelectionOverride(grp.mediaTrackGroup, opt.trackIndex)).build()
                            }
                            menu = null
                        }, modifier = Modifier.fillMaxWidth()) { Text(opt.label) }
                    }
                    OutlinedButton(onClick = { menu = null }, modifier = Modifier.fillMaxWidth()) { Text("Stäng", color = Color.White) }
                }
            }
        }

        AnimatedVisibility(visible = overlay, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Surface(color = Color(0xD90A0E17), shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 36.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${item.country} • ${item.subCategory}", color = Color(0xFF93C5FD), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(item.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Badge("Upplösning: ${info.resolution}", Color(0xFF065F46))
                            if (info.fps.isNotBlank()) Badge(info.fps, Color(0xFF1E3A8A))
                            if (info.videoCodec.isNotBlank()) Badge(info.videoCodec, Color(0xFF374151))
                            Badge("Ljud: ${info.activeAudioLang}", Color(0xFF312E81))
                            Badge("Text: ${info.activeSubtitleLang}", Color(0xFF4C1D95))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { if (player.isPlaying) player.pause() else player.play() }) { Text(if (player.isPlaying) "Pausa" else "Spela") }
                        OutlinedButton(onClick = { menu = "AUDIO" }) { Icon(Icons.Default.Audiotrack, contentDescription = null, tint = Color.White); Spacer(Modifier.width(6.dp)); Text("Språk (${audios.size})", color = Color.White) }
                        OutlinedButton(onClick = { menu = "SUB" }) { Icon(Icons.Default.Subtitles, contentDescription = null, tint = Color.White); Spacer(Modifier.width(6.dp)); Text("Undertext (${subs.size})", color = Color.White) }
                        OutlinedButton(onClick = { aspect = if (aspect == 0) 1 else 0 }) { Text(if (aspect == 0) "Zoom" else "Passa", color = Color.White) }
                        OutlinedButton(onClick = onToggleFav) { Text(if (isFavorite) "★ Favorit" else "☆ Favorit", color = Color.White) }
                        OutlinedButton(onClick = onExit) { Text("Stäng", color = Color.White) }
                    }
                }
            }
        }
    }
}

private fun langLabel(code: String?, label: String?, fallback: String): String {
    if (!label.isNullOrBlank()) return label
    if (!code.isNullOrBlank() && code != "und") {
        val d = Locale.forLanguageTag(code).getDisplayLanguage(Locale("sv"))
        if (d.isNotBlank()) return d.replaceFirstChar { it.uppercase() }
        return code.uppercase()
    }
    return fallback
}

@Composable
private fun PinDialog(expected: String, onOk: () -> Unit, onCancel: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color(0xD9000000)), contentAlignment = Alignment.Center) {
        Surface(color = Color(0xFF141926), shape = RoundedCornerShape(20.dp), modifier = Modifier.width(360.dp)) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White)
                Text("Ange PIN-kod", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = code, onValueChange = { err = false; if (it.length <= 4) code = it.filter(Char::isDigit) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                if (err) Text("Fel PIN-kod", color = Color(0xFFF87171), fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onCancel) { Text("Avbryt", color = Color.White) }
                    Button(onClick = { if (code == expected) onOk() else err = true }) { Text("Lås upp") }
                }
            }
        }
    }
}

private fun isAdultItem(item: TvMediaItem): Boolean {
    val t = "${item.title} ${item.category}".lowercase()
    return t.contains("xxx") || t.contains("adult") || t.contains("18+") || t.contains("porn")
}

private fun prefs(ctx: Context) = ctx.getSharedPreferences("ulf_tv_prefs", Context.MODE_PRIVATE)
private fun loadSettings(ctx: Context): TvSettings {
    val p = prefs(ctx)
    return TvSettings(
        userAgent = p.getString("ua", "IPTVSmartersPro") ?: "IPTVSmartersPro",
        largeBuffer = p.getBoolean("buf", true),
        hardwareDecoding = p.getBoolean("hw", true),
        hideAdult = p.getBoolean("adult", true),
        parentalPin = p.getString("pin", "0000") ?: "0000",
        parentalEnabled = p.getBoolean("pin_on", false),
        aspectMode = p.getInt("aspect", 0)
    )
}
private fun saveSettings(ctx: Context, s: TvSettings) {
    prefs(ctx).edit().putString("ua", s.userAgent).putBoolean("buf", s.largeBuffer).putBoolean("hw", s.hardwareDecoding).putBoolean("adult", s.hideAdult).putString("pin", s.parentalPin).putBoolean("pin_on", s.parentalEnabled).putInt("aspect", s.aspectMode).apply()
}
private fun loadAccounts(ctx: Context): List<TvAccount> = try {
    val arr = JSONArray(prefs(ctx).getString("accs", "[]") ?: "[]")
    (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        TvAccount(o.optString("id"), o.optString("name"), o.optString("mode"), o.optString("m3u"), o.optString("srv"), o.optString("usr"), o.optString("pwd"))
    }
} catch (_: Exception) { emptyList() }
private fun saveAccounts(ctx: Context, list: List<TvAccount>) {
    val arr = JSONArray()
    list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("mode", it.mode).put("m3u", it.m3uUrl).put("srv", it.server).put("usr", it.username).put("pwd", it.password)) }
    prefs(ctx).edit().putString("accs", arr.toString()).apply()
}
private fun loadActiveAccountId(ctx: Context) = prefs(ctx).getString("act_acc", "").orEmpty()
private fun saveActiveAccountId(ctx: Context, id: String) = prefs(ctx).edit().putString("act_acc", id).apply()
private fun loadFavorites(ctx: Context): Set<String> = prefs(ctx).getStringSet("favs", emptySet())?.toSet() ?: emptySet()
private fun saveFavorites(ctx: Context, favs: Set<String>) = prefs(ctx).edit().putStringSet("favs", favs).apply()
