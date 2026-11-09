package com.az.tube

import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.ConcurrentHashMap

/** One page of a list plus a way to get the next one. */
class PageResult(
    val items: List<Entry>,
    val more: (suspend () -> PageResult)?,
    val header: Any? = null,
)

data class ChannelHeader(val channel: ChannelItem, val banner: String?, val description: String)
data class PlaylistHeader(val name: String, val uploader: String, val count: Long, val thumb: String?)

data class Details(
    val url: String,
    val title: String,
    val views: Long,
    val likes: Long,
    val uploadMillis: Long,
    val uploaded: String,
    val description: String,
    val channel: ChannelItem,
    val related: List<Entry>,
)

val HomeCategories = listOf(
    "Tất cả", "Âm nhạc", "Chạy bộ", "Trò chơi", "Tin tức", "Phim", "Podcast", "Hài", "Nấu ăn", "Thể thao",
)

object Repo {
    private val yt = ServiceList.YouTube

    private fun pageOf(
        items: List<InfoItem>,
        next: Page?,
        header: Any? = null,
        map: (Entry) -> Entry = { it },
        fetch: (Page) -> InfoItemsPage<out InfoItem>,
    ): PageResult = PageResult(
        items.toEntries().map(map),
        next?.takeIf { Page.isValid(it) }?.let { page ->
            suspend {
                val r = withContext(Dispatchers.IO) { fetch(page) }
                pageOf(r.items, r.nextPage, null, map, fetch)
            }
        },
        header,
    )

    suspend fun search(query: String, videosOnly: Boolean = false): PageResult = withContext(Dispatchers.IO) {
        val filters = if (videosOnly) listOf(YoutubeSearchQueryHandlerFactory.VIDEOS) else emptyList()
        val handler = yt.searchQHFactory.fromQuery(query, filters, "")
        val info = SearchInfo.getInfo(yt, handler)
        pageOf(info.relatedItems, info.nextPage) { SearchInfo.getMoreItems(yt, handler, it) }
    }

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        try {
            yt.suggestionExtractor.suggestionList(query)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun kiosk(id: String): PageResult = withContext(Dispatchers.IO) {
        val url = yt.kioskList.getListLinkHandlerFactoryByType(id).fromId(id).url
        val info = KioskInfo.getInfo(yt, url)
        pageOf(info.relatedItems, info.nextPage) { KioskInfo.getMoreItems(yt, url, it) }
    }

    /** Recommended home feed (only used when recommendations are on): mixes searches by topic and your history. */
    suspend fun home(category: String): PageResult = coroutineScope {
        val queries = when (category) {
            "Âm nhạc" -> listOf("nhạc việt mới nhất", "vpop hot nhất", "nhạc trẻ remix")
            "Chạy bộ" -> listOf("nhạc chạy bộ", "running music 160 bpm", "nhạc tập gym")
            "Trò chơi" -> listOf("game hay nhất", "liên quân mobile")
            "Tin tức" -> listOf("tin tức mới nhất hôm nay", "thời sự")
            "Phim" -> listOf("phim hay", "trailer phim mới")
            "Podcast" -> listOf("podcast tiếng việt")
            "Hài" -> listOf("hài hước mới nhất", "hài việt")
            "Nấu ăn" -> listOf("nấu ăn ngon mỗi ngày")
            "Thể thao" -> listOf("bóng đá highlights", "thể thao 24h")
            else -> {
                val fromSearches = Store.searchHistory.take(3)
                val fromChannels = Store.watchHistory.map { it.channel }.filter { it.isNotBlank() }.distinct().take(2)
                (fromSearches + fromChannels + listOf("thịnh hành việt nam", "nhạc mới nhất", "tin tức hôm nay"))
                    .distinct().take(6)
            }
        }
        val kioskId = when (category) {
            "Trò chơi" -> "trending_gaming"
            "Phim" -> "trending_movies_and_shows"
            else -> null
        }
        val jobs = queries.map { q -> async { runCatching { search(q, videosOnly = true) }.getOrNull() } } +
            listOfNotNull(kioskId?.let { id -> async { runCatching { kiosk(id) }.getOrNull() } })
        val pages = jobs.awaitAll().filterNotNull()
        if (pages.isEmpty()) throw java.io.IOException("Không tải được trang chủ. Kiểm tra kết nối mạng.")
        mix(pages, HashSet())
    }

    /** Interleaves several lists (a, b, c, a, b, c…) without duplicates. */
    private fun mix(pages: List<PageResult>, seen: MutableSet<String>): PageResult {
        val out = ArrayList<Entry>()
        val lists = pages.map { p -> p.items.filterIsInstance<VideoItem>().filter { !it.isShort } }
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) {
            for (l in lists) {
                val v = l.getOrNull(i) ?: continue
                if (seen.add(v.url)) out += v
            }
        }
        val nexts = pages.mapNotNull { it.more }
        val more: (suspend () -> PageResult)? = if (nexts.isEmpty()) null else {
            suspend {
                coroutineScope {
                    val next = nexts.map { f -> async { runCatching { f() }.getOrNull() } }.awaitAll().filterNotNull()
                    mix(next, seen)
                }
            }
        }
        return PageResult(out, more)
    }

