package se.ulf.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
        setContent {
            UlfTvApp(store)
        }
    }
}

private val BgDark = Color(0xFF0A0A0F)
private val SurfaceGlass = Color(0x18FFFFFF)
private val BorderGlass = Color(0x28FFFFFF)

@Composable
fun UlfTvApp(store: Store) {
    val scope = rememberCoroutineScope()
    var activeTab by remember { mutableStateOf(AppTab.HOME) }
    var channels by remember { mutableStateOf(DemoChannels.list) }
    var isDemo by remember { mutableStateOf(true) }
    var loadingMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    var favorites by remember { mutableStateOf(store.getFavorites()) }
    var recents by remember { mutableStateOf(store.getRecents()) }
    var language by remember { mutableStateOf(store.language) }
    var bigBuffer by remember { mutableStateOf(store.bigBuffer) }

    var playingChannel by remember { mutableStateOf<Channel?>(null) }
    var playingList by remember { mutableStateOf<List<Channel>>(emptyList()) }

    val sv = language == "sv"

    fun loadSavedSource() {
        val m3u = store.m3uUrl
        val xtServer = store.xtreamServer
        if (m3u.isNotBlank()) {
            scope.launch {
                loadingMessage = if (sv) "Laddar M3U-spellista..." else "Loading M3U playlist..."
                PlaylistLoader.loadM3u(m3u).onSuccess { list ->
                    if (list.isNotEmpty()) {
                        channels = list
                        isDemo = false
                        statusMessage = if (sv) "${list.size} kanaler laddade" else "${list.size} channels loaded"
                    } else {
                        statusMessage = if (sv) "Listan innehöll inga kanaler" else "No channels found"
                    }
                }.onFailure {
                    statusMessage = "Fel: ${it.message}"
                }
                loadingMessage = null
            }
        } else if (xtServer.isNotBlank() && store.xtreamUser.isNotBlank()) {
            scope.launch {
                loadingMessage = if (sv) "Ansluter till Xtream..." else "Connecting to Xtream..."
                PlaylistLoader.loadXtream(xtServer, store.xtreamUser, store.xtreamPass).onSuccess { list ->
                    if (list.isNotEmpty()) {
                        channels = list
                        isDemo = false
                        statusMessage = if (sv) "${list.size} kanaler laddade" else "${list.size} channels loaded"
                    }
                }.onFailure {
                    statusMessage = "Fel: ${it.message}"
                }
                loadingMessage = null
            }
        }
    }

    LaunchedEffect(Unit) { loadSavedSource() }

    val webSetup = remember {
        RemoteWebSetup(
            onM3uReceived = { url ->
                store.clearSource()
                store.m3uUrl = url
                loadSavedSource()
            },
            onXtreamReceived = { server, user, pass ->
                store.clearSource()
                store.xtreamServer = server
                store.xtreamUser = user
                store.xtreamPass = pass
                loadSavedSource()
            }
        )
    }

    DisposableEffect(activeTab) {
        if (activeTab == AppTab.SETTINGS) {
            webSetup.start(scope)
        } else {
            webSetup.stop()
        }
        onDispose { webSetup.stop() }
    }

    if (playingChannel != null) {
        PlayerScreen(
            channels = playingList.ifEmpty { channels },
            initialChannel = playingChannel!!,
            isFavorite = { favorites.contains(it) },
            bigBuffer = bigBuffer,
            onToggleFavorite = { id -> favorites = store.toggleFavorite(id) },
            onClose = { playingChannel = null }
        )
        return
    }

    val startChannel: (Channel, List<Channel>) -> Unit = { ch, list ->
        recents = store.addRecent(ch.id)
        playingList = list
        playingChannel = ch
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF141828), BgDark),
                    radius = 1200f
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TvTopNavBar(
                activeTab = activeTab,
                sv = sv,
                isDemo = isDemo,
                onSelect = { activeTab = it }
            )

            if (loadingMessage != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(16.dp))
                        Text(loadingMessage!!, color = Color.White, fontSize = 18.sp)
                    }
                }
            } else {
                when (activeTab) {
                    AppTab.HOME -> HomeTab(
                        channels = channels,
                        favorites = favorites,
                        recents = recents,
                        sv = sv,
                        onPlay = startChannel
                    )
                    AppTab.LIVE -> LiveTab(
                        channels = channels,
                        favorites = favorites,
                        sv = sv,
                        onPlay = startChannel
                    )
                    AppTab.FAVORITES -> FavoritesTab(
                        channels = channels.filter { favorites.contains(it.id) },
                        sv = sv,
                        onPlay = startChannel
                    )
                    AppTab.SEARCH -> SearchTab(
                        channels = channels,
                        sv = sv,
                        onPlay = startChannel
                    )
                    AppTab.SETTINGS -> SettingsTab(
                        store = store,
                        localIp = webSetup.getLocalIpAddress(),
                        sv = sv,
                        bigBuffer = bigBuffer,
                        statusMessage = statusMessage,
                        onToggleLang = {
                            language = if (language == "sv") "en" else "sv"
                            store.language = language
                        },
                        onToggleBuffer = {
                            bigBuffer = !bigBuffer
                            store.bigBuffer = bigBuffer
                        },
                        onSaveM3u = { url ->
                            store.clearSource()
                            store.m3uUrl = url
                            loadSavedSource()
                        },
                        onResetDemo = {
                            store.clearSource()
                            channels = DemoChannels.list
                            isDemo = true
                            statusMessage = if (sv) "Återställd till demoläge" else "Reset to demo mode"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TvTopNavBar(
    activeTab: AppTab,
    sv: Boolean,
    isDemo: Boolean,
    onSelect: (AppTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 44.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Ulf TV",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            if (isDemo) {
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0x3338BDF8))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text("DEMO", color = Color(0xFF7DD3FC), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(SurfaceGlass)
                .border(1.dp, BorderGlass, CircleShape)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AppTab.entries.forEach { tab ->
                val selected = tab == activeTab
                var focused by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "tabScale")
                Box(
                    modifier = Modifier
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                        .clip(CircleShape)
                        .background(
                            when {
                                focused -> Color.White
                                selected -> Color.White.copy(alpha = 0.22f)
                                else -> Color.Transparent
                            }
                        )
                        .onFocusChanged { focused = it.isFocused }
                        .focusable()
                        .clickable { onSelect(tab) }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (sv) tab.svLabel else tab.enLabel,
                        color = if (focused) Color.Black else Color.White,
                        fontSize = 14.sp,
                        fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTab(
    channels: List<Channel>,
    favorites: Set<String>,
    recents: List<String>,
    sv: Boolean,
    onPlay: (Channel, List<Channel>) -> Unit
) {
    val hero = channels.firstOrNull()
    val recentChannels = remember(channels, recents) {
        val byId = channels.associateBy { it.id }
        recents.mapNotNull { byId[it] }
    }
    val favChannels = remember(channels, favorites) {
        channels.filter { favorites.contains(it.id) }
    }
    val grouped = remember(channels) {
        channels.groupBy { it.group }.entries.take(8).toList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        if (hero != null) {
            item {
                HeroBanner(channel = hero, sv = sv, onPlay = { onPlay(hero, channels) })
            }
        }
        if (recentChannels.isNotEmpty()) {
            item {
                ChannelRow(
                    title = if (sv) "Fortsätt titta" else "Continue Watching",
                    items = recentChannels,
                    favorites = favorites,
                    onPlay = { onPlay(it, recentChannels) }
                )
            }
        }
        if (favChannels.isNotEmpty()) {
            item {
                ChannelRow(
                    title = if (sv) "Dina favoriter" else "Your Favorites",
                    items = favChannels,
                    favorites = favorites,
                    onPlay = { onPlay(it, favChannels) }
                )
            }
        }
        grouped.forEach { (groupName, list) ->
            item {
                ChannelRow(
                    title = groupName,
                    items = list.take(25),
                    favorites = favorites,
                    onPlay = { onPlay(it, list) }
                )
            }
        }
    }
}

@Composable
private fun HeroBanner(channel: Channel, sv: Boolean, onPlay: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, label = "heroScale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 44.dp)
            .height(230.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF161B2E))
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) Color.White else BorderGlass,
                shape = RoundedCornerShape(24.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable { onPlay() }
    ) {
        if (channel.logo.isNotBlank()) {
            AsyncImage(
                model = channel.logo,
                contentDescription = channel.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xF00A0A0F), Color(0xAA0A0A0F), Color(0x330A0A0F))
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(32.dp)
                .fillMaxWidth(0.65f)
        ) {
            Text(
                text = channel.group.uppercase(),
                color = Color(0xFF93C5FD),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (channel.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = channel.description,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (sv) "Spela nu" else "Play Now",
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ChannelRow(
    title: String,
    items: List<Channel>,
    favorites: Set<String>,
    onPlay: (Channel) -> Unit
) {
    Column {
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 44.dp, bottom = 12.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 44.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items(items, key = { it.id }) { ch ->
                TvChannelCard(
                    channel = ch,
                    isFav = favorites.contains(ch.id),
                    onClick = { onPlay(ch) }
                )
            }
        }
    }
}

@Composable
private fun TvChannelCard(channel: Channel, isFav: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "cardScale")

    Column(
        modifier = Modifier
            .width(210.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF171B2A))
                .border(
                    width = if (focused) 2.5.dp else 1.dp,
                    color = if (focused) Color.White else BorderGlass,
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (channel.logo.isNotBlank()) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Tv,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(38.dp)
                )
            }
            if (isFav) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFFB7185),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(18.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = channel.name,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.82f),
            fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LiveTab(
    channels: List<Channel>,
    favorites: Set<String>,
    sv: Boolean,
    onPlay: (Channel, List<Channel>) -> Unit
) {
    val groups = remember(channels) { listOf(if (sv) "Alla" else "All") + channels.map { it.group }.distinct() }
    var selectedGroup by remember(groups) { mutableStateOf(groups.first()) }
    val filtered = remember(channels, selectedGroup) {
        if (selectedGroup == groups.first()) channels else channels.filter { it.group == selectedGroup }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 44.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .width(230.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(groups) { group ->
                val isSelected = group == selectedGroup
                var focused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                focused -> Color.White
                                isSelected -> Color.White.copy(alpha = 0.2f)
                                else -> SurfaceGlass
                            }
                        )
                        .onFocusChanged { focused = it.isFocused }
                        .focusable()
                        .clickable { selectedGroup = group }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = group,
                        color = if (focused) Color.Black else Color.White,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            items(filtered.take(300), key = { it.id }) { ch ->
                var focused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (focused) Color.White else SurfaceGlass)
                        .border(1.dp, if (focused) Color.White else BorderGlass, RoundedCornerShape(14.dp))
                        .onFocusChanged { focused = it.isFocused }
                        .focusable()
                        .clickable { onPlay(ch, filtered) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ch.name,
                            color = if (focused) Color.Black else Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = ch.group,
                            color = if (focused) Color.DarkGray else Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                    if (favorites.contains(ch.id)) {
                        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color(0xFFFB7185), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesTab(
    channels: List<Channel>,
    sv: Boolean,
    onPlay: (Channel, List<Channel>) -> Unit
) {
    if (channels.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (sv) "Inga favoriter ännu. Tryck Höger på fjärrkontrollen i spelaren för att spara." else "No favorites yet. Press Right in the player to add one.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 44.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(channels, key = { it.id }) { ch ->
                var focused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (focused) Color.White else SurfaceGlass)
                        .onFocusChanged { focused = it.isFocused }
                        .focusable()
                        .clickable { onPlay(ch, channels) }
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color(0xFFFB7185))
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = ch.name,
                        color = if (focused) Color.Black else Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchTab(
    channels: List<Channel>,
    sv: Boolean,
    onPlay: (Channel, List<Channel>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, channels) {
        if (query.length < 2) emptyList()
        else channels.filter { it.name.contains(query, ignoreCase = true) || it.group.contains(query, ignoreCase = true) }.take(60)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 44.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(if (sv) "Sök kanal eller kategori..." else "Search channel or category...", color = Color.Gray) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = BorderGlass
            ),
            modifier = Modifier.fillMaxWidth(0.6f)
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(results, key = { it.id }) { ch ->
                var focused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (focused) Color.White else SurfaceGlass)
                        .onFocusChanged { focused = it.isFocused }
                        .focusable()
                        .clickable { onPlay(ch, results) }
                        .padding(16.dp)
                ) {
                    Text(ch.name, color = if (focused) Color.Black else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    store: Store,
    localIp: String?,
    sv: Boolean,
    bigBuffer: Boolean,
    statusMessage: String?,
    onToggleLang: () -> Unit,
    onToggleBuffer: () -> Unit,
    onSaveM3u: (String) -> Unit,
    onResetDemo: () -> Unit
) {
    var m3uInput by remember { mutableStateOf(store.m3uUrl) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 44.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        if (statusMessage != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x3322C55E))
                        .padding(14.dp)
                ) {
                    Text(statusMessage, color = Color(0xFF86EFAC), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceGlass)
                    .border(1.dp, BorderGlass, RoundedCornerShape(18.dp))
                    .padding(20.dp)
            ) {
                Text(
                    text = if (sv) "Lägg in källa från mobilen/datorn (samma Wi-Fi)" else "Add source from phone/PC (same Wi-Fi)",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (localIp != null) {
                        if (sv) "Öppna http://$localIp:8088 i webbläsaren för att klistra in M3U eller Xtream Codes utan fjärrkontroll."
                        else "Open http://$localIp:8088 in your browser to paste M3U or Xtream Codes easily."
                    } else {
                        if (sv) "Anslut TV:n till Wi-Fi eller nätverkskabel." else "Connect TV to Wi-Fi or Ethernet."
                    },
                    color = Color(0xFF93C5FD),
                    fontSize = 15.sp
                )
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = m3uInput,
                    onValueChange = { m3uInput = it },
                    placeholder = { Text("https://exempel.se/playlist.m3u", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = BorderGlass
                    ),
                    modifier = Modifier.weight(1f)
                )
                TvGlassButton(
                    label = if (sv) "Ladda M3U" else "Load M3U",
                    onClick = { if (m3uInput.startsWith("http", true)) onSaveM3u(m3uInput) }
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvGlassButton(
                    label = if (sv) "Språk: Svenska" else "Language: English",
                    onClick = onToggleLang
                )
                TvGlassButton(
                    label = if (sv) "Stor buffert: ${if (bigBuffer) "PÅ" else "AV"}" else "Large Buffer: ${if (bigBuffer) "ON" else "OFF"}",
                    onClick = onToggleBuffer
                )
                TvGlassButton(
                    label = if (sv) "Återställ demo" else "Reset Demo",
                    onClick = onResetDemo
                )
            }
        }

        item {
            Text(
                text = if (sv) {
                    "Ulf TV är en ren mediaspelare utan eget innehåll och utan spårning. Använd endast lagliga källor som du har rätt att titta på."
                } else {
                    "Ulf TV is a media player with no built-in content and no tracking. Use only legal sources you are authorized to view."
                },
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun TvGlassButton(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "btnScale")
    Box(
        modifier = Modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) Color.White else SurfaceGlass)
            .border(1.dp, if (focused) Color.White else BorderGlass, RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(
            text = label,
            color = if (focused) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
