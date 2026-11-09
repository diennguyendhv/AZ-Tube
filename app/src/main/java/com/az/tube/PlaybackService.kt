package com.az.tube

import android.app.PendingIntent
import android.content.Intent
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

/**
 * Plays audio/video in the background, keeps going with the screen off, and shows
 * controls in the notification and on the lock screen (and responds to headphone buttons).
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val played = HashSet<String>()
    private val retries = HashMap<String, Int>()

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

        StreamResolver.onResolved = { mainExecutor.execute { maybeAppendRelated() } }

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
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** With recommendations and autoplay on: when the last video in the queue starts, add a related one. */
    private fun maybeAppendRelated() {
        val p = session?.player ?: return
        if (!Store.recommendationsOn || !Store.autoplay || p.mediaItemCount == 0) return
        if (p.currentMediaItemIndex != p.mediaItemCount - 1) return
        val current = p.currentMediaItem?.mediaId ?: return
        val related = StreamResolver.related[current] ?: return
        val inQueue = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId }.toSet()
        val next = related.firstOrNull { it.url !in played && it.url !in inQueue } ?: return
        p.addMediaItem(Media.playable(next.toMediaItem()))
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

        override fun onPlayerError(error: PlaybackException) {
            val id = player.currentMediaItem?.mediaId ?: return
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
