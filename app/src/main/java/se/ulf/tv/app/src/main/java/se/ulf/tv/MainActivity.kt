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
import androidx.compose.material.icons.filled.Wifi
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
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

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
    CONTENT("Innehåll & PIN"),
    REMOTE("Fjärrinmatning")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            UlfTvApp()
        }
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

    var catalog by remember { mutableStateOf(IptvRepository.demoCatalog()) }
    var statusText by remember { mutableStateOf("Demoläge") }
    var isLoading by remember { mutableStateOf(false) }

    var section by remember { mutableStateOf(TvSection.HOME) }
    var activePlayerItem by remember { mutableStateOf<MediaItem?>(null) }
    var activeSeriesDetail by remember { mutableStateOf<MediaItem?>(null) }
    var pinUnlocked by remember { mutableStateOf(false) }
    var pendingProtectedAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val activeAccount = accounts.firstOrNull { it.id == activeAccountId }

    fun refreshCatalog(targetAccount: AccountProfile?, currentSettings: AppSettings) {
        scope.launch {
            isLoading = true
            catalog = IptvRepository.loadAccountCatalog(
                account = targetAccount ?: AccountProfile("demo", "Demo", "M3U"),
                settings = currentSettings,
                onStatus = { msg -> statusText = msg }
            )
            statusText = if (targetAccount == null) {
                "Demoläge • ${catalog.liveChannels.size} live"
            } else {
                "${targetAccount.name}: ${catalog.liveChannels.size} live • ${catalog.movies.size} filmer • ${catalog.series.size} serier"
            }
            isLoading = false
        }
    }

    LaunchedEffect(activeAccountId, settings.userAgent, settings.hideAdult) {
        refreshCatalog(activeAccount, settings)
    }

    fun updateSettings(newSettings: AppSettings) {
        settings = newSettings
        saveSettings(context, newSettings)
    }

    fun toggleFavorite(itemId: String) {
        favorites = if (favorites.contains(itemId)) favorites - itemId else favorites + itemId
        saveFavorites(context, favorites)
    }

    fun guardedPlay(item: MediaItem) {
        if (settings.parentalEnabled && !pinUnlocked && isAdultLike(item)) {
            pendingProtectedAction = { activePlayerItem = item }
        } else {
            activePlayerItem = item
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0D14), Color(0xFF06070B), Color(0xFF040508))
                )
            )
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
                    when {
                        activePlayerItem != null -> {
                            activePlayerItem = null
                            true
                        }
                        activeSeriesDetail != null -> {
                            activeSeriesDetail = null
                            true
                        }
                        section != TvSection.HOME -> {
                            section = TvSection.HOME
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        when {
            activePlayerItem != null -> {
                TvPlayerScreen(
                    item = activePlayerItem!!,
                    allLiveChannels = catalog.liveChannels,
                    settings = settings,
                    isFavorite = favorites.contains(activePlayerItem!!.id),
                    onToggleFavorite = { toggleFavorite(activePlayerItem!!.id) },
                    onSwitchChannel = { nextItem -> activePlayerItem = nextItem },
                    onExit = { activePlayerItem = null }
                )
            }

            activeSeriesDetail != null -> {
                SeriesDetailScreen(
                    series = activeSeriesDetail!!,
                    account = activeAccount,
                    userAgent = settings.userAgent,
                    isFavorite = favorites.contains(activeSeriesDetail!!.id),
                    onToggleFavorite = { toggleFavorite(activeSeriesDetail!!.id) },
                    onPlayEpisode = { ep ->
                        guardedPlay(
                            MediaItem(
                                id = "ep_${ep.id}",
                                title = "${activeSeriesDetail!!.title} • S${ep.season}E${ep.episodeNum} - ${ep.title}",
                                category = activeSeriesDetail!!.category,
                                country = activeSeriesDetail!!.country,
                                subCategory = activeSeriesDetail!!.subCategory,
                                type = ContentType.SERIES,
                                streamUrl = ep.streamUrl,
                                posterUrl = ep.thumbUrl.ifBlank { activeSeriesDetail!!.posterUrl },
                                backdropUrl = activeSeriesDetail!!.backdropUrl,
                                plot = ep.plot.ifBlank { activeSeriesDetail!!.plot },
                                qualityBadge = activeSeriesDetail!!.qualityBadge
                            )
                        )
                    },
                    onBack = { activeSeriesDetail = null }
                )
            }

            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    AppleTvTopBar(
                        currentSection = section,
                        statusText = statusText,
                        isLoading = isLoading,
                        onSelectSection = { section = it },
                        onQuickRefresh = { refreshCatalog(activeAccount, settings) }
                    )

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (section) {
                            TvSection.HOME -> HomeDashboard(
                                catalog = catalog,
                                favorites = favorites,
                                onPlayItem = { item ->
                                    if (item.type == ContentType.SERIES) activeSeriesDetail = item
                                    else guardedPlay(item)
                                },
                                onSeeAll = { section = it }
                            )

                            TvSection.LIVE -> LiveCountryBrowserSection(
                                channels = catalog.liveChannels,
                                favorites = favorites,
                                onToggleFavorite = ::toggleFavorite,
                                onSelectChannel = ::guardedPlay
                            )

                            TvSection.MOVIES -> VodCuratedBrowserSection(
                                title = "Filmer",
                                items = catalog.movies,
                                favorites = favorites,
                                onToggleFavorite = ::toggleFavorite,
                                onSelectItem = ::guardedPlay
                            )

                            TvSection.SERIES -> VodCuratedBrowserSection(
                                title = "Serier",
                                items = catalog.series,
                                favorites = favorites,
                                onToggleFavorite = ::toggleFavorite,
                                onSelectItem = { activeSeriesDetail = it }
                            )

                            TvSection.FAVORITES -> {
                                val allItems = catalog.liveChannels + catalog.movies + catalog.series
                                val favItems = allItems.filter { favorites.contains(it.id) }
                                VodCuratedBrowserSection(
                                    title = "Favoriter",
                                    items = favItems,
                                    favorites = favorites,
                                    onToggleFavorite = ::toggleFavorite,
                                    onSelectItem = { item ->
                                        if (item.type == ContentType.SERIES) activeSeriesDetail = item
                                        else guardedPlay(item)
                                    }
                                )
                            }

                            TvSection.SEARCH -> SearchCatalogSection(
                                catalog = catalog,
                                onSelectItem = { item ->
                                    if (item.type == ContentType.SERIES) activeSeriesDetail = item
                                    else guardedPlay(item)
                                }
                            )

                            TvSection.SETTINGS -> CompleteSettingsScreen(
                                settings = settings,
                                accounts = accounts,
                                activeAccountId = activeAccountId,
                                onUpdateSettings = ::updateSettings,
                                onAddAccount = { acc ->
                                    val updated = accounts + acc
                                    accounts = updated
                                    saveAccounts(context, updated)
                                    activeAccountId = acc.id
                                    saveActiveAccountId(context, acc.id)
                                    refreshCatalog(acc, settings)
                                    section = TvSection.HOME
                                },
                                onSelectAccount = { id ->
                                    activeAccountId = id
                                    saveActiveAccountId(context, id)
                                    refreshCatalog(accounts.firstOrNull { it.id == id }, settings)
                                },
                                onDeleteAccount = { id ->
                                    val updated = accounts.filterNot { it.id == id }
                                    accounts = updated
                                    saveAccounts(context, updated)
                                    if (activeAccountId == id) {
                                        val nextId = updated.firstOrNull()?.id.orEmpty()
                                        activeAccountId = nextId
                                        saveActiveAccountId(context, nextId)
                                        refreshCatalog(updated.firstOrNull(), settings)
                                    }
                                },
                                onRemoteM3u = { url ->
                                    val acc = AccountProfile("acc_${System.currentTimeMillis()}", "Fjärr-M3U", "M3U", m3uUrl = url)
                                    val updated = accounts + acc
                                    accounts = updated
                                    saveAccounts(context, updated)
                                    activeAccountId = acc.id
                                    saveActiveAccountId(context, acc.id)
                                    refreshCatalog(acc, settings)
                                    section = TvSection.HOME
                                },
                                onRemoteXtream = { server, user, pass ->
                                    val acc = AccountProfile("acc_${System.currentTimeMillis()}", "Fjärr-Xtream", "XTREAM", server = server, username = user, password = pass)
                                    val updated = accounts + acc
                                    accounts = updated
                                    saveAccounts(context, updated)
                                    activeAccountId = acc.id
                                    saveActiveAccountId(context, acc.id)
                                    refreshCatalog(acc, settings)
                                    section = TvSection.HOME
                                }
                            )
                        }
                    }
                }
            }
        }

        if (pendingProtectedAction != null) {
            PinPromptOverlay(
                expectedPin = settings.parentalPin,
                onSuccess = {
                    pinUnlocked = true
                    val action = pendingProtectedAction
                    pendingProtectedAction = null
                    action?.invoke()
                },
                onCancel = { pendingProtectedAction = null }
            )
        }
    }
}

