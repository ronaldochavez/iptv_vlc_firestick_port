package com.tvabierta.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tvabierta.app.TvApp
import com.tvabierta.app.data.Channel
import com.tvabierta.app.data.Countries
import com.tvabierta.app.data.Country
import com.tvabierta.app.data.M3uParser
import com.tvabierta.app.data.SavedPlaylist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer

/** What the channel grid is showing. */
sealed interface Source {
    val key: String
    val title: String

    data class CountrySource(val country: Country) : Source {
        override val key get() = "country:${country.code}"
        override val title get() = country.label
    }

    data object Favorites : Source {
        override val key = "favorites"
        override val title = "⭐ Favoritos"
    }

    data object Recents : Source {
        override val key = "recents"
        override val title = "🕘 Vistos recientemente"
    }

    data class Playlist(val playlist: SavedPlaylist) : Source {
        override val key get() = "playlist:${playlist.url}"
        override val title get() = "📃 ${playlist.name}"
    }
}

data class BrowseState(
    val source: Source,
    val loading: Boolean = false,
    val channels: List<Channel> = emptyList(),
    val error: String? = null,
    /** The download failed and the grid shows the copy saved last time. */
    val offline: Boolean = false,
)

/** A playing list: the player zaps through [channels] starting at [startIndex]. */
data class PlayerSession(val channels: List<Channel>, val startIndex: Int, val title: String)

/** Where the browser puts D-pad focus when it (re)appears. */
sealed interface FocusTarget {
    data object FirstChannel : FocusTarget
    data class ChannelUrl(val url: String) : FocusTarget
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TvApp
    val store = app.store
    private val repository = app.playlists

    // Starts as loading so the first frame doesn't flash an empty list before init's select() runs.
    private val remote = MutableStateFlow(BrowseState(restoreSource(), loading = true))

    /** The selected source with live favorites/recents merged in. */
    val browse: StateFlow<BrowseState> = combine(remote, store.favorites, store.recents) { state, favorites, recents ->
        when (state.source) {
            Source.Favorites -> state.copy(channels = favorites)
            Source.Recents -> state.copy(channels = recents)
            else -> state
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, remote.value)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val favoriteUrls: StateFlow<Set<String>> = store.favorites
        .map { list -> list.mapTo(HashSet()) { it.url } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val playlists = store.playlists
    val recentCountries = store.recentCountries

    private val _session = MutableStateFlow<PlayerSession?>(null)
    val session: StateFlow<PlayerSession?> = _session.asStateFlow()

    private var lastPlayedUrl: String? = null
    private var loadJob: Job? = null

    init {
        select(remote.value.source)
    }

    fun select(source: Source, force: Boolean = false) {
        loadJob?.cancel()
        if (source.key != browse.value.source.key) _query.value = ""
        store.lastSource = source.key
        when (source) {
            Source.Favorites, Source.Recents -> remote.value = BrowseState(source)
            is Source.CountrySource -> {
                store.addRecentCountry(source.country.code)
                load(source, source.country.playlistUrl, "country-${source.country.code}", source.country.label, force)
            }
            is Source.Playlist ->
                load(source, source.playlist.url, "playlist-${source.playlist.url.hashCode()}", source.playlist.name, force)
        }
    }

    fun refresh() = select(browse.value.source, force = true)

    fun setQuery(value: String) {
        _query.value = value
    }

    private fun load(source: Source, url: String, cacheKey: String, origin: String, force: Boolean) {
        remote.value = BrowseState(source, loading = true)
        loadJob = viewModelScope.launch {
            remote.value = try {
                val result = repository.load(cacheKey, url, origin, force)
                BrowseState(source, channels = result.channels, offline = result.offline)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BrowseState(source, error = "No se pudo descargar la lista de canales.\n${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun play(channels: List<Channel>, index: Int) {
        if (channels.isEmpty()) return
        _session.value = PlayerSession(channels, index.coerceIn(channels.indices), browse.value.source.title)
    }

    /** "Abrir URL": play a single stream the user typed, like VLC's network stream. */
    fun playUrl(url: String) {
        val channel = Channel(name = hostOf(url), url = url, origin = "URL")
        _session.value = PlayerSession(listOf(channel), 0, "URL")
    }

    fun onChannelStarted(channel: Channel) {
        lastPlayedUrl = channel.url
        store.addRecent(channel)
    }

    fun closePlayer() {
        _session.value = null
    }

    /** Focus for the browser after it reappears: the last watched channel, or the first one. */
    fun browserFocusTarget(): FocusTarget = lastPlayedUrl?.let(FocusTarget::ChannelUrl) ?: FocusTarget.FirstChannel

    fun toggleFavorite(channel: Channel): Boolean = store.toggleFavorite(channel)

    fun savePlaylist(name: String, url: String) {
        val playlist = SavedPlaylist(name.trim().ifEmpty { hostOf(url) }, url.trim())
        store.addPlaylist(playlist)
        select(Source.Playlist(playlist))
    }

    fun removePlaylist(playlist: SavedPlaylist) {
        store.removePlaylist(playlist.url)
        if (browse.value.source.key == Source.Playlist(playlist).key) select(Source.CountrySource(Countries.default()))
    }

    /** A stream or playlist opened from another app (VIEW intent). */
    fun openExternal(url: String, mimeType: String?) {
        val path = url.substringBefore('?').lowercase()
        val isPlaylist = path.endsWith(".m3u") || (mimeType?.contains("mpegurl") == true && !path.endsWith(".m3u8"))
        if (isPlaylist) savePlaylist("", url) else playUrl(url)
    }

    private fun restoreSource(): Source {
        val key = store.lastSource ?: return Source.CountrySource(Countries.default())
        return when {
            key == Source.Favorites.key -> Source.Favorites
            key == Source.Recents.key -> Source.Recents
            key.startsWith("country:") -> Countries.byCode(key.removePrefix("country:"))?.let(Source::CountrySource)
            key.startsWith("playlist:") ->
                store.playlists.value.firstOrNull { it.url == key.removePrefix("playlist:") }?.let(Source::Playlist)
            else -> null
        } ?: Source.CountrySource(Countries.default())
    }

    private fun hostOf(url: String): String =
        url.substringAfter("://").substringBefore('/').substringBefore(':').ifEmpty { M3uParser.fallbackName(url) }

    companion object {
        private val marks = Regex("\\p{Mn}+")

        /** Case- and accent-insensitive form for searching ("Televisión" matches "television"). */
        private fun String.fold(): String = Normalizer.normalize(trim(), Normalizer.Form.NFD).replace(marks, "").lowercase()

        fun filter(channels: List<Channel>, query: String): List<Channel> {
            val needle = query.fold()
            return if (needle.isEmpty()) channels else channels.filter { needle in it.name.fold() }
        }
    }
}
