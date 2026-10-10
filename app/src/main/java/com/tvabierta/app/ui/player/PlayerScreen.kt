package com.tvabierta.app.ui.player

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnAttach
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tvabierta.app.TvApp
import com.tvabierta.app.data.Channel
import com.tvabierta.app.player.Phase
import com.tvabierta.app.player.PictureFormat
import com.tvabierta.app.player.PlaybackState
import com.tvabierta.app.player.VlcController
import com.tvabierta.app.ui.MainViewModel
import com.tvabierta.app.ui.PlayerSession
import com.tvabierta.app.ui.components.Badge
import com.tvabierta.app.ui.components.ChannelLogo
import com.tvabierta.app.ui.components.TvClickable
import com.tvabierta.app.ui.theme.Palette
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.Date

private enum class Panel { None, Channels, Options }

/** Wait this long after the last ▲▼ before opening a stream, so fast zapping skips channels cheaply. */
private const val ZAP_DELAY_MS = 350L
private const val BANNER_MS = 5_000L
private const val SLOW_AFTER_MS = 20_000L
// Some HLS channels load their audio rendition well before the video one.
private const val AUDIO_ONLY_AFTER_MS = 12_000L

@Composable
fun PlayerScreen(session: PlayerSession, vm: MainViewModel) {
    val context = LocalContext.current
    val app = context.applicationContext as TvApp
    val channels = session.channels
    var index by remember(session) { mutableIntStateOf(session.startIndex) }
    val channel = channels[index]

    val controller = remember {
        VlcController(
            app.libVlc,
            PictureFormat.entries.getOrElse(vm.store.videoScale) { PictureFormat.BestFit },
            deinterlace = vm.store.deinterlace,
        )
    }
    val videoLayout = remember { VLCVideoLayout(context) }
    val playback by controller.state.collectAsStateWithLifecycle()
    val favoriteUrls by vm.favoriteUrls.collectAsStateWithLifecycle()

    var panel by remember { mutableStateOf(Panel.None) }
    var bannerNonce by remember { mutableIntStateOf(0) }
    var bannerVisible by remember { mutableStateOf(true) }
    var started by remember { mutableStateOf(false) }
    var slow by remember { mutableStateOf(false) }
    val rootFocus = remember { FocusRequester() }

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    // The video surface only exists while the activity is visible: stop on Home, resume on return.
    LifecycleStartEffect(controller) {
        videoLayout.doOnAttach {
            controller.attach(videoLayout)
            started = true
        }
        onStopOrDispose {
            started = false
            controller.stop()
            controller.detach()
        }
    }

    LaunchedEffect(index, started) {
        if (!started) return@LaunchedEffect
        slow = false
        delay(ZAP_DELAY_MS)
        val current = channels[index]
        controller.play(current)
        vm.onChannelStarted(current)
        delay(SLOW_AFTER_MS)
        if (controller.state.value.loading) slow = true
    }

    val waitingForVideo = playback.phase == Phase.Playing && !playback.showingVideo
    LaunchedEffect(waitingForVideo) {
        while (waitingForVideo) {
            delay(1_000)
            controller.refreshVideoInfo()
        }
    }

    // Many streams start their audio a moment before the video track shows up: only call a
    // channel audio-only after it has played a while without one.
    var audioOnly by remember { mutableStateOf(false) }
    val maybeAudioOnly = playback.phase == Phase.Playing && playback.videoTracks == 0 && !playback.loading
    LaunchedEffect(maybeAudioOnly, index) {
        audioOnly = false
        if (maybeAudioOnly) {
            delay(AUDIO_ONLY_AFTER_MS)
            audioOnly = true
        }
    }

    LaunchedEffect(bannerNonce) {
        bannerVisible = true
        delay(BANNER_MS)
        bannerVisible = false
    }

    LaunchedEffect(panel) {
        if (panel == Panel.None) {
            withFrameNanos { }
            runCatching { rootFocus.requestFocus() }
        }
    }

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    BackHandler {
        if (panel != Panel.None) panel = Panel.None else vm.closePlayer()
    }

    fun showBanner() {
        bannerNonce++
    }

    fun zap(delta: Int) {
        index = (index + delta).mod(channels.size)
        showBanner()
    }

    fun onKey(event: KeyEvent): Boolean {
        if (panel != Panel.None) return false
        val down = event.type == KeyEventType.KeyDown
        val up = event.type == KeyEventType.KeyUp
        when (event.key) {
            // ▲▼ move through the list as it is drawn; CH+/CH- follow channel numbers.
            Key.DirectionUp, Key.ChannelDown -> if (down) zap(-1)
            Key.DirectionDown, Key.ChannelUp -> if (down) zap(+1)
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionLeft -> if (up) panel = Panel.Channels
            Key.DirectionRight, Key.Menu -> if (up) panel = Panel.Options
            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause, Key.Spacebar -> if (up) {
                controller.togglePause()
                showBanner()
            }
            Key.Info, Key.Guide -> if (up) showBanner()
            else -> return false
        }
        return true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent(::onKey)
            .focusRequester(rootFocus)
            .focusProperties { canFocus = panel == Panel.None }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { panel = if (panel == Panel.None) Panel.Channels else Panel.None })
            },
    ) {
        AndroidView(factory = { videoLayout }, modifier = Modifier.fillMaxSize())

        // Cover the previous channel's last frame until the new stream reports its video.
        if (!playback.showingVideo) Box(Modifier.fillMaxSize().background(Color.Black))

        if (audioOnly) AudioOnly(channel, Modifier.align(Alignment.Center))

        if (!audioOnly) CenterStatus(playback, slow, Modifier.align(Alignment.Center))

        AnimatedVisibility(
            visible = bannerVisible || playback.phase in setOf(Phase.Error, Phase.Ended, Phase.Paused),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            InfoBanner(channel, index + 1, channels.size, session.title, playback, channel.url in favoriteUrls)
        }

        AnimatedVisibility(
            visible = panel == Panel.Channels,
            enter = fadeIn() + slideInHorizontally { -it },
            exit = fadeOut() + slideOutHorizontally { -it },
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            ChannelPanel(
                channels = channels,
                current = index,
                title = session.title,
                favoriteUrls = favoriteUrls,
                onPick = { picked ->
                    if (picked != index) {
                        index = picked
                        showBanner()
                    }
                    panel = Panel.None
                },
                onClose = { panel = Panel.None },
            )
        }

        AnimatedVisibility(
            visible = panel == Panel.Options,
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            OptionsPanel(
                controller = controller,
                playback = playback,
                favorite = channel.url in favoriteUrls,
                onToggleFavorite = { vm.toggleFavorite(channel) },
                onFormat = { vm.store.videoScale = it.ordinal },
                onDeinterlace = { enabled ->
                    vm.store.deinterlace = enabled
                    controller.deinterlace = enabled
                    controller.reload()
                },
                onReload = {
                    slow = false
                    controller.reload()
                    panel = Panel.None
                    showBanner()
                },
                onClose = { panel = Panel.None },
            )
        }
    }
}

