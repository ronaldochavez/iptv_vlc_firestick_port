package com.tvabierta.app.data

import kotlinx.serialization.Serializable

/** One playable entry of an M3U playlist. [url] identifies the channel (favorites, recents). */
@Serializable
data class Channel(
    val name: String,
    val url: String,
    val logo: String? = null,
    val group: String? = null,
    /** Advertised resolution taken from the title, e.g. "720p". */
    val quality: String? = null,
    /** Title carried "[Not 24/7]": the stream is only live part of the day. */
    val notAlways: Boolean = false,
    /** Title carried "[Geo-blocked]": may only play from its own country. */
    val geoBlocked: Boolean = false,
    /** Per-stream libVLC options (":http-user-agent=…", ":http-referrer=…"). */
    val options: List<String> = emptyList(),
    /** Where the channel came from, for display ("🇲🇽 México"). */
    val origin: String? = null,
)

/** An M3U list the user added by URL. */
@Serializable
data class SavedPlaylist(val name: String, val url: String)
