package com.tvabierta.app.player

import android.net.Uri
import com.tvabierta.app.data.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.concurrent.Executors

enum class Phase { Idle, Opening, Playing, Paused, Ended, Error }

data class PlaybackState(
    val phase: Phase = Phase.Idle,
    /** libVLC's input buffer fill, 0–100. */
    val buffering: Float = 0f,
    /** Video tracks in the stream; 0 while playing means a radio-style, audio-only channel. */
    val videoTracks: Int = 0,
    /** Video outputs drawing this stream; above 0 once pictures reach the screen. */
    val videoOutputs: Int = 0,
    /** Decoded resolution, e.g. "1280×720", once the video track is known. */
    val videoSize: String? = null,
    /** Bumped whenever audio/subtitle tracks change, so menus re-read them. */
    val tracksVersion: Int = 0,
) {
    val loading: Boolean get() = phase == Phase.Opening || (phase == Phase.Playing && buffering > 0f && buffering < 100f)

    /** The current stream is drawing pictures (libVLC doesn't report a size for every stream). */
    val showingVideo: Boolean get() = videoOutputs > 0 || videoSize != null
}

enum class PictureFormat(val label: String, val type: MediaPlayer.ScaleType) {
    BestFit("Ajuste óptimo", MediaPlayer.ScaleType.SURFACE_BEST_FIT),
    FitScreen("Ajustar a pantalla", MediaPlayer.ScaleType.SURFACE_FIT_SCREEN),
    Fill("Estirar", MediaPlayer.ScaleType.SURFACE_FILL),
    Ratio16x9("16:9", MediaPlayer.ScaleType.SURFACE_16_9),
    Ratio4x3("4:3", MediaPlayer.ScaleType.SURFACE_4_3),
    Original("Tamaño original", MediaPlayer.ScaleType.SURFACE_ORIGINAL),
}

data class Track(val id: Int, val name: String)

/**
 * Thin wrapper over a libVLC [MediaPlayer] that exposes its events as [state]. Call it from the
 * main thread; the slow libVLC calls run on a worker thread of its own.
 */
class VlcController(private val libVlc: LibVLC, format: PictureFormat, var deinterlace: Boolean) {
    private val player = MediaPlayer(libVlc)
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /**
     * Opening and stopping a stream waits for libVLC's network threads to wind down, which can
     * take seconds; doing it here (as VLC for Android does) keeps the remote responsive.
     */
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "vlc-player") }
    private var released = false

    private var attached = false
    private var current: Channel? = null

    /** Events still queued from the previous stream arrive until libVLC confirms the media switch. */
    private var awaitingMediaChange = false

    var format: PictureFormat = format
        set(value) {
            field = value
            player.videoScale = value.type
        }

    init {
        player.setEventListener { onEvent(it) }
    }

    fun attach(layout: VLCVideoLayout) {
        if (attached) return
        player.attachViews(layout, null, true, false)
        player.videoScale = format.type
        attached = true
    }

    fun detach() {
        if (!attached) return
        player.detachViews()
        attached = false
    }

    fun play(channel: Channel) {
        if (released) return
        current = channel
        awaitingMediaChange = true
        _state.value = PlaybackState(phase = Phase.Opening)
        val options = buildList {
            if (deinterlace) {
                // Filters can't touch MediaCodec's direct-rendered frames: decode to buffers instead.
                add(":no-mediacodec-dr")
                add(":no-omxil-dr")
                add(":deinterlace=1")
                add(":deinterlace-mode=blend")
            }
            addAll(channel.options)
        }
        worker.execute {
            val media = Media(libVlc, Uri.parse(channel.url))
            media.setHWDecoderEnabled(true, false)
            options.forEach(media::addOption)
            player.media = media
            media.release()
            player.play()
        }
    }

    fun reload() {
        current?.let(::play)
    }

    fun stop() {
        if (released) return
        _state.update { it.copy(phase = Phase.Idle) }
        worker.execute { player.stop() }
    }

    fun togglePause() {
        if (released) return
        val resume = _state.value.phase == Phase.Paused
        worker.execute {
            when {
                resume -> player.play()
                player.isPlaying -> player.pause()
            }
        }
    }

    fun audioTracks(): List<Track> = player.audioTracks?.map { Track(it.id, it.name) }.orEmpty()
    fun currentAudioTrack(): Int = player.getAudioTrack()
    fun selectAudioTrack(id: Int) = player.setAudioTrack(id)

    fun subtitleTracks(): List<Track> = player.spuTracks?.map { Track(it.id, it.name) }.orEmpty()
    fun currentSubtitleTrack(): Int = player.getSpuTrack()
    fun selectSubtitleTrack(id: Int) = player.setSpuTrack(id)

    /** Re-reads the video track; libVLC doesn't always send an event when a reused output starts showing it. */
    fun refreshVideoInfo() {
        val tracks = player.videoTracksCount
        val size = videoSize()
        _state.update { if (it.videoTracks == tracks && it.videoSize == size) it else it.copy(videoTracks = tracks, videoSize = size) }
    }

    fun release() {
        if (released) return
        released = true
        player.setEventListener(null)
        detach()
        worker.execute {
            player.stop()
            player.release()
        }
        worker.shutdown()
    }

    private fun onEvent(event: MediaPlayer.Event) {
        if (event.type == MediaPlayer.Event.MediaChanged) {
            awaitingMediaChange = false
            return
        }
        if (awaitingMediaChange) return
        when (event.type) {
            MediaPlayer.Event.Opening -> _state.update { it.copy(phase = Phase.Opening) }
            MediaPlayer.Event.Buffering -> _state.update { it.copy(buffering = event.buffering) }
            MediaPlayer.Event.Playing -> _state.update { it.copy(phase = Phase.Playing) }
            MediaPlayer.Event.Paused -> _state.update { it.copy(phase = Phase.Paused) }
            MediaPlayer.Event.EndReached -> _state.update { it.copy(phase = Phase.Ended) }
            MediaPlayer.Event.EncounteredError -> _state.update { it.copy(phase = Phase.Error) }
            MediaPlayer.Event.Vout -> _state.update {
                it.copy(videoOutputs = event.voutCount, videoTracks = player.videoTracksCount, videoSize = videoSize())
            }
            MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESDeleted, MediaPlayer.Event.ESSelected ->
                _state.update {
                    it.copy(
                        videoTracks = player.videoTracksCount,
                        videoSize = videoSize(),
                        tracksVersion = it.tracksVersion + 1,
                    )
                }
        }
    }

    private fun videoSize(): String? = runCatching {
        player.currentVideoTrack?.takeIf { it.width > 0 && it.height > 0 }?.let { "${it.width}×${it.height}" }
    }.getOrNull()
}