@Composable
private fun LiveCountryBrowserSection(
    channels: List<MediaItem>,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onSelectChannel: (MediaItem) -> Unit
) {
    val countries = remember(channels) {
        val grouped = channels.groupBy { it.country }
        listOf("Alla länder") + grouped.keys.sortedByDescending { grouped[it]?.size ?: 0 }
    }
    var selectedCountry by remember { mutableStateOf("Alla länder") }

    val countryChannels = remember(channels, selectedCountry) {
        if (selectedCountry == "Alla länder") channels else channels.filter { it.country == selectedCountry }
    }

    val subCategories = remember(countryChannels) {
        val preferredOrder = listOf("Alla", "Sport", "Film & Serier", "Nyheter", "Barn", "Dokumentär", "Musik", "Allmänt")
        val existing = countryChannels.map { it.subCategory }.toSet()
        listOf("Alla") + preferredOrder.filter { it != "Alla" && existing.contains(it) }
    }
    var selectedSub by remember { mutableStateOf("Alla") }

    LaunchedEffect(selectedCountry) {
        selectedSub = "Alla"
    }

    val visibleChannels = remember(countryChannels, selectedSub) {
        if (selectedSub == "Alla") countryChannels else countryChannels.filter { it.subCategory == selectedSub }
    }

    var focusedChannel by remember { mutableStateOf(visibleChannels.firstOrNull()) }
    LaunchedEffect(visibleChannels) {
        if (visibleChannels.isNotEmpty() && !visibleChannels.contains(focusedChannel)) {
            focusedChannel = visibleChannels.first()
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
                Text("Länder", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            }
            items(countries) { country ->
                val count = if (country == "Alla länder") channels.size else channels.count { it.country == country }
                val active = selectedCountry == country
                var focused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                active -> Color(0xFF2563EB)
                                focused -> Color(0x33FFFFFF)
                                else -> Color(0x12FFFFFF)
                            }
                        )
                        .onFocusChanged { focused = it.isFocused }
                        .focusable()
                        .clickable { selectedCountry = country }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(country, color = Color.White, fontSize = 14.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("$count", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                    }
                }
            }
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

            focusedChannel?.let { info ->
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
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(info.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                InfoBadge("${info.country} • ${info.subCategory}", Color(0xFF1E3A8A))
                                if (info.qualityBadge.isNotBlank()) {
                                    InfoBadge(info.qualityBadge, Color(0xFF065F46))
                                }
                            }
                            Text(
                                text = if (info.epgTitle.isNotBlank()) "${info.epgTitle} • ${info.epgNext}" else "Kategori: ${info.category} • Stöd för ljudspår & undertexter i spelaren",
                                color = Color(0xFF9CA3AF),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(
                            onClick = { onToggleFavorite(info.id) },
                            border = BorderStroke(1.dp, Color(0x55FFFFFF))
                        ) {
                            Icon(
                                imageVector = if (favorites.contains(info.id)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
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
                    Box(modifier = Modifier.onFocusChanged { if (it.isFocused) focusedChannel = channel }) {
                        TvMediaCard(
                            item = channel,
                            isPoster = false,
                            onClick = { onSelectChannel(channel) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VodCuratedBrowserSection(
    title: String,
    items: List<MediaItem>,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onSelectItem: (MediaItem) -> Unit
) {
    val smartFilters = remember(items) {
        val base = listOf("Senast tillagda", "Nysläppta (2025–2026)", "Topprankade", "Alla")
        val genres = items.map { it.subCategory }.filter { it.isNotBlank() }.distinct().sorted()
        base + genres
    }
    var selectedFilter by remember { mutableStateOf("Senast tillagda") }

    val sortedOrFiltered = remember(items, selectedFilter) {
        when (selectedFilter) {
            "Senast tillagda" -> items.sortedByDescending { it.addedTimestamp }
            "Nysläppta (2025–2026)" -> items.filter { it.year == "2026" || it.year == "2025" }.ifEmpty { items.sortedByDescending { it.year } }
            "Topprankade" -> items.sortedByDescending { it.rating.toDoubleOrNull() ?: 0.0 }
            "Alla" -> items
            else -> items.filter { it.subCategory == selectedFilter }
        }
    }

    var focusedItem by remember { mutableStateOf(sortedOrFiltered.firstOrNull()) }
    LaunchedEffect(sortedOrFiltered) {
        if (sortedOrFiltered.isNotEmpty() && !sortedOrFiltered.contains(focusedItem)) {
            focusedItem = sortedOrFiltered.first()
        }
    }

    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 42.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        LazyColumn(
            modifier = Modifier.width(240.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            }
            items(smartFilters) { filterName ->
                val active = selectedFilter == filterName
                var focused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                active -> Color(0xFF2563EB)
                                focused -> Color(0x33FFFFFF)
                                else -> Color(0x12FFFFFF)
                            }
                        )
                        .onFocusChanged { focused = it.isFocused }
                        .focusable()
                        .clickable { selectedFilter = filterName }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(filterName, color = Color.White, fontSize = 14.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            focusedItem?.let { info ->
                Surface(
                    color = Color(0x18FFFFFF),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0x26FFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(info.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (info.year.isNotBlank()) InfoBadge(info.year, Color(0xFF1E3A8A))
                                if (info.qualityBadge.isNotBlank()) InfoBadge(info.qualityBadge, Color(0xFF065F46))
                                if (info.rating.isNotBlank()) InfoBadge("★ ${info.rating}", Color(0xFF78350F))
                            }
                            Text(
                                text = info.plot.ifBlank { "${info.subCategory} • ${info.category}" },
                                color = Color(0xFF9CA3AF),
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(onClick = { onToggleFavorite(info.id) }, border = BorderStroke(1.dp, Color(0x55FFFFFF))) {
                            Icon(
                                imageVector = if (favorites.contains(info.id)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = Color(0xFFF87171)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (favorites.contains(info.id)) "Favorit" else "Spara", color = Color.White)
                        }
                    }
                }
            }

            if (sortedOrFiltered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Inga titlar hittades i denna kategori.", color = Color(0xFF9CA3AF), fontSize = 16.sp)
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(sortedOrFiltered.take(250), key = { it.id }) { item ->
                        Box(modifier = Modifier.onFocusChanged { if (it.isFocused) focusedItem = item }) {
                            TvMediaCard(
                                item = item,
                                isPoster = item.type != ContentType.LIVE,
                                onClick = { onSelectItem(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoBadge(text: String, bg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AppleTvTopBar(
    currentSection: TvSection,
    statusText: String,
    isLoading: Boolean,
    onSelectSection: (TvSection) -> Unit,
    onQuickRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 42.dp, vertical = 18.dp),
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
            Text("ULF TV", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 1.sp)
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0x26FFFFFF))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvSection.entries.forEach { tab ->
                val selected = currentSection == tab
                var focused by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "tabScale")
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
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(tab.icon, contentDescription = tab.label, tint = if (selected) Color.Black else Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tab.label, color = if (selected) Color.Black else Color.White, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (isLoading) "Synkar..." else statusText,
                color = Color(0xFF9CA3AF),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(220.dp)
            )
            var refreshFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (refreshFocused) Color.White else Color(0x22FFFFFF))
                    .onFocusChanged { refreshFocused = it.isFocused }
                    .focusable()
                    .clickable { onQuickRefresh() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Uppdatera", tint = if (refreshFocused) Color.Black else Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun HomeDashboard(
    catalog: CatalogData,
    favorites: Set<String>,
    onPlayItem: (MediaItem) -> Unit,
    onSeeAll: (TvSection) -> Unit
) {
    val featured = catalog.movies.firstOrNull()
        ?: catalog.series.firstOrNull()
        ?: catalog.liveChannels.firstOrNull()

    val favItems = remember(catalog, favorites) {
        (catalog.liveChannels + catalog.movies + catalog.series).filter { favorites.contains(it.id) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        if (featured != null) {
            item {
                AppleTvHeroBanner(
                    item = featured,
                    onPlay = { onPlayItem(featured) },
                    onBrowse = {
                        onSeeAll(
                            when (featured.type) {
                                ContentType.MOVIE -> TvSection.MOVIES
                                ContentType.SERIES -> TvSection.SERIES
                                ContentType.LIVE -> TvSection.LIVE
                            }
                        )
                    }
                )
            }
        }

        if (favItems.isNotEmpty()) {
            item {
                ShelfRow("Favoriter", favItems.take(24), isPoster = false, onItemClick = onPlayItem, onMoreClick = { onSeeAll(TvSection.FAVORITES) })
            }
        }

        if (catalog.liveChannels.isNotEmpty()) {
            item {
                ShelfRow("Live TV • Sorterat efter länder (${catalog.liveChannels.size})", catalog.liveChannels.take(30), isPoster = false, onItemClick = onPlayItem, onMoreClick = { onSeeAll(TvSection.LIVE) })
            }
        }

        if (catalog.movies.isNotEmpty()) {
            item {
                ShelfRow("Senaste Filmer (${catalog.movies.size})", catalog.movies.sortedByDescending { it.addedTimestamp }.take(30), isPoster = true, onItemClick = onPlayItem, onMoreClick = { onSeeAll(TvSection.MOVIES) })
            }
        }

        if (catalog.series.isNotEmpty()) {
            item {
                ShelfRow("Senaste Serier (${catalog.series.size})", catalog.series.sortedByDescending { it.addedTimestamp }.take(30), isPoster = true, onItemClick = onPlayItem, onMoreClick = { onSeeAll(TvSection.SERIES) })
            }
        }
    }
}

@Composable
private fun AppleTvHeroBanner(item: MediaItem, onPlay: () -> Unit, onBrowse: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(310.dp)
            .padding(horizontal = 42.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF131826))
    ) {
        if (item.backdropUrl.isNotBlank()) {
            AsyncImage(model = item.backdropUrl, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color(0xF206080E), Color(0xB806080E), Color(0x3306080E)))
            )
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(0.68f).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoBadge(item.country, Color(0xFF1E3A8A))
                InfoBadge(item.subCategory, Color(0xFF374151))
                if (item.qualityBadge.isNotBlank()) InfoBadge(item.qualityBadge, Color(0xFF065F46))
            }
            Text(item.title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.plot.isNotBlank()) {
                Text(item.plot, color = Color(0xFFD1D5DB), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(onClick = onPlay, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (item.type == ContentType.SERIES) "Visa säsonger" else "Spela nu", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onBrowse, border = BorderStroke(1.dp, Color(0x66FFFFFF)), shape = RoundedCornerShape(12.dp)) {
                    Text("Utforska alla", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ShelfRow(title: String, items: List<MediaItem>, isPoster: Boolean, onItemClick: (MediaItem) -> Unit, onMoreClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 42.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("Visa alla ›", color = Color(0xFF9CA3AF), fontSize = 14.sp, modifier = Modifier.focusable().clickable { onMoreClick() }.padding(8.dp))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 42.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items(items, key = { it.id }) { item ->
                TvMediaCard(item = item, isPoster = isPoster, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
private fun TvMediaCard(item: MediaItem, isPoster: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "cardScale")

    Column(
        modifier = Modifier
            .width(if (isPoster) 150.dp else 230.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (isPoster) 2f / 3f else 16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF141926))
                .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color(0x22FFFFFF), RoundedCornerShape(16.dp))
        ) {
            if (item.posterUrl.isNotBlank()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = if (isPoster) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(if (isPoster) 0.dp else 10.dp)
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(item.title.take(2).uppercase(), color = Color(0xFF93C5FD), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (item.qualityBadge.isNotBlank()) {
                Box(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xCC065F46)).padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(item.qualityBadge, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (item.rating.isNotBlank()) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xCC000000)).padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("★ ${item.rating}", color = Color(0xFFFBBF24), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(item.title, color = if (focused) Color.White else Color(0xFFE5E7EB), fontSize = 14.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${item.country} • ${item.subCategory}", color = Color(0xFF9CA3AF), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SeriesDetailScreen(
    series: MediaItem,
    account: AccountProfile?,
    userAgent: String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlayEpisode: (EpisodeItem) -> Unit,
    onBack: () -> Unit
) {
    var seasonsMap by remember { mutableStateOf>>(emptyMap()) }
    var selectedSeason by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(series.id) {
        loading = true
        val loaded = if (account != null && account.mode == "XTREAM" && series.seriesId.isNotBlank()) {
            IptvRepository.fetchSeriesEpisodes(account, series.seriesId, userAgent)
        } else if (series.streamUrl.isNotBlank()) {
            mapOf(1 to listOf(EpisodeItem(series.id, 1, 1, series.title, series.streamUrl)))
        } else {
            emptyMap()
        }
        seasonsMap = loaded
        selectedSeason = loaded.keys.firstOrNull() ?: 1
        loading = false
    }

    val seasonNumbers = seasonsMap.keys.sorted()
    val episodes = seasonsMap[selectedSeason].orEmpty()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
            if (series.posterUrl.isNotBlank()) {
                AsyncImage(model = series.posterUrl, contentDescription = series.title, contentScale = ContentScale.Crop, modifier = Modifier.width(140.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(series.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("${series.subCategory} • ${series.year} ${if (series.rating.isNotBlank()) "• ★ ${series.rating}" else ""}", color = Color(0xFF93C5FD), fontSize = 14.sp)
                if (series.plot.isNotBlank()) {
                    Text(series.plot, color = Color(0xFFD1D5DB), fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onToggleFavorite, border = BorderStroke(1.dp, Color(0x55FFFFFF))) {
                        Text(if (isFavorite) "I Favoriter" else "Lägg till favorit", color = Color.White)
                    }
                    OutlinedButton(onClick = onBack, border = BorderStroke(1.dp, Color(0x55FFFFFF))) {
                        Text("Tillbaka", color = Color.White)
                    }
                }
            }
        }

        if (seasonNumbers.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(seasonNumbers) { sNum ->
                    val active = sNum == selectedSeason
                    Button(
                        onClick = { selectedSeason = sNum },
                        colors = ButtonDefaults.buttonColors(containerColor = if (active) Color.White else Color(0x22FFFFFF), contentColor = if (active) Color.Black else Color.White)
                    ) { Text("Säsong $sNum", fontWeight = FontWeight.Bold) }
                }
            }
        }

        if (loading) {
            Text("Hämtar avsnitt...", color = Color(0xFF9CA3AF))
        } else if (episodes.isEmpty()) {
            Text("Inga avsnitt hittades.", color = Color(0xFF9CA3AF))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(episodes, key = { it.id }) { ep ->
                    Surface(color = Color(0x18FFFFFF), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().focusable().clickable { onPlayEpisode(ep) }) {
                        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Avsnitt ${ep.episodeNum}: ${ep.title}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                if (ep.duration.isNotBlank()) Text("Längd: ${ep.duration}", color = Color(0xFF9CA3AF), fontSize = 12.sp)
                            }
                            Icon(Icons.Default.PlayArrow, contentDescription = "Spela", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchCatalogSection(catalog: CatalogData, onSelectItem: (MediaItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    val all = remember(catalog) { catalog.liveChannels + catalog.movies + catalog.series }
    val matches = remember(query, all) {
        val q = query.trim().lowercase()
        if (q.length < 2) emptyList()
        else all.filter { it.title.lowercase().contains(q) || it.country.lowercase().contains(q) || it.subCategory.lowercase().contains(q) }.take(80)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Sök kanal, land, genre, film eller serie") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color(0x55FFFFFF),
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color(0xFF9CA3AF)
            ),
            modifier = Modifier.fillMaxWidth(0.6f)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(matches, key = { it.id }) { item ->
                TvMediaCard(item = item, isPoster = item.type != ContentType.LIVE, onClick = { onSelectItem(item) })
            }
        }
    }
}

@Composable
private fun CompleteSettingsScreen(
    settings: AppSettings,
    accounts: List<AccountProfile>,
    activeAccountId: String,
    onUpdateSettings: (AppSettings) -> Unit,
    onAddAccount: (AccountProfile) -> Unit,
    onSelectAccount: (String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onRemoteM3u: (String) -> Unit,
    onRemoteXtream: (String, String, String) -> Unit
) {
    var tab by remember { mutableStateOf(SettingsTab.ACCOUNTS) }
    var profileName by remember { mutableStateOf("Mitt IPTV-konto") }
    var m3uInput by remember { mutableStateOf("") }
    var serverInput by remember { mutableStateOf("") }
    var userInput by remember { mutableStateOf("") }
    var passInput by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf(settings.parentalPin) }

    val remoteServer = remember {
        RemoteWebSetup(port = 8088, onM3uReceived = { onRemoteM3u(it) }, onXtreamReceived = { s, u, p -> onRemoteXtream(s, u, p) })
    }

    LaunchedEffect(tab) {
        if (tab == SettingsTab.REMOTE) remoteServer.start(CoroutineScope(Dispatchers.IO))
    }
    DisposableEffect(Unit) { onDispose { remoteServer.stop() } }

    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 42.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(modifier = Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Inställningar", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            SettingsTab.entries.forEach { item ->
                val selected = tab == item
                Button(
                    onClick = { tab = item },
                    colors = ButtonDefaults.buttonColors(containerColor = if (selected) Color.White else Color(0x18FFFFFF), contentColor = if (selected) Color.Black else Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(item.label, fontWeight = FontWeight.Bold) }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 36.dp)) {
            when (tab) {
                SettingsTab.ACCOUNTS -> {
                    item {
                        Text("Sparade konton", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        accounts.forEach { acc ->
                            val isActive = acc.id == activeAccountId
                            Surface(color = if (isActive) Color(0x332563EB) else Color(0x16FFFFFF), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(acc.name, color = Color.White, fontWeight = FontWeight.Bold)
                                        Text("${acc.mode} • ${if (acc.mode == "XTREAM") acc.server else acc.m3uUrl}", color = Color(0xFF9CA3AF), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { onSelectAccount(acc.id) }) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (isActive) "Aktivt" else "Välj")
                                        }
                                        OutlinedButton(onClick = { onDeleteAccount(acc.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Ta bort", tint = Color(0xFFF87171))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text("Lägg till nytt konto", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = profileName, onValueChange = { profileName = it }, label = { Text("Kontonamn") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = m3uInput, onValueChange = { m3uInput = it }, label = { Text("M3U-URL (http://... eller https://...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            if (m3uInput.isNotBlank()) {
                                onAddAccount(AccountProfile("acc_${System.currentTimeMillis()}", profileName.ifBlank { "M3U" }, "M3U", m3uUrl = m3uInput.trim()))
                                m3uInput = ""
                            }
                        }) { Text("Spara M3U-konto") }

                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(value = serverInput, onValueChange = { serverInput = it }, label = { Text("Xtream Server-URL (t.ex. http://domän:8080)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(value = userInput, onValueChange = { userInput = it }, label = { Text("Användarnamn") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = passInput, onValueChange = { passInput = it }, label = { Text("Lösenord") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            if (serverInput.isNotBlank() && userInput.isNotBlank() && passInput.isNotBlank()) {
                                onAddAccount(AccountProfile("acc_${System.currentTimeMillis()}", profileName.ifBlank { "Xtream" }, "XTREAM", server = serverInput.trim(), username = userInput.trim(), password = passInput.trim()))
                                serverInput = ""; userInput = ""; passInput = ""
                            }
                        }) { Text("Spara Xtream-konto") }
                    }
                }

                SettingsTab.PLAYBACK -> {
                    item {
                        SettingSwitchRow("Stor buffert (rekommenderas för Live TV)", "Minskar lagg på strömmar med variabel bithastighet", settings.largeBuffer) {
                            onUpdateSettings(settings.copy(largeBuffer = it))
                        }
                        SettingSwitchRow("Hårdvaruavkodning", "Använder TV:ns hårdvaruavkodare i första hand", settings.hardwareDecoding) {
                            onUpdateSettings(settings.copy(hardwareDecoding = it))
                        }
                        Text("Bildformat", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(0 to "Passa", 1 to "Fyll (Zoom)").forEach { (mode, label) ->
                                Button(
                                    onClick = { onUpdateSettings(settings.copy(aspectMode = mode)) },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (settings.aspectMode == mode) Color.White else Color(0x22FFFFFF), contentColor = if (settings.aspectMode == mode) Color.Black else Color.White)
                                ) { Text(label) }
                            }
                        }
                        Text("User-Agent", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("IPTVSmartersPro", "VLC/3.0.20", "TiviMate/4.7.0").forEach { ua ->
                                Button(
                                    onClick = { onUpdateSettings(settings.copy(userAgent = ua)) },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (settings.userAgent == ua) Color.White else Color(0x22FFFFFF), contentColor = if (settings.userAgent == ua) Color.Black else Color.White)
                                ) { Text(ua) }
                            }
                        }
                    }
                }

                SettingsTab.CONTENT -> {
                    item {
                        SettingSwitchRow("Dölj vuxeninnehåll (18+ / XXX)", "Filtrerar bort vuxenkanaler automatiskt", settings.hideAdult) {
                            onUpdateSettings(settings.copy(hideAdult = it))
                        }
                        SettingSwitchRow("Aktivera PIN-skydd", "Kräver 4-siffrig PIN vid låst innehåll", settings.parentalEnabled) {
                            onUpdateSettings(settings.copy(parentalEnabled = it))
                        }
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { if (it.length <= 4) pinInput = it.filter(Char::isDigit) },
                            label = { Text("4-siffrig PIN-kod") },
                            singleLine = true,
                            modifier = Modifier.width(220.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { if (pinInput.length == 4) onUpdateSettings(settings.copy(parentalPin = pinInput)) }) {
                            Text("Spara PIN-kod")
                        }
                    }
                }

                SettingsTab.REMOTE -> {
                    item {
                        val ip = remoteServer.getLocalIpAddress() ?: "Kontrollera Wi-Fi"
                        Surface(color = Color(0x18FFFFFF), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF60A5FA))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Fjärrinmatning från mobil eller dator", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("Öppna i webbläsaren på samma nätverk:", color = Color(0xFFD1D5DB))
                                Text("http://$ip:8088", color = Color(0xFF34D399), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(subtitle, color = Color(0xFF9CA3AF), fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color.White))
    }
}

@Composable
private fun TvPlayerScreen(
    item: MediaItem,
    allLiveChannels: List<MediaItem>,
    settings: AppSettings,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onSwitchChannel: (MediaItem) -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    var showOverlay by remember { mutableStateOf(true) }
    var aspectMode by remember { mutableStateOf(settings.aspectMode) }
    var techInfo by remember { mutableStateOf(StreamTechInfo(resolution = item.qualityBadge)) }
    var audioTracks by remember { mutableStateOf<List<TrackOption>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<TrackOption>>(emptyList()) }
    var activeTrackMenu by remember { mutableStateOf<String?>(null) }

    val trackSelector = remember { DefaultTrackSelector(context) }

    val exoPlayer = remember(item.streamUrl, settings.largeBuffer, settings.hardwareDecoding) {
        val okClient = IptvRepository.buildClient(settings.userAgent)
        val dataSourceFactory = OkHttpDataSource.Factory(okClient)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                if (settings.largeBuffer) 15000 else 5000,
                if (settings.largeBuffer) 60000 else 20000,
                if (settings.largeBuffer) 2500 else 1000,
                if (settings.largeBuffer) 5000 else 2000
            )
            .build()
        val renderersFactory = DefaultRenderersFactory(context).setExtensionRendererMode(
            if (settings.hardwareDecoding) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
        )
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .build().apply {
                setMediaItem(ExoMediaItem.fromUri(item.streamUrl))
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                var resLabel = item.qualityBadge
                var fpsLabel = ""
                var vCodec = ""
                var aCodec = ""
                var activeAudio = ""
                var activeSub = "Av"

                val audios = mutableListOf<TrackOption>()
                val subs = mutableListOf<TrackOption>()

                for (gIndex in tracks.groups.indices) {
                    val group = tracks.groups[gIndex]
                    for (tIndex in 0 until group.length) {
                        val fmt = group.getTrackFormat(tIndex)
                        val selected = group.isTrackSelected(tIndex)
                        when (group.type) {
                            C.TRACK_TYPE_VIDEO -> if (selected && fmt.height > 0) {
                                resLabel = when {
                                    fmt.height >= 2160 -> "4K (${fmt.width}×${fmt.height})"
                                    fmt.height >= 1080 -> "1080p (${fmt.width}×${fmt.height})"
                                    fmt.height >= 720 -> "720p (${fmt.width}×${fmt.height})"
                                    else -> "${fmt.height}p (${fmt.width}×${fmt.height})"
                                }
                                if (fmt.frameRate > 0f) fpsLabel = "${fmt.frameRate.toInt()} fps"
                                vCodec = fmt.sampleMimeType?.substringAfter("/")?.uppercase().orEmpty()
                            }
                            C.TRACK_TYPE_AUDIO -> {
                                val langName = formatLanguageLabel(fmt.language, fmt.label, "Ljud ${audios.size + 1}")
                                audios.add(TrackOption(langName, gIndex, tIndex, selected))
                                if (selected) {
                                    activeAudio = langName
                                    aCodec = fmt.sampleMimeType?.substringAfter("/")?.uppercase().orEmpty()
                                }
                            }
                            C.TRACK_TYPE_TEXT -> {
                                val langName = formatLanguageLabel(fmt.language, fmt.label, "Undertext ${subs.size + 1}")
                                subs.add(TrackOption(langName, gIndex, tIndex, selected))
                                if (selected) activeSub = langName
                            }
                        }
                    }
                }

                audioTracks = audios
                subtitleTracks = subs
                techInfo = StreamTechInfo(
                    resolution = resLabel.ifBlank { "Auto" },
                    fps = fpsLabel,
                    videoCodec = vCodec,
                    audioCodec = aCodec,
                    activeAudioLang = activeAudio.ifBlank { "Standard" },
                    activeSubtitleLang = activeSub
                )
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(showOverlay, activeTrackMenu, item.id) {
        if (showOverlay && activeTrackMenu == null) {
            delay(6500)
            showOverlay = false
        }
    }

    fun selectTrack(tracks: Tracks, option: TrackOption) {
        val group = tracks.groups.getOrNull(option.groupIndex) ?: return
        val params = exoPlayer.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
            .build()
        exoPlayer.trackSelectionParameters = params
    }

    fun disableSubtitles() {
        val params = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        exoPlayer.trackSelectionParameters = params
    }

    fun zap(delta: Int) {
        if (item.type == ContentType.LIVE && allLiveChannels.isNotEmpty()) {
            val idx = allLiveChannels.indexOfFirst { it.id == item.id }
            if (idx != -1) {
                val nextIdx = (idx + delta + allLiveChannels.size) % allLiveChannels.size
                onSwitchChannel(allLiveChannels[nextIdx])
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown) {
                    when (ev.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (activeTrackMenu != null) activeTrackMenu = null
                            else showOverlay = !showOverlay
                            true
                        }
                        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_UP -> {
                            if (activeTrackMenu == null) zap(1)
                            true
                        }
                        KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (activeTrackMenu == null) zap(-1)
                            true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            if (activeTrackMenu != null) activeTrackMenu = null
                            else onExit()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = if (aspectMode == 1) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { view ->
                view.player = exoPlayer
                view.resizeMode = if (aspectMode == 1) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            modifier = Modifier.fillMaxSize()
        )

        if (activeTrackMenu != null) {
            Surface(
                color = Color(0xEB0E1320),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                modifier = Modifier.align(Alignment.Center).width(420.dp)
            ) {
                Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (activeTrackMenu == "AUDIO") "Välj ljudspråk" else "Välj undertext",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (activeTrackMenu == "AUDIO") {
                        if (audioTracks.isEmpty()) {
                            Text("Endast ett standardljudspår tillgängligt.", color = Color(0xFF9CA3AF))
                        } else {
                            audioTracks.forEach { opt ->
                                Button(
                                    onClick = {
                                        selectTrack(exoPlayer.currentTracks, opt)
                                        activeTrackMenu = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (opt.isSelected) Color.White else Color(0x22FFFFFF), contentColor = if (opt.isSelected) Color.Black else Color.White),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(opt.label) }
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                disableSubtitles()
                                activeTrackMenu = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FFFFFF), contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Av (Ingen undertext)") }

                        subtitleTracks.forEach { opt ->
                            Button(
                                onClick = {
                                    val params = exoPlayer.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
                                    exoPlayer.trackSelectionParameters = params
                                    selectTrack(exoPlayer.currentTracks, opt)
                                    activeTrackMenu = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (opt.isSelected) Color.White else Color(0x22FFFFFF), contentColor = if (opt.isSelected) Color.Black else Color.White),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(opt.label) }
                        }
                    }
                    OutlinedButton(onClick = { activeTrackMenu = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("Stäng", color = Color.White)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color(0xD90A0E17),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                border = BorderStroke(1.dp, Color(0x28FFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 36.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${item.country} • ${item.subCategory}", color = Color(0xFF93C5FD), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(item.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (item.epgTitle.isNotBlank()) {
                                Text("${item.epgTitle} • ${item.epgNext}", color = Color(0xFFD1D5DB), fontSize = 13.sp)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoBadge("Upplösning: ${techInfo.resolution}", Color(0xFF065F46))
                            if (techInfo.fps.isNotBlank()) InfoBadge(techInfo.fps, Color(0xFF1E3A8A))
                            if (techInfo.videoCodec.isNotBlank()) InfoBadge(techInfo.videoCodec, Color(0xFF374151))
                            InfoBadge("Ljud: ${techInfo.activeAudioLang}", Color(0xFF312E81))
                            InfoBadge("Text: ${techInfo.activeSubtitleLang}", Color(0xFF4C1D95))
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }) {
                            Text(if (exoPlayer.isPlaying) "Pausa" else "Spela")
                        }
                        OutlinedButton(onClick = { activeTrackMenu = "AUDIO" }) {
                            Icon(Icons.Default.Audiotrack, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Språk (${audioTracks.size})", color = Color.White)
                        }
                        OutlinedButton(onClick = { activeTrackMenu = "SUB" }) {
                            Icon(Icons.Default.Subtitles, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Undertext (${subtitleTracks.size})", color = Color.White)
                        }
                        OutlinedButton(onClick = { aspectMode = if (aspectMode == 0) 1 else 0 }) {
                            Text(if (aspectMode == 0) "Zoom" else "Passa", color = Color.White)
                        }
                        OutlinedButton(onClick = onToggleFavorite) {
                            Text(if (isFavorite) "★ Favorit" else "☆ Favorit", color = Color.White)
                        }
                        OutlinedButton(onClick = onExit) {
                            Text("Stäng", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private fun formatLanguageLabel(langCode: String?, label: String?, fallback: String): String {
    if (!label.isNullOrBlank()) return label
    if (!langCode.isNullOrBlank() && langCode != "und") {
        val loc = Locale.forLanguageTag(langCode)
        val display = loc.getDisplayLanguage(Locale("sv"))
        if (display.isNotBlank()) return display.replaceFirstChar { it.uppercase() }
        return langCode.uppercase()
    }
    return fallback
}

@Composable
private fun PinPromptOverlay(expectedPin: String, onSuccess: () -> Unit, onCancel: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xD9000000)), contentAlignment = Alignment.Center) {
        Surface(color = Color(0xFF141926), shape = RoundedCornerShape(20.dp), modifier = Modifier.width(380.dp)) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                Text("Ange PIN-kod", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = code,
                    onValueChange = { wrong = false; if (it.length <= 4) code = it.filter(Char::isDigit) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                if (wrong) Text("Fel PIN-kod", color = Color(0xFFF87171), fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onCancel) { Text("Avbryt", color = Color.White) }
                    Button(onClick = { if (code == expectedPin) onSuccess() else wrong = true }) { Text("Lås upp") }
                }
            }
        }
    }
}

private fun isAdultLike(item: MediaItem): Boolean {
    val t = "${item.title} ${item.category}".lowercase()
    return t.contains("xxx") || t.contains("adult") || t.contains("18+") || t.contains("porn")
}

private fun prefs(context: Context) = context.getSharedPreferences("ulf_tv_prefs", Context.MODE_PRIVATE)

private fun loadSettings(context: Context): AppSettings {
    val p = prefs(context)
    return AppSettings(
        userAgent = p.getString("ua", "IPTVSmartersPro") ?: "IPTVSmartersPro",
        largeBuffer = p.getBoolean("large_buf", true),
        hardwareDecoding = p.getBoolean("hw_dec", true),
        hideAdult = p.getBoolean("hide_adult", true),
        parentalPin = p.getString("pin", "0000") ?: "0000",
        parentalEnabled = p.getBoolean("pin_on", false),
        autoRefreshHours = p.getInt("refresh_h", 12),
        aspectMode = p.getInt("aspect", 0)
    )
}

private fun saveSettings(context: Context, s: AppSettings) {
    prefs(context).edit()
        .putString("ua", s.userAgent)
        .putBoolean("large_buf", s.largeBuffer)
        .putBoolean("hw_dec", s.hardwareDecoding)
        .putBoolean("hide_adult", s.hideAdult)
        .putString("pin", s.parentalPin)
        .putBoolean("pin_on", s.parentalEnabled)
        .putInt("refresh_h", s.autoRefreshHours)
        .putInt("aspect", s.aspectMode)
        .apply()
}

private fun loadAccounts(context: Context): List<AccountProfile> {
    val raw = prefs(context).getString("accounts_json", "[]") ?: "[]"
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            AccountProfile(
                id = o.optString("id"),
                name = o.optString("name"),
                mode = o.optString("mode"),
                m3uUrl = o.optString("m3uUrl"),
                server = o.optString("server"),
                username = o.optString("username"),
                password = o.optString("password")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveAccounts(context: Context, list: List<AccountProfile>) {
    val arr = JSONArray()
    list.forEach { acc ->
        arr.put(
            JSONObject()
                .put("id", acc.id)
                .put("name", acc.name)
                .put("mode", acc.mode)
                .put("m3uUrl", acc.m3uUrl)
                .put("server", acc.server)
                .put("username", acc.username)
                .put("password", acc.password)
        )
    }
    prefs(context).edit().putString("accounts_json", arr.toString()).apply()
}

private fun loadActiveAccountId(context: Context) = prefs(context).getString("active_acc", "").orEmpty()
private fun saveActiveAccountId(context: Context, id: String) = prefs(context).edit().putString("active_acc", id).apply()

private fun loadFavorites(context: Context): Set<String> = prefs(context).getStringSet("favs", emptySet())?.toSet() ?: emptySet()
private fun saveFavorites(context: Context, favs: Set<String>) = prefs(context).edit().putStringSet("favs", favs).apply()
