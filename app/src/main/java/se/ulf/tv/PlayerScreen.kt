package se.ulf.tv

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channels: List<Channel>,
    initialChannel: Channel,
    isFavorite: (String) -> Boolean,
    bigBuffer: Boolean,
    onToggleFavorite: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var currentIndex by remember(initialChannel.id) {
        mutableIntStateOf(channels.indexOfFirst { it.id == initialChannel.id }.coerceAtLeast(0))
    }
    val currentChannel = channels.getOrNull(currentIndex) ?: initialChannel

    var showOverlay by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }

    val focusRequester = remember { FocusRequester() }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val player = remember(bigBuffer) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                if (bigBuffer) 15000 else 5000,
                if (bigBuffer) 60000 else 30000,
                if (bigBuffer) 3000 else 1500,
                if (bigBuffer) 6000 else 3000
            )
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(PlaylistLoader.httpClient)
            .setUserAgent("UlfTV/1.0")

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    errorText = null
                    retryCount = 0
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (retryCount < 3) {
                    retryCount++
                    errorText = "Återansluter ($retryCount/3)..."
                } else {
                    isBuffering = false
                    errorText = "Kunde inte spela strömmen: ${error.errorCodeName}"
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(currentChannel.url) {
        retryCount = 0
        errorText = null
        isBuffering = true
        showOverlay = true
        player.setMediaItem(MediaItem.fromUri(currentChannel.url))
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(retryCount) {
        if (retryCount in 1..3) {
            delay(retryCount * 2000L)
            player.setMediaItem(MediaItem.fromUri(currentChannel.url))
            player.prepare()
            player.playWhenReady = true
        }
    }

    LaunchedEffect(showOverlay, currentChannel.id) {
        if (showOverlay) {
            delay(4500)
            showOverlay = false
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BackHandler { onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp, Key.ChannelUp -> {
                            if (channels.isNotEmpty()) {
                                currentIndex = (currentIndex - 1 + channels.size) % channels.size
                            }
                            true
                        }
                        Key.DirectionDown, Key.ChannelDown -> {
                            if (channels.isNotEmpty()) {
                                currentIndex = (currentIndex + 1) % channels.size
                            }
                            true
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            showOverlay = !showOverlay
                            true
                        }
                        Key.Menu, Key.DirectionRight -> {
                            onToggleFavorite(currentChannel.id)
                            showOverlay = true
                            true
                        }
                        Key.Back, Key.Escape -> {
                            onClose()
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
                    this.player = player
                    useController = false
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering && errorText == null) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(48.dp)
            )
        }

        if (errorText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC12141E))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Text(text = errorText!!, color = Color.White, fontSize = 16.sp)
            }
        }

        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xE60A0A0F))
                        )
                    )
                    .padding(horizontal = 48.dp, vertical = 32.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xB3161822))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
                        .padding(horizontal = 28.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentChannel.group.uppercase(),
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = currentChannel.name,
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "↑ / ↓ Byt kanal   •   Höger = Favorit   •   Tillbaka = Stäng",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 13.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite(currentChannel.id)) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Favorit",
                            tint = if (isFavorite(currentChannel.id)) Color(0xFFFB7185) else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(28.dp)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
                            Text(
                                text = "${currentIndex + 1}/${channels.size}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}
