package com.tvabierta.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Favorites, recently watched channels, saved playlists and small UI preferences. */
class LibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val channelList = ListSerializer(Channel.serializer())
    private val playlistList = ListSerializer(SavedPlaylist.serializer())

    private val _favorites = MutableStateFlow(read(KEY_FAVORITES, channelList))
    val favorites: StateFlow<List<Channel>> = _favorites.asStateFlow()

    private val _recents = MutableStateFlow(read(KEY_RECENTS, channelList))
    val recents: StateFlow<List<Channel>> = _recents.asStateFlow()

    private val _playlists = MutableStateFlow(read(KEY_PLAYLISTS, playlistList))
    val playlists: StateFlow<List<SavedPlaylist>> = _playlists.asStateFlow()

    private val _recentCountries = MutableStateFlow(
        prefs.getString(KEY_RECENT_COUNTRIES, null)?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
    )
    val recentCountries: StateFlow<List<String>> = _recentCountries.asStateFlow()

    /** Key of the source (country, favorites, playlist…) shown last, restored on launch. */
    var lastSource: String?
        get() = prefs.getString(KEY_LAST_SOURCE, null)
        set(value) = prefs.edit { putString(KEY_LAST_SOURCE, value) }

    /** Index into the player's picture formats ("Ajuste óptimo", "Rellenar", …). */
    var videoScale: Int
        get() = prefs.getInt(KEY_VIDEO_SCALE, 0)
        set(value) = prefs.edit { putInt(KEY_VIDEO_SCALE, value) }

    /** Deinterlace 1080i broadcasts (costs CPU: frames are decoded to memory instead of straight to screen). */
    var deinterlace: Boolean
        get() = prefs.getBoolean(KEY_DEINTERLACE, false)
        set(value) = prefs.edit { putBoolean(KEY_DEINTERLACE, value) }

    fun isFavorite(url: String) = _favorites.value.any { it.url == url }

    /** Returns true when the channel is now a favorite. */
    fun toggleFavorite(channel: Channel): Boolean {
        val add = !isFavorite(channel.url)
        _favorites.value = if (add) _favorites.value + channel else _favorites.value.filterNot { it.url == channel.url }
        write(KEY_FAVORITES, channelList, _favorites.value)
        return add
    }

    fun addRecent(channel: Channel) {
        _recents.value = (listOf(channel) + _recents.value.filterNot { it.url == channel.url }).take(MAX_RECENTS)
        write(KEY_RECENTS, channelList, _recents.value)
    }

    fun addPlaylist(playlist: SavedPlaylist) {
        _playlists.value = _playlists.value.filterNot { it.url == playlist.url } + playlist
        write(KEY_PLAYLISTS, playlistList, _playlists.value)
    }

    fun removePlaylist(url: String) {
        _playlists.value = _playlists.value.filterNot { it.url == url }
        write(KEY_PLAYLISTS, playlistList, _playlists.value)
    }

    fun addRecentCountry(code: String) {
        _recentCountries.value = (listOf(code) + _recentCountries.value.filterNot { it == code }).take(MAX_RECENT_COUNTRIES)
        prefs.edit { putString(KEY_RECENT_COUNTRIES, _recentCountries.value.joinToString(",")) }
    }

    private fun <T> read(key: String, serializer: KSerializer<List<T>>): List<T> =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private fun <T> write(key: String, serializer: KSerializer<List<T>>, value: List<T>) =
        prefs.edit { putString(key, json.encodeToString(serializer, value)) }

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_RECENTS = "recents"
        const val KEY_PLAYLISTS = "playlists"
        const val KEY_RECENT_COUNTRIES = "recent_countries"
        const val KEY_LAST_SOURCE = "last_source"
        const val KEY_VIDEO_SCALE = "video_scale"
        const val KEY_DEINTERLACE = "deinterlace"
        const val MAX_RECENTS = 40
        const val MAX_RECENT_COUNTRIES = 5
    }
}
