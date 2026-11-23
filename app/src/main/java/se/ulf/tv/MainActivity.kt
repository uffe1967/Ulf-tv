package se.ulf.tv

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private enum class TvNavSection(val label: String, val icon: ImageVector) {
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

private data class TvTrackChoice(
    val label: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val isSelected: Boolean
)

private data class TvMediaDetails(
    val resolution: String = "",
    val fps: String = "",
    val videoCodec: String = "",
    val audioLang: String = "",
    val subtitleLang: String = "Av"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { UlfTvRoot() }
    }
}

@Composable
private fun UlfTvRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(context) { IptvRepository(context) }

    var settings by remember { mutableStateOf(repo.loadSettings()) }
    var accounts by remember { mutableStateOf(repo.loadAccounts()) }
    var activeAccountId by remember { mutableStateOf(repo.loadActiveAccountId()) }
    var favorites by remember { mutableStateOf(repo.loadFavorites()) }
    var catalog by remember { mutableStateOf(repo.demoCatalog()) }
    var statusText by remember { mutableStateOf("Demoläge") }
    var isLoading by remember { mutableStateOf(false) }

    var section by remember { mutableStateOf(TvNavSection.HOME) }
    var playingItem by remember { mutableStateOf<MediaItem?>(null) }
    var selectedSeries by remember { mutableStateOf<MediaItem?>(null) }
    var pinUnlocked by remember { mutableStateOf(false) }
    var pendingItem by remember { mutableStateOf<MediaItem?>(null) }

    val activeAccount = accounts.firstOrNull { it.id == activeAccountId }

    fun syncCatalog(acc: IptvAccount?, st: AppSettings) {
        scope.launch {
            isLoading = true
            catalog = repo.loadCatalog(acc, st) { statusText = it }
            val liveCount = catalog.liveChannels.size
            val movieCount = catalog.movies.size
            val seriesCount = catalog.series.size
            statusText = if (acc == null) {
                "Demoläge • $liveCount live"
            } else {
                "${acc.name}: $liveCount live • $movieCount filmer • $seriesCount serier"
            }
            isLoading = false
        }
    }

    LaunchedEffect(activeAccountId, settings.userAgent, settings.hideAdult) {
        syncCatalog(activeAccount, settings)
    }

    fun toggleFavorite(id: String) {
        favorites = if (favorites.contains(id)) favorites - id else favorites + id
        repo.saveFavorites(favorites)
    }

    fun openMedia(item: MediaItem) {
        when {
            item.type == ContentType.SERIES -> selectedSeries = item
            settings.parentalEnabled && !pinUnlocked && repo.isAdultItem(item) -> pendingItem = item
            else -> playingItem = item
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A0D14), Color(0xFF06070B), Color(0xFF040508))
                )
            )
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
                    when {
                        playingItem != null -> { playingItem = null; true }
                        selectedSeries != null -> { selectedSeries = null; true }
                        section != TvNavSection.HOME -> { section = TvNavSection.HOME; true }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        when {
            playingItem != null -> PlayerScreen(
                item = playingItem!!,
                liveChannels = catalog.liveChannels,
                settings = settings,
                repo = repo,
                isFavorite = favorites.contains(playingItem!!.id),
                onToggleFavorite = { toggleFavorite(playingItem!!.id) },
                onSwitchChannel = { playingItem = it },
                onClose = { playingItem = null }
            )

            selectedSeries != null -> SeriesDetailScreen(
                series = selectedSeries!!,
                account = activeAccount,
                settings = settings,
                repo = repo,
                isFavorite = favorites.contains(selectedSeries!!.id),
                onToggleFavorite = { toggleFavorite(selectedSeries!!.id) },
                onPlayEpisode = { ep ->
                    playingItem = selectedSeries!!.copy(
                        id = "ep_${ep.id}",
                        title = "${selectedSeries!!.title} • S${ep.season}E${ep.episodeNum} - ${ep.title}",
                        streamUrl = ep.streamUrl
                    )
                },
                onBack = { selectedSeries = null }
            )

            else -> Column(modifier = Modifier.fillMaxSize()) {
                HeaderBar(
                    current = section,
                    status = statusText,
                    loading = isLoading,
                    onSelectSection = { section = it },
                    onRefresh = { syncCatalog(activeAccount, settings) }
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (section) {
                        TvNavSection.HOME -> HomeView(
                            catalog = catalog,
                            favorites = favorites,
                            repo = repo,
                            onSelectItem = ::openMedia,
                            onSeeAll = { section = it }
                        )
                        TvNavSection.LIVE -> LiveChannelsView(
                            channels = catalog.liveChannels,
                            favorites = favorites,
                            repo = repo,
                            onToggleFavorite = ::toggleFavorite,
                            onSelectItem = ::openMedia
                        )
                        TvNavSection.MOVIES -> VodGridView(
                            title = "Filmer",
                            items = catalog.movies,
                            favorites = favorites,
                            repo = repo,
                            onToggleFavorite = ::toggleFavorite,
                            onSelectItem = ::openMedia
                        )
                        TvNavSection.SERIES -> VodGridView(
                            title = "Serier",
                            items = catalog.series,
                            favorites = favorites,
                            repo = repo,
                            onToggleFavorite = ::toggleFavorite,
                            onSelectItem = ::openMedia
                        )
                        TvNavSection.FAVORITES -> {
                            val all = catalog.liveChannels + catalog.movies + catalog.series
                            VodGridView(
                                title = "Favoriter",
                                items = all.filter { favorites.contains(it.id) },
                                favorites = favorites,
                                repo = repo,
                                onToggleFavorite = ::toggleFavorite,
                                onSelectItem = ::openMedia
                            )
                        }
                        TvNavSection.SEARCH -> SearchView(
                            catalog = catalog,
                            repo = repo,
                            onSelectItem = ::openMedia
                        )
                        TvNavSection.SETTINGS -> SettingsView(
                            settings = settings,
                            accounts = accounts,
                            activeAccountId = activeAccountId,
                            onSaveSettings = {
                                settings = it
                                repo.saveSettings(it)
                            },
                            onAddAccount = { acc ->
                                val updated = accounts + acc
                                accounts = updated
                                repo.saveAccounts(updated)
                                activeAccountId = acc.id
                                repo.saveActiveAccountId(acc.id)
                                syncCatalog(acc, settings)
                                section = TvNavSection.HOME
                            },
                            onSelectAccount = { id ->
                                activeAccountId = id
                                repo.saveActiveAccountId(id)
                                syncCatalog(accounts.firstOrNull { it.id == id }, settings)
                            },
                            onDeleteAccount = { id ->
                                val updated = accounts.filterNot { it.id == id }
                                accounts = updated
                                repo.saveAccounts(updated)
                                if (activeAccountId == id) {
                                    val nextId = updated.firstOrNull()?.id.orEmpty()
                                    activeAccountId = nextId
                                    repo.saveActiveAccountId(nextId)
                                    syncCatalog(updated.firstOrNull(), settings)
                                }
                            }
                        )
                    }
                }
            }
        }

        if (pendingItem != null) {
            PinPrompt(
                expectedPin = settings.parentalPin,
                onSuccess = {
                    pinUnlocked = true
                    val target = pendingItem
                    pendingItem = null
                    target?.let { playingItem = it }
                },
                onDismiss = { pendingItem = null }
            )
        }
    }
}

