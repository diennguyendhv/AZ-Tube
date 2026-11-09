package com.az.tube

import org.json.JSONObject
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.Locale

/** Anything that can appear in a list: a video, a channel or a playlist. */
sealed interface Entry {
    val url: String
}

data class VideoItem(
    override val url: String,
    val title: String,
    val channel: String,
    val channelUrl: String? = null,
    val channelAvatar: String? = null,
    val duration: Long = 0,
    val thumb: String? = null,
    val views: Long = -1,
    val uploaded: String = "",
    val uploadMillis: Long = 0,
    val verified: Boolean = false,
    val isShort: Boolean = false,
) : Entry {
    fun toJson(): JSONObject = JSONObject()
        .put("url", url).put("title", title).put("channel", channel)
        .put("channelUrl", channelUrl).put("avatar", channelAvatar)
        .put("duration", duration).put("thumb", thumb)

    companion object {
        fun fromJson(o: JSONObject) = VideoItem(
            url = o.getString("url"),
            title = o.optString("title"),
            channel = o.optString("channel"),
            channelUrl = o.optString("channelUrl").ifEmpty { null },
            channelAvatar = o.optString("avatar").ifEmpty { null },
            duration = o.optLong("duration"),
            thumb = o.optString("thumb").ifEmpty { null },
        )
    }
}

data class ChannelItem(
    override val url: String,
    val name: String,
    val avatar: String?,
    val subscribers: Long = -1,
    val verified: Boolean = false,
) : Entry {
    fun toJson(): JSONObject = JSONObject().put("url", url).put("name", name).put("avatar", avatar)

    companion object {
        fun fromJson(o: JSONObject) = ChannelItem(
            url = o.getString("url"),
            name = o.optString("name"),
            avatar = o.optString("avatar").ifEmpty { null },
        )
    }
}

data class PlaylistItem(
    override val url: String,
    val name: String,
    val thumb: String?,
    val uploader: String,
    val count: Long,
) : Entry

fun InfoItem.toEntry(): Entry? = when (this) {
    is StreamInfoItem -> {
        if (streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM) {
            null // live streams aren't supported by the player
        } else {
            val millis = try {
                uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() ?: 0L
            } catch (_: Exception) {
                0L
            }
            VideoItem(
                url = url,
                title = name,
                channel = uploaderName.orEmpty(),
                channelUrl = uploaderUrl,
                channelAvatar = uploaderAvatars.pickImage(48),
                duration = duration,
                thumb = thumbnails.pickImage(360),
                views = viewCount,
                uploaded = textualUploadDate.orEmpty(),
                uploadMillis = millis,
                verified = isUploaderVerified,
                isShort = isShortFormContent,
            )
        }
    }
    is ChannelInfoItem -> ChannelItem(url, name, thumbnails.pickImage(88), subscriberCount, isVerified)
    is PlaylistInfoItem -> PlaylistItem(url, name, thumbnails.pickImage(360), uploaderName.orEmpty(), streamCount)
    else -> null
}

fun List<InfoItem>.toEntries(): List<Entry> = mapNotNull { it.toEntry() }

/** Picks the smallest image at least [minHeight] tall (or the biggest one available). */
fun List<Image>.pickImage(minHeight: Int): String? {
    val sorted = sortedBy { it.height }
    val url = (sorted.firstOrNull { it.height >= minHeight } ?: sorted.lastOrNull())?.url ?: return null
    return if (url.startsWith("//")) "https:$url" else url
}

// ---------- Formatting, Vietnamese YouTube style ----------

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** 1234 -> "1,2 N", 43727856 -> "43 Tr", like YouTube in Vietnamese. */
fun compactNumber(n: Long): String {
    fun fmt(v: Double, unit: String): String {
        val text = if (v < 10) String.format(Locale.US, "%.1f", v).removeSuffix(".0") else v.toLong().toString()
        return text.replace('.', ',') + " " + unit
    }
    return when {
        n < 0 -> ""
        n < 1_000 -> n.toString()
        n < 1_000_000 -> fmt(n / 1_000.0, "N")
        n < 1_000_000_000 -> fmt(n / 1_000_000.0, "Tr")
        else -> fmt(n / 1_000_000_000.0, "T")
    }
}

fun viewsText(n: Long): String = if (n < 0) "" else "${compactNumber(n)} lượt xem"

fun subsText(n: Long): String = if (n < 0) "" else "${compactNumber(n)} người đăng ký"

fun timeAgo(millis: Long, textual: String): String {
    if (millis > 0) {
        val sec = (System.currentTimeMillis() - millis) / 1000
        return when {
            sec < 60 -> "Vừa xong"
            sec < 3600 -> "${sec / 60} phút trước"
            sec < 86_400 -> "${sec / 3600} giờ trước"
            sec < 7 * 86_400 -> "${sec / 86_400} ngày trước"
            sec < 30 * 86_400 -> "${sec / (7 * 86_400)} tuần trước"
            sec < 365 * 86_400 -> "${sec / (30 * 86_400)} tháng trước"
            else -> "${sec / (365 * 86_400)} năm trước"
        }
    }
    // Fallback: translate YouTube's English text ("5 months ago").
    val m = Regex("(\\d+)\\s*(second|minute|hour|day|week|month|year)", RegexOption.IGNORE_CASE).find(textual)
        ?: return textual
    val unit = when (m.groupValues[2].lowercase()) {
        "second" -> "giây"
        "minute" -> "phút"
        "hour" -> "giờ"
        "day" -> "ngày"
        "week" -> "tuần"
        "month" -> "tháng"
        else -> "năm"
    }
    return "${m.groupValues[1]} $unit trước"
}

fun VideoItem.metaLine(includeChannel: Boolean = true): String =
    listOfNotNull(
        channel.takeIf { includeChannel && it.isNotBlank() },
        viewsText(views).ifEmpty { null },
        timeAgo(uploadMillis, uploaded).ifEmpty { null },
    ).joinToString(" · ")
