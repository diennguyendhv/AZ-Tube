package com.az.tube

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

fun VideoItem.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(url)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(channel)
            .setArtworkUri(thumb?.toUri())
            .setExtras(
                Bundle().apply {
                    putString("channelUrl", channelUrl)
                    putString("avatar", channelAvatar)
                    putLong("duration", duration)
                },
            )
            .build(),
    )
    .build()

/** Rebuilds a VideoItem from what the player knows about a queue entry. */
fun MediaItem.toVideoItem(): VideoItem {
    val m = mediaMetadata
    val extras = m.extras
    return VideoItem(
        url = mediaId,
        title = m.title?.toString().orEmpty(),
        channel = m.artist?.toString().orEmpty(),
        channelUrl = extras?.getString("channelUrl"),
        channelAvatar = extras?.getString("avatar"),
        duration = extras?.getLong("duration") ?: 0L,
        thumb = m.artworkUri?.toString(),
    )
}

/** StreamInfo is fetched by both the screen (details) and the player (stream links): share it. */
object InfoCache {
    private val map = ConcurrentHashMap<String, Pair<Long, StreamInfo>>()

    fun get(url: String, maxAgeMs: Long): StreamInfo {
        map[url]?.let { (time, info) ->
            if (System.currentTimeMillis() - time < maxAgeMs) return info
        }
        val info = StreamInfo.getInfo(ServiceList.YouTube, url)
        map[url] = System.currentTimeMillis() to info
        if (map.size > 60) map.keys.take(20).forEach { map.remove(it) }
        return info
    }

    fun invalidate(url: String) {
        map.remove(url)
    }
}

/**
 * Turns a YouTube video URL into a DASH manifest pointing straight at the audio/video files.
 * Nothing from YouTube's ad system is ever requested, so there are no ads.
 */
object StreamResolver {
    /** Related videos for each resolved URL, used by autoplay when recommendations are on. */
    val related = ConcurrentHashMap<String, List<VideoItem>>()

    @Volatile
    var onResolved: ((String) -> Unit)? = null

    fun manifestFor(url: String, maxHeight: Int): String {
        val info = InfoCache.get(url, maxAgeMs = 10 * 60_000L)
        if (info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM) {
            throw IOException("Chưa hỗ trợ phát trực tiếp (livestream)")
        }
        related[url] = info.relatedItems.mapNotNull { it.toEntry() as? VideoItem }.filter { it.duration > 0 }

        val audio = pickAudio(info.audioStreams) ?: throw IOException("Không tìm thấy luồng âm thanh")
        var mpd = YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(
            audio.content, audio.itagItem!!, info.duration,
        )

        // Add the video track to the same manifest. When "audio only" is on (or the screen is
        // off) the player disables this track and never downloads it.
        pickVideo(info.videoOnlyStreams, maxHeight)?.let { video ->
            try {
                val v = YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(
                    video.content, video.itagItem!!, info.duration,
                )
                val start = v.indexOf("<AdaptationSet")
                val endTag = "</AdaptationSet>"
                val end = v.lastIndexOf(endTag)
                if (start >= 0 && end > start) {
                    val set = v.substring(start, end + endTag.length).replaceFirst("id=\"0\"", "id=\"1\"")
                    mpd = mpd.replace("</Period>", "$set</Period>")
                }
            } catch (_: Exception) {
                // Audio alone still plays.
            }
        }
        onResolved?.invoke(url)
        return mpd
    }

    private fun pickAudio(streams: List<AudioStream>): AudioStream? {
        val ok = streams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.itagItem != null }
        val original = ok.filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
            .ifEmpty { ok }
        return original.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
            ?: original.maxByOrNull { it.averageBitrate }
    }

    private fun pickVideo(streams: List<VideoStream>, maxHeight: Int): VideoStream? {
        val ok = streams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.itagItem != null }
        val fit = ok.filter { it.height in 1..maxHeight }
        // Prefer H.264 (hardware decoding on every phone), then anything else that fits.
        return fit.filter { it.format == MediaFormat.MPEG_4 }.maxByOrNull { it.height }
            ?: fit.maxByOrNull { it.height }
            ?: ok.minByOrNull { it.height }
    }
}

@UnstableApi
object Media {
    private const val SCHEME = "aztube"

    /**
     * Items in the queue only carry the video URL; stream links are looked up right before
     * playing. The quality is part of the URI so changing it makes the player reload.
     */
    fun playable(item: MediaItem): MediaItem = item.buildUpon()
        .setUri(
            Uri.Builder().scheme(SCHEME).authority("v")
                .appendQueryParameter("u", item.mediaId)
                .appendQueryParameter("q", Store.maxQuality.toString())
                .build(),
        )
        .setMimeType(MimeTypes.APPLICATION_MPD)
        .build()

    fun dataSourceFactory(context: Context): DataSource.Factory {
        val base = DefaultDataSource.Factory(context, YoutubeDataSource.Factory(Net.client))
        return ResolvingDataSource.Factory(base) { spec ->
            if (spec.uri.scheme == SCHEME) {
                val url = spec.uri.getQueryParameter("u").orEmpty()
                val quality = spec.uri.getQueryParameter("q")?.toIntOrNull() ?: 720
                val mpd = try {
                    StreamResolver.manifestFor(url, quality)
                } catch (e: IOException) {
                    throw e
                } catch (e: Exception) {
                    throw IOException(e.message ?: "Không tải được video", e)
                }
                val encoded = Base64.encodeToString(mpd.toByteArray(), Base64.NO_WRAP)
                spec.withUri(Uri.parse("data:application/dash+xml;base64,$encoded"))
            } else {
                spec
            }
        }
    }
}
