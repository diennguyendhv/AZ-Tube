package com.az.tube

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Plays audio/video in the background, keeps going with the screen off, and shows
 * controls in the notification and on the lock screen (and responds to headphone buttons).
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val played = HashSet<String>()
    private val retries = HashMap<String, Int>()

    /** Video decoder failures per video: the stream is fine, so these never skip to the next song. */
    private val decoderRetries = HashMap<String, Int>()
    private val main = Handler(Looper.getMainLooper())
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }

    /** Playback stopped because the network dropped; resume when it's back. */
    private var waitingForNetwork = false
    private var netRetries = 0

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            main.postDelayed({ resumeAfterNetwork() }, 2_000)
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) main.post { resumeAfterNetwork() }
        }
    }

    /** Screen off: never decode video, whatever state the app's screen was left in. */
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            session?.player?.let { setVideoEnabled(it, false) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(Media.dataSourceFactory(this)))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_NETWORK) // keep CPU + Wi-Fi awake with the screen off
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        player.addListener(PlayerListener(player))

        // Handler, not mainExecutor: that one needs Android 9 and crashed on Android 8.
        StreamResolver.onResolved = { main.post { maybeAppendRelated() } }
        connectivity.registerDefaultNetworkCallback(networkCallback)
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away keeps the music going; stop only if nothing is playing.
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        StreamResolver.onResolved = null
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        runCatching { unregisterReceiver(screenOff) }
        main.removeCallbacksAndMessages(null)
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** With autoplay on: when the last video in the queue plays, add a video from the same channel. */
    private fun maybeAppendRelated() {
        val p = session?.player ?: return
        if (!Store.autoplay || p.mediaItemCount == 0) return
        if (p.currentMediaItemIndex != p.mediaItemCount - 1) return
        val current = p.currentMediaItem?.mediaId ?: return
        val related = StreamResolver.related[current] ?: return
        val inQueue = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId }.toSet()
        val next = related.firstOrNull { it.url !in played && it.url !in inQueue && !Blocked.isBlocked(it.channelUrl) } ?: return
        StreamResolver.autoAdded.add(next.url)
        p.addMediaItem(Media.playable(next.toMediaItem()))
    }

    private fun hasNetwork(): Boolean {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isNetworkError(error: PlaybackException): Boolean {
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        ) {
            return true
        }
        var e: Throwable? = error.cause
        while (e != null) {
            if (e is UnknownHostException || e is SocketTimeoutException || e is ConnectException || e is SocketException) return true
            e = e.cause
        }
        return !hasNetwork()
    }

    /** Network is back: try the same video again from where it stopped. */
    private fun resumeAfterNetwork() {
        if (!waitingForNetwork) return
        val p = session?.player ?: return
        waitingForNetwork = false
        if (p.playerError != null) {
            p.prepare()
            p.play()
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { Media.playable(it) }.toMutableList())
    }

    private inner class PlayerListener(private val player: Player) : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.mediaId?.let { played.add(it) }
            maybeAppendRelated()
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                // Playing fine again: give this video its retries back.
                player.currentMediaItem?.mediaId?.let { retries.remove(it) }
                netRetries = 0
                waitingForNetwork = false
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val id = player.currentMediaItem?.mediaId ?: return
            if (isNetworkError(error)) {
                // Never skip a song because of the network. Keep the position and wait.
                if (!hasNetwork()) {
                    if (!waitingForNetwork) toast("Mất kết nối mạng. Sẽ phát tiếp khi có mạng lại")
                    waitingForNetwork = true
                    return
                }
                // Network is up but not working yet (just reconnected, weak signal): keep trying,
                // waiting a little longer each time (at most 30 s). Never skip the song.
                netRetries++
                if (netRetries == 5) toast("Mạng yếu. Vẫn đang thử phát tiếp bài này")
                waitingForNetwork = true
                main.postDelayed({ resumeAfterNetwork() }, (3_000L * netRetries).coerceAtMost(30_000L))
                return
            }
            if (error.errorCode in PlaybackException.ERROR_CODE_DECODER_INIT_FAILED..PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED) {
                // The phone's video decoder couldn't start (e.g. busy right after the screen came back on).
                // Try again where we are; if it fails again, keep playing the sound without the picture.
                val n = decoderRetries[id] ?: 0
                decoderRetries[id] = n + 1
                if (n >= 1) {
                    setVideoEnabled(player, false)
                    if (n == 1) toast("Không mở được phần hình của video này, đang phát âm thanh")
                }
                if (n < 4) {
                    player.prepare()
                    player.play()
                    return
                }
            }
            val tries = retries[id] ?: 0
            when {
                // Stream links expire after a few hours: look them up again once.
                tries < 1 -> {
                    retries[id] = tries + 1
                    InfoCache.invalidate(id)
                    player.prepare()
                    player.play()
                }
                player.hasNextMediaItem() -> {
                    toast("Bỏ qua bài lỗi: ${error.cause?.message ?: error.errorCodeName}")
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
                else -> toast("Lỗi phát: ${error.cause?.message ?: error.errorCodeName}")
            }
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