    suspend fun channel(url: String): PageResult = withContext(Dispatchers.IO) {
        val info = ChannelInfo.getInfo(yt, url)
        val channel = ChannelItem(info.url, info.name, info.avatars.pickImage(88), info.subscriberCount, info.isVerified)
        val header = ChannelHeader(channel, info.banners.pickImage(200), info.description.orEmpty())
        val tab = info.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.VIDEOS) } ?: info.tabs.firstOrNull()
            ?: return@withContext PageResult(emptyList(), null, header)
        val tabInfo = ChannelTabInfo.getInfo(yt, tab)
        // Videos on a channel page don't carry the channel's avatar: add it.
        val fill: (Entry) -> Entry = { e ->
            if (e is VideoItem) {
                e.copy(
                    channel = e.channel.ifBlank { channel.name },
                    channelUrl = e.channelUrl ?: channel.url,
                    channelAvatar = e.channelAvatar ?: channel.avatar,
                )
            } else {
                e
            }
        }
        pageOf(tabInfo.relatedItems, tabInfo.nextPage, header, fill) { ChannelTabInfo.getMoreItems(yt, tab, it) }
    }

    suspend fun playlist(url: String): PageResult = withContext(Dispatchers.IO) {
        val info = PlaylistInfo.getInfo(yt, url)
        val header = PlaylistHeader(info.name, info.uploaderName.orEmpty(), info.streamCount, info.thumbnails.pickImage(360))
        pageOf(info.relatedItems, info.nextPage, header) { PlaylistInfo.getMoreItems(yt, url, it) }
    }

    /** Newest videos from every subscribed channel, newest first. */
    suspend fun subscriptionFeed(subs: List<ChannelItem>): PageResult = coroutineScope {
        val gate = Semaphore(4)
        val lists = subs.take(30).map { sub ->
            async {
                gate.withPermit {
                    runCatching { channel(sub.url).items.filterIsInstance<VideoItem>().take(15) }.getOrDefault(emptyList())
                }
            }
        }.awaitAll()
        PageResult(lists.flatten().filter { !it.isShort }.sortedByDescending { it.uploadMillis }, null)
    }

    /** Each channel's video list, kept for 30 minutes so the watch page and autoplay share one request. */
    private val channelVideoCache = ConcurrentHashMap<String, Pair<Long, List<VideoItem>>>()

    private suspend fun channelVideos(channelUrl: String): List<VideoItem> {
        val now = System.currentTimeMillis()
        channelVideoCache[channelUrl]?.let { (at, list) -> if (now - at < 30 * 60_000L) return list }
        val list = runCatching { channel(channelUrl).items.filterIsInstance<VideoItem>() }.getOrDefault(emptyList())
        if (list.isNotEmpty()) channelVideoCache[channelUrl] = now to list
        return list
    }

    /**
     * Recommendations for a video: only videos from the same channel. Uses YouTube's related list
     * first and, when it has fewer than 10 from this channel, fills up with the channel's newest videos.
     */
    suspend fun sameChannelVideos(info: StreamInfo): List<VideoItem> {
        val key = Blocked.key(info.uploaderUrl)
        if (key.isEmpty() || Blocked.isBlocked(info.uploaderUrl)) return emptyList()
        val playable: (VideoItem) -> Boolean = { it.url != info.url && it.duration > 0 && !it.isShort }
        val own = info.relatedItems.mapNotNull { it.toEntry() as? VideoItem }
            .filter { Blocked.key(it.channelUrl) == key && playable(it) }
        if (own.size >= 10) return own
        val more = channelVideos(info.uploaderUrl).filter(playable)
        return (own + more).distinctBy { it.url }
    }

    suspend fun details(url: String): Details = withContext(Dispatchers.IO) {
        val info = InfoCache.get(url, maxAgeMs = 60 * 60_000L)
        val millis = try {
            info.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() ?: 0L
        } catch (_: Exception) {
            0L
        }
        val desc = info.description
        val text = if (desc.type() == Description.Type.HTML) {
            HtmlCompat.fromHtml(desc.content(), HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
        } else {
            desc.content()
        }
        Details(
            url = url,
            title = info.name,
            views = info.viewCount,
            likes = info.likeCount,
            uploadMillis = millis,
            uploaded = info.textualUploadDate.orEmpty(),
            description = text.trim(),
            channel = ChannelItem(
                info.uploaderUrl.orEmpty(),
                info.uploaderName.orEmpty(),
                info.uploaderAvatars.pickImage(48),
                info.uploaderSubscriberCount,
                info.isUploaderVerified,
            ),
            related = sameChannelVideos(info),
        )
    }
}
