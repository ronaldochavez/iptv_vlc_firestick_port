package com.tvabierta.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Downloads M3U playlists and keeps a copy on disk, so the app starts fast and still
 * works (with the last known list) when GitHub or the network is down.
 */
class PlaylistRepository(context: Context, private val http: OkHttpClient) {
    private val dir = File(context.filesDir, "playlists").apply { mkdirs() }

    data class Result(val channels: List<Channel>, val offline: Boolean)

    suspend fun load(cacheKey: String, url: String, origin: String?, force: Boolean): Result =
        withContext(Dispatchers.IO) {
            val file = File(dir, cacheKey.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".m3u")
            val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS
            if (!force && fresh) return@withContext Result(M3uParser.parse(file.readText(), origin), offline = false)

            try {
                val text = download(url)
                file.writeText(text)
                Result(M3uParser.parse(text, origin), offline = false)
            } catch (e: IOException) {
                if (!file.exists()) throw e
                Result(M3uParser.parse(file.readText(), origin), offline = true)
            }
        }

    private fun download(url: String): String {
        val request = Request.Builder().url(url).build()
        http.newCall(request).execute().use { response ->
            // world_ip_tv drops a country's file when none of its channels pass the check.
            if (response.code == 404) return ""
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private companion object {
        // world_ip_tv re-checks its streams every few hours.
        const val MAX_AGE_MS = 6 * 60 * 60 * 1000L
    }
}