@Composable
private fun HeaderBar(
    current: TvNavSection,
    status: String,
    loading: Boolean,
    onSelectSection: (TvNavSection) -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 42.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text("U", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text("ULF TV", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0x26FFFFFF))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TvNavSection.entries.forEach { tab ->
                val selected = current == tab
                var focused by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "nav_scale")
                Box(
                    modifier = Modifier
                        .scale(scale)
                        .clip(RoundedCornerShape(50))
                        .background(
                            when {
                                selected -> Color.White
                                focused -> Color(0x44FFFFFF)
                                else -> Color.Transparent
                            }
                        )
                        .onFocusChanged { focused = it.isFocused }
                        .focusable()
                        .clickable { onSelectSection(tab) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            tab.icon,
                            contentDescription = null,
                            tint = if (selected) Color.Black else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            tab.label,
                            color = if (selected) Color.Black else Color.White,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (loading) "Synkar..." else status,
                color = Color(0xFF9CA3AF),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(210.dp)
            )
            var refreshFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (refreshFocused) Color.White else Color(0x22FFFFFF))
                    .onFocusChanged { refreshFocused = it.isFocused }
                    .focusable()
                    .clickable { onRefresh() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    tint = if (refreshFocused) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeView(
    catalog: IptvCatalog,
    favorites: Set<String>,
    repo: IptvRepository,
    onSelectItem: (MediaItem) -> Unit,
    onSeeAll: (TvNavSection) -> Unit
) {
    val hero = catalog.movies.firstOrNull() ?: catalog.series.firstOrNull() ?: catalog.liveChannels.firstOrNull()
    val favItems = (catalog.liveChannels + catalog.movies + catalog.series).filter { favorites.contains(it.id) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        if (hero != null) {
            item {
                val (country, subCat) = repo.parseCountryAndSub(hero.category, hero.title)
                val quality = repo.detectQuality(hero.title)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(horizontal = 42.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF131826))
                ) {
                    if (hero.backdropUrl.isNotBlank()) {
                        AsyncImage(
                            hero.backdropUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xF206080E), Color(0xB806080E), Color(0x3306080E))
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(0.65f)
                            .padding(30.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoTag(country, Color(0xFF1E3A8A))
                            InfoTag(subCat, Color(0xFF374151))
                            if (quality.isNotBlank()) InfoTag(quality, Color(0xFF065F46))
                        }
                        Text(
                            hero.title,
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Button(
                            onClick = { onSelectItem(hero) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (hero.type == ContentType.SERIES) "Visa säsonger" else "Spela nu",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (favItems.isNotEmpty()) {
            item {
                MediaShelf("Favoriter", favItems.take(24), false, repo, onSelectItem) { onSeeAll(TvNavSection.FAVORITES) }
            }
        }
        if (catalog.liveChannels.isNotEmpty()) {
            item {
                MediaShelf(
                    "Live TV • Länder (${catalog.liveChannels.size})",
                    catalog.liveChannels.take(30),
                    false,
                    repo,
                    onSelectItem
                ) { onSeeAll(TvNavSection.LIVE) }
            }
        }
        if (catalog.movies.isNotEmpty()) {
            item {
                MediaShelf(
                    "Senaste Filmer (${catalog.movies.size})",
                    catalog.movies.sortedByDescending { it.addedTimestamp }.take(30),
                    true,
                    repo,
                    onSelectItem
                ) { onSeeAll(TvNavSection.MOVIES) }
            }
        }
        if (catalog.series.isNotEmpty()) {
            item {
                MediaShelf(
                    "Senaste Serier (${catalog.series.size})",
                    catalog.series.sortedByDescending { it.addedTimestamp }.take(30),
                    true,
                    repo,
                    onSelectItem
                ) { onSeeAll(TvNavSection.SERIES) }
            }
        }
    }
}

@Composable
private fun LiveChannelsView(
    channels: List<MediaItem>,
    favorites: Set<String>,
    repo: IptvRepository,
    onToggleFavorite: (String) -> Unit,
    onSelectItem: (MediaItem) -> Unit
) {
    val countries = remember(channels) {
        val grouped = channels.groupBy { repo.parseCountryAndSub(it.category, it.title).first }
        listOf("Alla länder") + grouped.keys.sortedByDescending { grouped[it]?.size ?: 0 }
    }
    var selectedCountry by remember { mutableStateOf("Alla länder") }

    val countryChannels = remember(channels, selectedCountry) {
        if (selectedCountry == "Alla länder") {
            channels
        } else {
            channels.filter { repo.parseCountryAndSub(it.category, it.title).first == selectedCountry }
        }
    }

    val subCategories = remember(countryChannels) {
        val preferredOrder = listOf("Sport", "Film & Serier", "Nyheter", "Barn", "Dokumentär", "Musik", "Allmänt")
        val present = countryChannels.map { repo.parseCountryAndSub(it.category, it.title).second }.toSet()
        listOf("Alla") + preferredOrder.filter { present.contains(it) }
    }
    var selectedSub by remember { mutableStateOf("Alla") }
    LaunchedEffect(selectedCountry) { selectedSub = "Alla" }

    val visibleChannels = remember(countryChannels, selectedSub) {
        if (selectedSub == "Alla") {
            countryChannels
        } else {
            countryChannels.filter { repo.parseCountryAndSub(it.category, it.title).second == selectedSub }
        }
    }

    var focusedItem by remember { mutableStateOf(visibleChannels.firstOrNull()) }
    LaunchedEffect(visibleChannels) {
        if (visibleChannels.isNotEmpty() && !visibleChannels.contains(focusedItem)) {
            focusedItem = visibleChannels.first()
        }
    }

    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 42.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        LazyColumn(
            modifier = Modifier.width(230.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text(
                    "Länder",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(countries) { country ->
                val count = if (country == "Alla länder") {
                    channels.size
                } else {
                    channels.count { repo.parseCountryAndSub(it.category, it.title).first == country }
                }
                val isSelected = selectedCountry == country
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Color(0xFF2563EB) else Color(0x12FFFFFF))
                        .focusable()
                        .clickable { selectedCountry = country }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            country,
                            color = Color.White,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text("$count", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                    }
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(subCategories) { sub ->
                    val active = selectedSub == sub
                    Button(
                        onClick = { selectedSub = sub },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (active) Color.White else Color(0x20FFFFFF),
                            contentColor = if (active) Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(sub, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            focusedItem?.let { info ->
                val (cName, sName) = repo.parseCountryAndSub(info.category, info.title)
                val qBadge = repo.detectQuality(info.title)
                Surface(
                    color = Color(0x18FFFFFF),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0x26FFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    info.title,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                InfoTag("$cName • $sName", Color(0xFF1E3A8A))
                                if (qBadge.isNotBlank()) InfoTag(qBadge, Color(0xFF065F46))
                            }
                            Text(
                                "Kategori: ${info.category} • Språk & undertext i spelaren",
                                color = Color(0xFF9CA3AF),
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                        OutlinedButton(
                            onClick = { onToggleFavorite(info.id) },
                            border = BorderStroke(1.dp, Color(0x55FFFFFF))
                        ) {
                            Icon(
                                if (favorites.contains(info.id)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = Color(0xFFF87171)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (favorites.contains(info.id)) "Favorit" else "Spara", color = Color.White)
                        }
                    }
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(visibleChannels.take(250), key = { it.id }) { channel ->
                    Box(Modifier.onFocusChanged { if (it.isFocused) focusedItem = channel }) {
                        MediaCardTile(channel, false, repo) { onSelectItem(channel) }
                    }
                }
            }
        }
    }
}

@Composable
private fun VodGridView(
    title: String,
    items: List<MediaItem>,
    favorites: Set<String>,
    repo: IptvRepository,
    onToggleFavorite: (String) -> Unit,
    onSelectItem: (MediaItem) -> Unit
) {
    val genres = remember(items) {
        listOf("Senast tillagda", "Nysläppta (2025–2026)", "Topprankade", "Alla") +
            items.map { repo.normalizeGenre(it.category, it.title) }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
    }
    var selectedGenre by remember { mutableStateOf("Senast tillagda") }

    val filtered = remember(items, selectedGenre) {
        when (selectedGenre) {
            "Senast tillagda" -> items.sortedByDescending { it.addedTimestamp }
            "Nysläppta (2025–2026)" -> items
                .filter { it.year == "2026" || it.year == "2025" }
                .ifEmpty { items.sortedByDescending { it.year } }
            "Topprankade" -> items.sortedByDescending { it.rating.toDoubleOrNull() ?: 0.0 }
            "Alla" -> items
            else -> items.filter { repo.normalizeGenre(it.category, it.title) == selectedGenre }
        }
    }

    var focusedItem by remember { mutableStateOf(filtered.firstOrNull()) }
    LaunchedEffect(filtered) {
        if (filtered.isNotEmpty() && !filtered.contains(focusedItem)) {
            focusedItem = filtered.first()
        }
    }

    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 42.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        LazyColumn(
            modifier = Modifier.width(240.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(genres) { genre ->
                val active = selectedGenre == genre
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) Color(0xFF2563EB) else Color(0x12FFFFFF))
                        .focusable()
                        .clickable { selectedGenre = genre }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        genre,
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            focusedItem?.let { info ->
                val gName = repo.normalizeGenre(info.category, info.title)
                val qBadge = repo.detectQuality(info.title)
                Surface(
                    color = Color(0x18FFFFFF),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0x26FFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    info.title,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (info.year.isNotBlank()) InfoTag(info.year, Color(0xFF1E3A8A))
                                if (qBadge.isNotBlank()) InfoTag(qBadge, Color(0xFF065F46))
                                if (info.rating.isNotBlank()) InfoTag("★ ${info.rating}", Color(0xFF78350F))
                            }
                            Text(
                                info.plot.ifBlank { "$gName • ${info.category}" },
                                color = Color(0xFF9CA3AF),
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = { onToggleFavorite(info.id) },
                            border = BorderStroke(1.dp, Color(0x55FFFFFF))
                        ) {
                            Icon(
                                if (favorites.contains(info.id)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = Color(0xFFF87171)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (favorites.contains(info.id)) "Favorit" else "Spara", color = Color.White)
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Inga titlar hittades.", color = Color(0xFF9CA3AF))
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filtered.take(250), key = { it.id }) { item ->
                        Box(Modifier.onFocusChanged { if (it.isFocused) focusedItem = item }) {
                            MediaCardTile(item, item.type != ContentType.LIVE, repo) { onSelectItem(item) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaShelf(
    title: String,
    items: List<MediaItem>,
    poster: Boolean,
    repo: IptvRepository,
    onSelectItem: (MediaItem) -> Unit,
    onSeeAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 42.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                "Visa alla ›",
                color = Color(0xFF9CA3AF),
                fontSize = 14.sp,
                modifier = Modifier.focusable().clickable { onSeeAll() }.padding(8.dp)
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 42.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items(items, key = { it.id }) { item ->
                MediaCardTile(item, poster, repo) { onSelectItem(item) }
            }
        }
    }
}

@Composable
private fun MediaCardTile(
    item: MediaItem,
    poster: Boolean,
    repo: IptvRepository,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "card_scale")
    val (country, sub) = repo.parseCountryAndSub(item.category, item.title)
    val quality = repo.detectQuality(item.title)

    Column(
        modifier = Modifier
            .width(if (poster) 150.dp else 230.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (poster) 2f / 3f else 16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF141926))
                .border(
                    if (focused) 3.dp else 1.dp,
                    if (focused) Color.White else Color(0x22FFFFFF),
                    RoundedCornerShape(16.dp)
                )
        ) {
            if (item.posterUrl.isNotBlank()) {
                AsyncImage(
                    item.posterUrl,
                    contentDescription = null,
                    contentScale = if (poster) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(if (poster) 0.dp else 10.dp)
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        item.title.take(2).uppercase(),
                        color = Color(0xFF93C5FD),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (quality.isNotBlank()) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC065F46))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(quality, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.title,
            color = if (focused) Color.White else Color(0xFFE5E7EB),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "$country • $sub",
            color = Color(0xFF9CA3AF),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InfoTag(text: String, bg: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SeriesDetailScreen(
    series: MediaItem,
    account: IptvAccount?,
    settings: AppSettings,
    repo: IptvRepository,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlayEpisode: (EpisodeItem) -> Unit,
    onBack: () -> Unit
) {
    var seasons by remember { mutableStateOf>>(emptyMap()) }
    var selectedSeason by remember { mutableStateOf(1) }

    LaunchedEffect(series.id) {
        val loaded = repo.loadEpisodes(account, series.seriesId, settings.userAgent)
        seasons = loaded
        selectedSeason = loaded.keys.firstOrNull() ?: 1
    }

    val episodes = seasons[selectedSeason].orEmpty()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (series.posterUrl.isNotBlank()) {
                AsyncImage(
                    series.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(130.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp))
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(series.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text("${series.category} • ${series.year}", color = Color(0xFF93C5FD), fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onToggleFavorite) {
                        Text(if (isFavorite) "I Favoriter" else "Spara", color = Color.White)
                    }
                    OutlinedButton(onClick = onBack) {
                        Text("Tillbaka", color = Color.White)
                    }
                }
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(seasons.keys.sorted()) { seasonNum ->
                val active = selectedSeason == seasonNum
                Button(
                    onClick = { selectedSeason = seasonNum },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (active) Color.White else Color(0x22FFFFFF),
                        contentColor = if (active) Color.Black else Color.White
                    )
                ) {
                    Text("Säsong $seasonNum")
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(episodes, key = { it.id }) { ep ->
                Surface(
                    color = Color(0x18FFFFFF),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().focusable().clickable { onPlayEpisode(ep) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Avsnitt ${ep.episodeNum}: ${ep.title}", color = Color.White, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchView(
    catalog: IptvCatalog,
    repo: IptvRepository,
    onSelectItem: (MediaItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val allItems = catalog.liveChannels + catalog.movies + catalog.series
    val matches = remember(query, allItems) {
        val term = query.trim().lowercase()
        if (term.length < 2) {
            emptyList()
        } else {
            allItems.filter {
                it.title.lowercase().contains(term) || it.category.lowercase().contains(term)
            }.take(80)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Sök kanal, film eller serie") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth(0.6f)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(matches, key = { it.id }) { item ->
                MediaCardTile(item, item.type != ContentType.LIVE, repo) { onSelectItem(item) }
            }
        }
    }
}

@Composable
private fun SettingsView(
    settings: AppSettings,
    accounts: List<IptvAccount>,
    activeAccountId: String,
    onSaveSettings: (AppSettings) -> Unit,
    onAddAccount: (IptvAccount) -> Unit,
    onSelectAccount: (String) -> Unit,
    onDeleteAccount: (String) -> Unit
) {
    var tab by remember { mutableStateOf(SettingsTab.ACCOUNTS) }
    var name by remember { mutableStateOf("Mitt IPTV") }
    var m3uUrl by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 42.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Column(modifier = Modifier.width(230.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsTab.entries.forEach { t ->
                val active = tab == t
                Button(
                    onClick = { tab = t },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (active) Color.White else Color(0x18FFFFFF),
                        contentColor = if (active) Color.Black else Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(t.label, fontWeight = FontWeight.Bold)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 36.dp)
        ) {
            when (tab) {
                SettingsTab.ACCOUNTS -> {
                    item {
                        accounts.forEach { acc ->
                            val isActive = acc.id == activeAccountId
                            Surface(
                                color = if (isActive) Color(0x332563EB) else Color(0x16FFFFFF),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(acc.name, color = Color.White, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${acc.mode.name} • ${if (acc.mode == AccountMode.XTREAM) acc.server else acc.m3uUrl}",
                                            color = Color(0xFF9CA3AF),
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { onSelectAccount(acc.id) }) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (isActive) "Aktivt" else "Välj")
                                        }
                                        OutlinedButton(onClick = { onDeleteAccount(acc.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFF87171))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Kontonamn") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = m3uUrl,
                            onValueChange = { m3uUrl = it },
                            label = { Text("M3U-URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            if (m3uUrl.isNotBlank()) {
                                onAddAccount(
                                    IptvAccount(
                                        id = "acc_${System.currentTimeMillis()}",
                                        name = name.ifBlank { "M3U" },
                                        mode = AccountMode.M3U,
                                        m3uUrl = m3uUrl.trim()
                                    )
                                )
                                m3uUrl = ""
                            }
                        }) {
                            Text("Spara M3U")
                        }
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = server,
                            onValueChange = { server = it },
                            label = { Text("Xtream Server-URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("Användarnamn") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Lösenord") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            if (server.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                                onAddAccount(
                                    IptvAccount(
                                        id = "acc_${System.currentTimeMillis()}",
                                        name = name.ifBlank { "Xtream" },
                                        mode = AccountMode.XTREAM,
                                        server = server.trim(),
                                        username = username.trim(),
                                        password = password.trim()
                                    )
                                )
                                server = ""
                                username = ""
                                password = ""
                            }
                        }) {
                            Text("Spara Xtream")
                        }
                    }
                }

                SettingsTab.PLAYBACK -> {
                    item {
                        SettingToggle("Stor buffert", "Bättre stabilitet för Live TV", settings.largeBuffer) {
                            onSaveSettings(settings.copy(largeBuffer = it))
                        }
                        SettingToggle("Hårdvaruavkodning", "Använder TV:ns hårdvaruavkodare", settings.hardwareDecoding) {
                            onSaveSettings(settings.copy(hardwareDecoding = it))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("IPTVSmartersPro", "VLC/3.0.20", "TiviMate/4.7.0").forEach { ua ->
                                val active = settings.userAgent == ua
                                Button(
                                    onClick = { onSaveSettings(settings.copy(userAgent = ua)) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (active) Color.White else Color(0x22FFFFFF),
                                        contentColor = if (active) Color.Black else Color.White
                                    )
                                ) {
                                    Text(ua)
                                }
                            }
                        }
                    }
                }

                SettingsTab.CONTENT -> {
                    item {
                        SettingToggle("Dölj vuxeninnehåll", "Filtrerar bort 18+ / XXX", settings.hideAdult) {
                            onSaveSettings(settings.copy(hideAdult = it))
                        }
                        SettingToggle("PIN-skydd", "Kräver PIN vid låst innehåll", settings.parentalEnabled) {
                            onSaveSettings(settings.copy(parentalEnabled = it))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xFF9CA3AF), fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color.White)
        )
    }
}

@Composable
private fun PlayerScreen(
    item: MediaItem,
    liveChannels: List<MediaItem>,
    settings: AppSettings,
    repo: IptvRepository,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onSwitchChannel: (MediaItem) -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    var overlayVisible by remember { mutableStateOf(true) }
    var aspectMode by remember { mutableStateOf(settings.aspectMode) }
    var details by remember { mutableStateOf(TvMediaDetails(resolution = repo.detectQuality(item.title))) }
    var audioTracks by remember { mutableStateOf<List<TvTrackChoice>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<TvTrackChoice>>(emptyList()) }
    var activeMenu by remember { mutableStateOf<String?>(null) }

    val exoPlayer = remember(item.streamUrl, settings.largeBuffer, settings.hardwareDecoding, settings.userAgent) {
        val dataSourceFactory = OkHttpDataSource.Factory(repo.buildClient(settings.userAgent))
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 60000, 2500, 5000)
            .build()
        val renderersFactory = DefaultRenderersFactory(ctx).setExtensionRendererMode(
            if (settings.hardwareDecoding) {
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            } else {
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            }
        )
        ExoPlayer.Builder(ctx)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .build()
            .apply {
                setMediaItem(ExoMediaItem.fromUri(item.streamUrl))
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                var res = repo.detectQuality(item.title)
                var fps = ""
                var codec = ""
                var aLang = ""
                var sLang = "Av"
                val aChoices = mutableListOf<TvTrackChoice>()
                val sChoices = mutableListOf<TvTrackChoice>()

                for (gi in tracks.groups.indices) {
                    val group = tracks.groups[gi]
                    for (ti in 0 until group.length) {
                        val fmt = group.getTrackFormat(ti)
                        val selected = group.isTrackSelected(ti)
                        when (group.type) {
                            C.TRACK_TYPE_VIDEO -> if (selected && fmt.height > 0) {
                                res = when {
                                    fmt.height >= 2160 -> "4K (${fmt.width}×${fmt.height})"
                                    fmt.height >= 1080 -> "1080p (${fmt.width}×${fmt.height})"
                                    fmt.height >= 720 -> "720p (${fmt.width}×${fmt.height})"
                                    else -> "${fmt.height}p"
                                }
                                if (fmt.frameRate > 0f) fps = "${fmt.frameRate.toInt()} fps"
                                codec = fmt.sampleMimeType?.substringAfter("/")?.uppercase().orEmpty()
                            }
                            C.TRACK_TYPE_AUDIO -> {
                                val label = formatLanguage(fmt.language, fmt.label, "Ljud ${aChoices.size + 1}")
                                aChoices.add(TvTrackChoice(label, gi, ti, selected))
                                if (selected) aLang = label
                            }
                            C.TRACK_TYPE_TEXT -> {
                                val label = formatLanguage(fmt.language, fmt.label, "Undertext ${sChoices.size + 1}")
                                sChoices.add(TvTrackChoice(label, gi, ti, selected))
                                if (selected) sLang = label
                            }
                        }
                    }
                }
                audioTracks = aChoices
                subtitleTracks = sChoices
                details = TvMediaDetails(
                    resolution = res.ifBlank { "Auto" },
                    fps = fps,
                    videoCodec = codec,
                    audioLang = aLang.ifBlank { "Standard" },
                    subtitleLang = sLang
                )
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(overlayVisible, activeMenu, item.id) {
        if (overlayVisible && activeMenu == null) {
            delay(6500)
            overlayVisible = false
        }
    }

    fun zap(delta: Int) {
        if (item.type == ContentType.LIVE && liveChannels.isNotEmpty()) {
            val idx = liveChannels.indexOfFirst { it.id == item.id }
            if (idx != -1) {
                onSwitchChannel(liveChannels[(idx + delta + liveChannels.size) % liveChannels.size])
            }
        }
    }

    val (country, sub) = TvEngine.parseCountryAndSub(item.category, item.title)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown) {
                    when (ev.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (activeMenu != null) activeMenu = null else overlayVisible = !overlayVisible
                            true
                        }
                        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_UP -> {
                            if (activeMenu == null) zap(1)
                            true
                        }
                        KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (activeMenu == null) zap(-1)
                            true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            if (activeMenu != null) activeMenu = null else onClose()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = if (aspectMode == 1) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                }
            },
            update = { v ->
                v.player = exoPlayer
                v.resizeMode = if (aspectMode == 1) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (activeMenu != null) {
            Surface(
                color = Color(0xEB0E1320),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.align(Alignment.Center).width(400.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (activeMenu == "AUDIO") "Välj ljudspråk" else "Välj undertext",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (activeMenu == "SUB") {
                        Button(
                            onClick = {
                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                    .build()
                                activeMenu = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Av")
                        }
                    }
                    val options = if (activeMenu == "AUDIO") audioTracks else subtitleTracks
                    if (options.isEmpty()) {
                        Text("Inga extra spår hittades.", color = Color(0xFF9CA3AF))
                    }
                    options.forEach { opt ->
                        Button(
                            onClick = {
                                val grp = exoPlayer.currentTracks.groups.getOrNull(opt.groupIndex)
                                if (grp != null) {
                                    val builder = exoPlayer.trackSelectionParameters.buildUpon()
                                    if (activeMenu == "SUB") {
                                        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    }
                                    exoPlayer.trackSelectionParameters = builder
                                        .setOverrideForType(TrackSelectionOverride(grp.mediaTrackGroup, opt.trackIndex))
                                        .build()
                                }
                                activeMenu = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(opt.label)
                        }
                    }
                    OutlinedButton(onClick = { activeMenu = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("Stäng", color = Color.White)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color(0xD90A0E17),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 36.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "$country • $sub",
                                color = Color(0xFF93C5FD),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                item.title,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoTag("Upplösning: ${details.resolution}", Color(0xFF065F46))
                            if (details.fps.isNotBlank()) InfoTag(details.fps, Color(0xFF1E3A8A))
                            if (details.videoCodec.isNotBlank()) InfoTag(details.videoCodec, Color(0xFF374151))
                            InfoTag("Ljud: ${details.audioLang}", Color(0xFF312E81))
                            InfoTag("Text: ${details.subtitleLang}", Color(0xFF4C1D95))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }) {
                            Text(if (exoPlayer.isPlaying) "Pausa" else "Spela")
                        }
                        OutlinedButton(onClick = { activeMenu = "AUDIO" }) {
                            Icon(Icons.Default.Audiotrack, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Språk (${audioTracks.size})", color = Color.White)
                        }
                        OutlinedButton(onClick = { activeMenu = "SUB" }) {
                            Icon(Icons.Default.Subtitles, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Undertext (${subtitleTracks.size})", color = Color.White)
                        }
                        OutlinedButton(onClick = { aspectMode = if (aspectMode == 0) 1 else 0 }) {
                            Text(if (aspectMode == 0) "Zoom" else "Passa", color = Color.White)
                        }
                        OutlinedButton(onClick = onToggleFavorite) {
                            Text(if (isFavorite) "★ Favorit" else "☆ Favorit", color = Color.White)
                        }
                        OutlinedButton(onClick = onClose) {
                            Text("Stäng", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private fun formatLanguage(code: String?, label: String?, fallback: String): String {
    if (!label.isNullOrBlank()) return label
    if (!code.isNullOrBlank() && code != "und") {
        val display = Locale.forLanguageTag(code).getDisplayLanguage(Locale("sv"))
        if (display.isNotBlank()) return display.replaceFirstChar { it.uppercase() }
        return code.uppercase()
    }
    return fallback
}

@Composable
private fun PinPrompt(expectedPin: String, onSuccess: () -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color(0xD9000000)), contentAlignment = Alignment.Center) {
        Surface(
            color = Color(0xFF141926),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.width(360.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White)
                Text("Ange PIN-kod", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        error = false
                        if (it.length <= 4) code = it.filter(Char::isDigit)
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                if (error) {
                    Text("Fel PIN-kod", color = Color(0xFFF87171), fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Avbryt", color = Color.White)
                    }
                    Button(onClick = { if (code == expectedPin) onSuccess() else error = true }) {
                        Text("Lås upp")
                    }
                }
            }
        }
    }
}