@Composable
private fun CenterStatus(playback: PlaybackState, slow: Boolean, modifier: Modifier) {
    when {
        playback.phase == Phase.Error -> MessageCard(
            "No se pudo reproducir este canal",
            "Puede estar caído o bloqueado en tu país. Usa ▲▼ para probar otro.",
            modifier,
        )
        playback.phase == Phase.Ended -> MessageCard(
            "La transmisión se interrumpió",
            "Pulsa ▶ y elige «Recargar canal», o cambia de canal con ▲▼.",
            modifier,
        )
        // A short re-buffer over a moving picture gets a small corner spinner, not a big one.
        playback.loading && playback.showingVideo -> Box(Modifier.fillMaxSize().padding(32.dp)) {
            CircularProgressIndicator(
                color = Palette.Accent,
                strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
            )
        }
        playback.loading || (playback.phase == Phase.Playing && !playback.showingVideo) -> Column(
            modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = Palette.Accent, strokeWidth = 4.dp, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(14.dp))
            val buffering = playback.buffering.toInt()
            val label = when {
                buffering in 1..99 -> "Cargando $buffering%"
                playback.phase == Phase.Playing -> "Cargando video…"
                else -> "Conectando…"
            }
            Text(label, color = Color.White, fontSize = 16.sp)
            if (slow) {
                Spacer(Modifier.height(6.dp))
                Text("Este canal está tardando en responder…", color = Palette.TextSecondary, fontSize = 14.sp)
            }
        }
        playback.phase == Phase.Paused -> MessageCard("⏸  En pausa", "Pulsa ⏯ para continuar.", modifier)
    }
}

