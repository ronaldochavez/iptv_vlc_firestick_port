package com.tvabierta.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.tvabierta.app.data.LibraryStore
import com.tvabierta.app.data.PlaylistRepository
import okhttp3.OkHttpClient
import org.videolan.libvlc.LibVLC
import java.util.concurrent.TimeUnit

class TvApp : Application(), SingletonImageLoader.Factory {

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val store: LibraryStore by lazy { LibraryStore(this) }

    val playlists: PlaylistRepository by lazy { PlaylistRepository(this, http) }

    /** One libVLC instance for the whole app: creating it loads every VLC module, which is slow. */
    val libVlc: LibVLC by lazy {
        LibVLC(
            this,
            // libVLC appends its own defaults to this list, so it must be mutable.
            arrayListOf(
                "--http-reconnect",
                "--network-caching=$NETWORK_CACHING_MS",
                "--audio-time-stretch",
            ),
        )
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
            .crossfade(true)
            .build()

    companion object {
        const val NETWORK_CACHING_MS = 1500
    }
}
