package com.tvabierta.app.ui.browser

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tvabierta.app.data.Channel
import com.tvabierta.app.data.Countries
import com.tvabierta.app.data.SavedPlaylist
import com.tvabierta.app.ui.FocusTarget
import com.tvabierta.app.ui.MainViewModel
import com.tvabierta.app.ui.Source
import com.tvabierta.app.ui.components.Badge
import com.tvabierta.app.ui.components.ChannelLogo
import com.tvabierta.app.ui.components.TvButton
import com.tvabierta.app.ui.components.TvClickable
import com.tvabierta.app.ui.theme.Palette
import kotlinx.coroutines.launch
import java.text.Normalizer

private sealed interface BrowserDialog {
    data object OpenUrl : BrowserDialog
    data object Search : BrowserDialog
    data class RemovePlaylist(val playlist: SavedPlaylist) : BrowserDialog
}

/** Rows of the left rail: section titles and selectable entries. */
private sealed interface RailEntry {
    val key: String
}

private class RailHeader(val text: String) : RailEntry {
    override val key = "header:$text"
}

private class RailItem(
    override val key: String,
    val icon: String,
    val label: String,
    /** [Source.key] this row selects, so it can show as selected. */
    val sourceKey: String?,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
    val trailing: String? = null,
) : RailEntry

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BrowserScreen(vm: MainViewModel, railState: LazyListState, gridState: LazyGridState) {
    val state by vm.browse.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val channels = remember(state.channels, query) { MainViewModel.filter(state.channels, query) }
    val favoriteUrls by vm.favoriteUrls.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val recentCountries by vm.recentCountries.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var dialog by remember { mutableStateOf<BrowserDialog?>(null) }
    var pendingFocus by remember { mutableStateOf<FocusTarget?>(vm.browserFocusTarget()) }
    val channelFocus = remember { HashMap<Int, FocusRequester>() }
    val railFocus = remember { HashMap<Int, FocusRequester>() }
    var focusedRailIndex by remember { mutableIntStateOf(-1) }
    var focusedChannelIndex by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()

    fun choose(source: Source) {
        vm.select(source)
        pendingFocus = FocusTarget.FirstChannel
    }

    fun toggleFavorite(channel: Channel) {
        val added = vm.toggleFavorite(channel)
        Toast.makeText(context, if (added) "★ Añadido a favoritos" else "Quitado de favoritos", Toast.LENGTH_SHORT).show()
    }

    val entries = buildList {
        add(RailHeader("BIBLIOTECA"))
        add(RailItem("favorites", "⭐", "Favoritos", Source.Favorites.key, { choose(Source.Favorites) }, trailing = favoriteUrls.size.takeIf { it > 0 }?.toString()))
        add(RailItem("recents", "🕘", "Recientes", Source.Recents.key, { choose(Source.Recents) }))
        add(RailItem("open", "➕", "Abrir URL o lista…", null, { dialog = BrowserDialog.OpenUrl }))
        playlists.forEach { playlist ->
            val source = Source.Playlist(playlist)
            add(RailItem("pl:${playlist.url}", "📃", playlist.name, source.key, { choose(source) }, { dialog = BrowserDialog.RemovePlaylist(playlist) }))
        }
        val recent = recentCountries.mapNotNull(Countries::byCode)
        if (recent.isNotEmpty()) {
            add(RailHeader("TUS PAÍSES"))
            recent.forEach { country ->
                val source = Source.CountrySource(country)
                add(RailItem("recent:${country.code}", country.flag, country.name, source.key, { choose(source) }))
            }
        }
        add(RailHeader(ALL_COUNTRIES))
        Countries.all.forEach { country ->
            val source = Source.CountrySource(country)
            add(RailItem("$ALL_PREFIX${country.code}", country.flag, country.name, source.key, { choose(source) }))
        }
    }
    val selectedRailIndex = entries.indexOfFirst { it is RailItem && it.sourceKey == state.source.key }

    suspend fun focusRailNow(index: Int) {
        if (railState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            railState.scrollToItem((index - 3).coerceAtLeast(0))
        }
        focusWhenReady({ railFocus[index] }, { focusedRailIndex == index })
    }

    fun focusRail(index: Int) {
        scope.launch { focusRailNow(index) }
    }

    // ⏪ ⏩ on the remote jump through the country list by initial letter.
    fun jumpLetter(forward: Boolean) {
        val countries = entries.indices.filter { entries[it].key.startsWith(ALL_PREFIX) }
        if (countries.isEmpty()) return
        fun letter(i: Int) = initialOf((entries[i] as RailItem).label)
        val from = focusedRailIndex
        val inCountries = from in countries
        val target = if (forward) {
            if (!inCountries) countries.first() else countries.firstOrNull { it > from && letter(it) != letter(from) }
        } else {
            val previous = countries.lastOrNull { it < from && (!inCountries || letter(it) != letter(from)) }
            previous?.let { p -> countries.first { letter(it) == letter(p) } }
        } ?: return
        focusRail(target)
    }

    // Move D-pad focus to the target once its list is on screen; with nothing to show, park it on the rail.
    LaunchedEffect(pendingFocus, state.loading, channels) {
        val target = pendingFocus ?: return@LaunchedEffect
        if (state.loading) return@LaunchedEffect
        if (channels.isEmpty()) {
            if (selectedRailIndex >= 0) focusRailNow(selectedRailIndex)
            return@LaunchedEffect
        }
        val index = when (target) {
            FocusTarget.FirstChannel -> 0
            is FocusTarget.ChannelUrl -> channels.indexOfFirst { it.url == target.url }.coerceAtLeast(0)
        }
        gridState.scrollToItem(index)
        focusWhenReady({ channelFocus[index] }, { focusedChannelIndex == index })
        pendingFocus = null
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            // Once the user starts moving around, stop steering focus on their behalf.
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && pendingFocus != null) pendingFocus = null
                false
            }
            .padding(horizontal = 36.dp, vertical = 22.dp),
    ) {
        // Left rail: library and countries.
        Column(Modifier.width(240.dp).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp)) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Palette.Brand),
                    contentAlignment = Alignment.Center,
                ) { Text("▶", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(10.dp))
                Text("TV Abierta", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
            }
            LazyColumn(
                state = railState,
                modifier = Modifier
                    .weight(1f)
                    .focusRestorer()
                    .onKeyEvent { event ->
                        val forward = when (event.key) {
                            Key.MediaFastForward -> true
                            Key.MediaRewind -> false
                            else -> return@onKeyEvent false
                        }
                        if (event.type == KeyEventType.KeyDown) jumpLetter(forward)
                        true
                    },
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
                    when (entry) {
                        is RailHeader -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 4.dp),
                        ) {
                            Text(
                                entry.text,
                                color = Palette.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.weight(1f),
                            )
                            if (entry.text == ALL_COUNTRIES) Text("⏪ ⏩ por letra", color = Palette.TextSecondary, fontSize = 11.sp)
                        }
                        is RailItem -> {
                            val requester = remember { FocusRequester() }
                            DisposableEffect(index, requester) {
                                railFocus[index] = requester
                                onDispose { if (railFocus[index] === requester) railFocus.remove(index) }
                            }
                            RailRow(
                                item = entry,
                                selected = entry.sourceKey == state.source.key,
                                modifier = Modifier
                                    .focusRequester(requester)
                                    .onFocusChanged {
                                        if (it.isFocused) focusedRailIndex = index
                                        else if (focusedRailIndex == index) focusedRailIndex = -1
                                    },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.width(20.dp))

        // Right: channels of the selected source.
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        state.source.title,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(subtitle(state.loading, channels.size, state.channels.size, query, state.offline), fontSize = 14.sp, color = Palette.TextSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (query.isNotEmpty()) TvButton("✕  «$query»", onClick = { vm.setQuery("") })
                    TvButton("Buscar", icon = Icons.Default.Search, onClick = { dialog = BrowserDialog.Search })
                    if (state.source is Source.CountrySource || state.source is Source.Playlist) {
                        TvButton("Actualizar", icon = Icons.Default.Refresh, onClick = {
                            vm.refresh()
                            pendingFocus = FocusTarget.FirstChannel
                        })
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Palette.Accent)
                        Spacer(Modifier.height(12.dp))
                        Text("Cargando canales…", color = Palette.TextSecondary)
                    }
                    state.error != null -> Column(
                        Modifier.align(Alignment.Center).width(420.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(state.error.orEmpty(), color = Palette.TextSecondary, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        TvButton("Reintentar", icon = Icons.Default.Refresh, onClick = {
                            vm.refresh()
                            pendingFocus = FocusTarget.FirstChannel
                        })
                    }
                    channels.isEmpty() -> Text(
                        emptyMessage(state.source, query),
                        color = Palette.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).width(420.dp),
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 132.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize().focusRestorer(),
                        contentPadding = PaddingValues(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        itemsIndexed(channels, key = { index, channel -> "$index|${channel.url}" }) { index, channel ->
                            val requester = remember { FocusRequester() }
                            DisposableEffect(index, requester) {
                                channelFocus[index] = requester
                                onDispose { if (channelFocus[index] === requester) channelFocus.remove(index) }
                            }
                            ChannelCard(
                                channel = channel,
                                favorite = channel.url in favoriteUrls,
                                modifier = Modifier
                                    .focusRequester(requester)
                                    .onFocusChanged {
                                        if (it.isFocused) focusedChannelIndex = index
                                        else if (focusedChannelIndex == index) focusedChannelIndex = -1
                                    },
                                onClick = { vm.play(channels, index) },
                                onLongClick = { toggleFavorite(channel) },
                            )
                        }
                    }
                }
            }

            Text(
                "OK  ver canal     ·     Mantén OK o pulsa ≡  favorito     ·     ◀  países",
                color = Palette.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }

    when (val current = dialog) {
        BrowserDialog.OpenUrl -> OpenUrlDialog(
            onPlay = { url ->
                dialog = null
                vm.playUrl(url)
            },
            onSave = { name, url ->
                dialog = null
                vm.savePlaylist(name, url)
                pendingFocus = FocusTarget.FirstChannel
            },
            onDismiss = { dialog = null },
        )
        BrowserDialog.Search -> SearchDialog(
            initial = query,
            onQueryChange = vm::setQuery,
            onDismiss = {
                dialog = null
                pendingFocus = FocusTarget.FirstChannel
            },
        )
        is BrowserDialog.RemovePlaylist -> ConfirmRemoveDialog(
            name = current.playlist.name,
            onConfirm = {
                dialog = null
                vm.removePlaylist(current.playlist)
                pendingFocus = FocusTarget.FirstChannel
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun RailRow(item: RailItem, selected: Boolean, modifier: Modifier = Modifier) {
    TvClickable(
        onClick = item.onClick,
        onLongClick = item.onLongClick,
        modifier = modifier.fillMaxWidth().padding(vertical = 1.dp),
        shape = RoundedCornerShape(8.dp),
        unfocusedColor = if (selected) Palette.Surface else Color.Transparent,
    ) { focused ->
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(item.icon, fontSize = 17.sp, modifier = Modifier.width(30.dp))
            Text(
                item.label,
                fontSize = 15.sp,
                fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) Palette.Accent else Palette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            item.trailing?.let { Text(it, fontSize = 13.sp, color = Palette.TextSecondary) }
        }
    }
}

@Composable
private fun ChannelCard(
    channel: Channel,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvClickable(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        focusedScale = 1.06f,
        unfocusedColor = Palette.Surface,
    ) { focused ->
        Column(Modifier.padding(8.dp)) {
            Box {
                ChannelLogo(channel, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                if (favorite) {
                    Text("★", color = Palette.Accent, fontSize = 16.sp, modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 5.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                channel.name,
                color = Palette.TextPrimary,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 4.dp).height(16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                channel.quality?.let { Badge(it) }
                if (channel.notAlways) Badge("No 24/7")
                if (channel.geoBlocked) Badge("Geo")
            }
        }
    }
}

private const val ALL_COUNTRIES = "TODOS LOS PAÍSES"
private const val ALL_PREFIX = "all:"

private val accents = Regex("\\p{Mn}+")

/** "Árabe" and "Arabia" both file under A. */
private fun initialOf(name: String): Char =
    Normalizer.normalize(name.take(1), Normalizer.Form.NFD).replace(accents, "").firstOrNull()?.uppercaseChar() ?: '#'

/**
 * Lazy rows are only composed during a layout pass, after the frame callback, so their
 * requester may not be attached yet, and a request made before the window has focus is
 * dropped: keep asking for a while until [landed] confirms it.
 */
private suspend fun focusWhenReady(requester: () -> FocusRequester?, landed: () -> Boolean) {
    repeat(60) {
        withFrameNanos { }
        if (landed()) return
        requester()?.let { runCatching { it.requestFocus() } }
    }
}

private fun subtitle(loading: Boolean, shown: Int, total: Int, query: String, offline: Boolean): String = when {
    loading -> "Cargando…"
    else -> buildString {
        append(if (query.isNotEmpty()) "$shown de $total canales" else "$total canales")
        if (offline) append("  ·  sin conexión: mostrando la lista guardada")
    }
}

private fun emptyMessage(source: Source, query: String): String = when {
    query.isNotEmpty() -> "Ningún canal coincide con «$query»."
    source == Source.Favorites -> "Aún no tienes favoritos.\nMantén pulsado OK (o pulsa ≡) sobre un canal para añadirlo."
    source == Source.Recents -> "Aquí aparecerán los canales que veas."
    else -> "Esta lista no tiene canales disponibles en este momento."
}
