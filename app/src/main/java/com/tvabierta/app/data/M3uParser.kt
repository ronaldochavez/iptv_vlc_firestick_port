package com.tvabierta.app.data

/**
 * Parses extended M3U playlists (the iptv-org / world_ip_tv format):
 *
 * ```
 * #EXTINF:-1 tvg-logo="https://…/logo.png" group-title="Mexico",ADN 40 (720p) [Not 24/7]
 * #EXTVLCOPT:http-user-agent=Mozilla/5.0
 * https://…/stream.m3u8
 * ```
 */
object M3uParser {
    private val attrRegex = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")
    private val qualityRegex = Regex("""\((\d{3,4}[pPiI])\)""")
    private val tagRegex = Regex("""\[([^\]]*)]""")
    private val spaces = Regex("""\s+""")

    fun parse(text: String, origin: String? = null): List<Channel> {
        val channels = ArrayList<Channel>()
        var extinf: String? = null
        var groupOverride: String? = null
        val options = ArrayList<String>()

        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("﻿")
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    extinf = line
                    groupOverride = null
                    options.clear()
                }
                line.startsWith("#EXTVLCOPT", ignoreCase = true) ->
                    line.substringAfter(':', "").trim().takeIf { it.isNotEmpty() }?.let { options += ":$it" }
                line.startsWith("#EXTGRP", ignoreCase = true) ->
                    groupOverride = line.substringAfter(':', "").trim().ifEmpty { null }
                line.startsWith("#") -> Unit
                else -> {
                    channels += toChannel(extinf, line, groupOverride, options, origin)
                    extinf = null
                    groupOverride = null
                    options.clear()
                }
            }
        }
        return channels
    }

    private fun toChannel(
        extinf: String?,
        urlLine: String,
        groupOverride: String?,
        vlcOptions: List<String>,
        origin: String?,
    ): Channel {
        val attrs = extinf
            ?.let { line -> attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() } }
            .orEmpty()
        // The title is whatever follows the comma once the quoted attributes (which may hold commas) are gone.
        val title = extinf?.let { attrRegex.replace(it, "").substringAfter(',', "").trim() }.orEmpty()
        val tags = tagRegex.findAll(title).map { it.groupValues[1].lowercase() }.toList()

        // Kodi-style headers: "http://host/stream.m3u8|User-Agent=abc&Referer=http://x"
        val url = urlLine.substringBefore('|').trim()
        val pipeHeaders = urlLine.substringAfter('|', "").split('&')
            .mapNotNull { pair -> pair.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim().lowercase() to it[1].trim() } }
            .toMap()

        val userAgent = attrs["http-user-agent"] ?: attrs["user-agent"] ?: pipeHeaders["user-agent"]
        val referrer = attrs["http-referrer"] ?: attrs["referrer"] ?: pipeHeaders["referer"] ?: pipeHeaders["referrer"]
        val options = buildList {
            addAll(vlcOptions)
            userAgent?.takeIf { it.isNotEmpty() }?.let { add(":http-user-agent=$it") }
            referrer?.takeIf { it.isNotEmpty() }?.let { add(":http-referrer=$it") }
        }.distinct()

        val name = title.replace(qualityRegex, " ").replace(tagRegex, " ").replace(spaces, " ").trim()
            .ifEmpty { attrs["tvg-name"].orEmpty() }
            .ifEmpty { fallbackName(url) }

        return Channel(
            name = name,
            url = url,
            logo = attrs["tvg-logo"]?.takeIf { it.startsWith("http", ignoreCase = true) },
            group = groupOverride ?: attrs["group-title"]?.ifEmpty { null },
            quality = qualityRegex.find(title)?.groupValues?.get(1)?.lowercase(),
            notAlways = tags.any { "24/7" in it },
            geoBlocked = tags.any { "geo" in it },
            options = options,
            origin = origin,
        )
    }

    fun fallbackName(url: String): String =
        url.substringBefore('?').trimEnd('/').substringAfterLast('/').ifEmpty { url }
}