@Composable
private fun MessageCard(title: String, body: String, modifier: Modifier) {
    Column(
        modifier
            .width(460.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xE0151A21))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, color = Palette.TextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AudioOnly(channel: Channel, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ChannelLogo(channel, Modifier.size(240.dp, 135.dp), initialsSize = 40.sp)
        Spacer(Modifier.height(12.dp))
        Text("Solo audio", color = Palette.TextSecondary, fontSize = 15.sp)
    }
}

@Composable
private fun InfoBanner(
    channel: Channel,
    number: Int,
    total: Int,
    sessionTitle: String,
    playback: PlaybackState,
    favorite: Boolean,
) {
    val context = LocalContext.current
    val time by produceState(DateFormat.getTimeFormat(context).format(Date())) {
        while (true) {
            delay(10_000)
            value = DateFormat.getTimeFormat(context).format(Date())
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000), Color(0xF2000000))))
            .padding(start = 48.dp, end = 48.dp, top = 56.dp, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(channel, Modifier.size(128.dp, 72.dp), initialsSize = 24.sp)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$number", color = Palette.Accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        channel.name,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (favorite) Text("  ★", color = Palette.Accent, fontSize = 24.sp)
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(channel.origin ?: sessionTitle, color = Palette.TextSecondary, fontSize = 15.sp, maxLines = 1)
                    StatusBadge(playback)
                    (playback.videoSize ?: channel.quality)?.let { Badge(it) }
                    if (channel.notAlways) Badge("No 24/7")
                    if (channel.geoBlocked) Badge("Puede tener bloqueo regional")
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "▲▼ Cambiar canal     OK Lista de canales     ▶ Opciones     Atrás Salir",
                    color = Color(0xFFB8C0CA),
                    fontSize = 12.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(time, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("$number / $total", color = Palette.TextSecondary, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun StatusBadge(playback: PlaybackState) {
    when {
        playback.phase == Phase.Error -> Badge("SIN SEÑAL", color = Color(0xFF5A1F1F), textColor = Color(0xFFFFB3B3))
        playback.phase == Phase.Ended -> Badge("INTERRUMPIDO", color = Color(0xFF5A1F1F), textColor = Color(0xFFFFB3B3))
        playback.phase == Phase.Paused -> Badge("EN PAUSA")
        playback.loading || playback.phase == Phase.Idle -> Badge("CONECTANDO")
        playback.phase == Phase.Playing -> Badge("● EN VIVO", color = Color(0xFFB3261E), textColor = Color.White)
    }
}

@Composable
private fun ChannelPanel(
    channels: List<Channel>,
    current: Int,
    title: String,
    favoriteUrls: Set<String>,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    val currentFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { currentFocus.requestFocus() }
    }
    Column(
        Modifier
            .fillMaxHeight()
            .width(420.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xF50B0E13), Color(0xE60B0E13))))
            .onKeyEvent { event ->
                if (event.key == Key.DirectionRight) {
                    if (event.type == KeyEventType.KeyUp) onClose()
                    true
                } else {
                    false
                }
            }
            .padding(start = 36.dp, end = 14.dp, top = 24.dp, bottom = 16.dp),
    ) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${channels.size} canales  ·  ▶ cerrar", color = Palette.TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 6.dp)) {
            itemsIndexed(channels, key = { i, ch -> "$i|${ch.url}" }) { i, ch ->
                val isCurrent = i == current
                TvClickable(
                    onClick = { onPick(i) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .then(if (isCurrent) Modifier.focusRequester(currentFocus) else Modifier),
                    shape = RoundedCornerShape(8.dp),
                    unfocusedColor = if (isCurrent) Palette.SurfaceHigh else Color.Transparent,
                ) { focused ->
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${i + 1}",
                            color = if (isCurrent) Palette.Accent else Palette.TextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.width(40.dp),
                        )
                        ChannelLogo(ch, Modifier.size(56.dp, 32.dp), initialsSize = 12.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            ch.name,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = if (isCurrent || focused) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (ch.url in favoriteUrls) Text(" ★", color = Palette.Accent, fontSize = 14.sp)
                        if (isCurrent) Text(" ▶", color = Palette.Accent, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionsPanel(
    controller: VlcController,
    playback: PlaybackState,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onFormat: (PictureFormat) -> Unit,
    onDeinterlace: (Boolean) -> Unit,
    onReload: () -> Unit,
    onClose: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    var deinterlace by remember { mutableStateOf(controller.deinterlace) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    var format by remember { mutableStateOf(controller.format) }
    // libVLC lists a "Disable" audio entry too; never offer muting by accident.
    val audioTracks = remember(playback.tracksVersion) { controller.audioTracks().filter { it.id >= 0 } }
    var audioId by remember(playback.tracksVersion) { mutableIntStateOf(controller.currentAudioTrack()) }
    val subtitleTracks = remember(playback.tracksVersion) { controller.subtitleTracks() }
    var subtitleId by remember(playback.tracksVersion) { mutableIntStateOf(controller.currentSubtitleTrack()) }

    Column(
        Modifier
            .fillMaxHeight()
            .width(380.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xE60B0E13), Color(0xF50B0E13))))
            .onKeyEvent { event ->
                if (event.key == Key.DirectionLeft) {
                    if (event.type == KeyEventType.KeyUp) onClose()
                    true
                } else {
                    false
                }
            }
            .padding(start = 18.dp, end = 40.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Opciones", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("◀ cerrar", color = Palette.TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))

        OptionRow(
            title = if (favorite) "★  Quitar de favoritos" else "☆  Añadir a favoritos",
            value = null,
            onClick = onToggleFavorite,
            modifier = Modifier.focusRequester(firstFocus),
        )
        OptionRow("Formato de imagen", format.label, onClick = {
            format = PictureFormat.entries[(format.ordinal + 1) % PictureFormat.entries.size]
            controller.format = format
            onFormat(format)
        })
        OptionRow("Desentrelazar (1080i)", if (deinterlace) "Activado" else "Desactivado", onClick = {
            deinterlace = !deinterlace
            onDeinterlace(deinterlace)
        })
        OptionRow(
            "Audio",
            when {
                audioTracks.isEmpty() -> "—"
                audioTracks.size == 1 -> "Única pista"
                else -> audioTracks.firstOrNull { it.id == audioId }?.name ?: "Pista ${audioTracks.indexOfFirst { it.id == audioId } + 1}"
            },
            onClick = {
                if (audioTracks.size > 1) {
                    val next = audioTracks[(audioTracks.indexOfFirst { it.id == audioId } + 1).mod(audioTracks.size)]
                    if (controller.selectAudioTrack(next.id)) audioId = next.id
                }
            },
        )
        OptionRow(
            "Subtítulos",
            when {
                subtitleTracks.none { it.id >= 0 } -> "No disponibles"
                subtitleId < 0 -> "Desactivados"
                else -> subtitleTracks.firstOrNull { it.id == subtitleId }?.name ?: "Activados"
            },
            onClick = {
                if (subtitleTracks.any { it.id >= 0 }) {
                    val next = subtitleTracks[(subtitleTracks.indexOfFirst { it.id == subtitleId } + 1).mod(subtitleTracks.size)]
                    if (controller.selectSubtitleTrack(next.id)) subtitleId = next.id
                }
            },
        )
        OptionRow("Recargar canal", null, onClick = onReload)

        Spacer(Modifier.weight(1f))
        Text(
            listOfNotNull(playback.videoSize?.let { "Vídeo $it" }, "libVLC").joinToString("  ·  "),
            color = Palette.TextSecondary,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun OptionRow(title: String, value: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvClickable(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), unfocusedColor = Color(0x14FFFFFF)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(title, color = Color.White, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
            value?.let {
                Text(it, color = Palette.Accent, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
