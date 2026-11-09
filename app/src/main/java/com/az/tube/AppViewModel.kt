package com.az.tube

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService.LinkType

sealed interface Route {
    data object Home : Route
    data object Subscriptions : Route
    data object Library : Route
    data class Search(val query: String) : Route
    data class Channel(val url: String, val name: String) : Route
    data class Playlist(val url: String, val name: String) : Route
}

/** A list that loads page by page as you scroll. */
class Paged(private val scope: CoroutineScope, private val loader: suspend () -> PageResult) {
    var items by mutableStateOf<List<Entry>>(emptyList())
        private set
    var header by mutableStateOf<Any?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var more: (suspend () -> PageResult)? = null
    private var job: Job? = null

    init {
        load(false)
    }

    fun refresh() = load(true)

    private fun load(isRefresh: Boolean) {
        job?.cancel()
        job = scope.launch {
            loading = true
            refreshing = isRefresh
            error = null
            try {
                val r = loader()
                items = r.items
                header = r.header
                more = r.more
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    fun loadMore() {
        val next = more ?: return
        if (loading) return
        job = scope.launch {
            loading = true
            try {
                val r = next()
                val seen = items.map { it.url }.toHashSet()
                items = items + r.items.filter { seen.add(it.url) }
                more = r.more
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                more = null
            } finally {
                loading = false
            }
        }
    }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    // ---------- Navigation ----------
    val stack = mutableStateListOf<Route>(Route.Home)
    val route: Route get() = stack.last()

    fun open(r: Route) {
        if (stack.last() != r) stack.add(r)
    }

    fun selectTab(r: Route) {
        if (stack.size == 1 && stack[0] == r) {
            if (r == Route.Home) homeFeed.refresh()
            return
        }
        stack.clear()
        stack.add(r)
    }

    /** Returns false when there's nowhere left to go back to. */
    fun back(): Boolean {
        if (searchOpen) {
            searchOpen = false
            return true
        }
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            return true
        }
        if (stack[0] != Route.Home) {
            stack[0] = Route.Home
            return true
        }
        return false
    }

    // ---------- Home ----------
    var category by mutableStateOf(HomeCategories.first())
        private set
    private val homeCache = HashMap<String, Paged>()

    /** With recommendations on: a mixed feed by topic. Off: only new videos from your subscriptions. */
    val homeFeed: Paged
        get() = if (recommendationsOn) homeFor(category) else pagedFor(Route.Subscriptions)

    private fun homeFor(c: String) = homeCache.getOrPut(c) { Paged(viewModelScope) { Repo.home(c) } }

    fun selectCategory(c: String) {
        category = c
    }

    // ---------- Search ----------
    var searchOpen by mutableStateOf(false)
    var searchText by mutableStateOf("")
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var searchHistory by mutableStateOf(Store.searchHistory)
        private set
    private var suggestJob: Job? = null

    /** A video opened from a shared link, waiting for the player. */
    var playRequest by mutableStateOf<VideoItem?>(null)

    fun openSearch(prefill: String = "") {
        updateSearchText(prefill)
        searchOpen = true
    }

    /** Typing in the search box. Suggestions always show (whatever "Đề xuất" is set to) to save typing. */
    fun updateSearchText(text: String) {
        searchText = text
        suggestJob?.cancel()
        if (text.isBlank()) {
            suggestions = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(200)
            suggestions = Repo.suggestions(text)
        }
    }

    fun submitSearch(text: String) {
        val q = text.trim()
        if (q.isEmpty()) return
        searchOpen = false
        val url = Regex("https?://\\S+").find(q)?.value
        if (url != null) {
            openLink(url)
            return
        }
        if (searchHistoryOn) {
            searchHistory = listOf(q) + (searchHistory - q)
            Store.searchHistory = searchHistory
        }
        // Replace the current results page instead of stacking many searches.
        if (route is Route.Search) stack.removeAt(stack.lastIndex)
        open(Route.Search(q))
    }

    fun removeSearchHistory(q: String) {
        searchHistory = searchHistory - q
        Store.searchHistory = searchHistory
    }

    /** Handles a YouTube link: video, playlist or channel. */
    fun openLink(url: String) {
        viewModelScope.launch {
            try {
                val yt = ServiceList.YouTube
                when (withContext(Dispatchers.IO) { yt.getLinkTypeByUrl(url) }) {
                    LinkType.STREAM -> {
                        val d = Repo.details(url)
                        playRequest = VideoItem(
                            url = d.url, title = d.title, channel = d.channel.name,
                            channelUrl = d.channel.url, channelAvatar = d.channel.avatar,
                            thumb = InfoCache.get(url, 3_600_000L).thumbnails.pickImage(360),
                        )
                    }
                    LinkType.PLAYLIST -> open(Route.Playlist(url, "Danh sách phát"))
                    LinkType.CHANNEL -> open(Route.Channel(url, "Kênh"))
                    else -> message = "Link này chưa được hỗ trợ"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Không mở được link: ${e.message}"
            }
        }
    }

    /** Short messages shown at the bottom of the screen. */
    var message by mutableStateOf<String?>(null)

    // ---------- Lists for searches, channels and playlists ----------
    private val pages = HashMap<Route, Paged>()

    fun pagedFor(r: Route): Paged = pages.getOrPut(r) {
        Paged(viewModelScope) {
            when (r) {
                is Route.Search -> Repo.search(r.query)
                is Route.Channel -> Repo.channel(r.url)
                is Route.Playlist -> Repo.playlist(r.url)
                Route.Subscriptions -> Repo.subscriptionFeed(subscriptions)
                else -> PageResult(emptyList(), null)
            }
        }
    }

    // ---------- Subscriptions ----------
    var subscriptions by mutableStateOf(Store.subscriptions)
        private set

    fun isSubscribed(url: String?) = url != null && subscriptions.any { it.url == url }

    fun toggleSubscribe(channel: ChannelItem) {
        subscriptions = if (isSubscribed(channel.url)) {
            subscriptions.filterNot { it.url == channel.url }
        } else {
            listOf(channel) + subscriptions
        }
        Store.subscriptions = subscriptions
        pages.remove(Route.Subscriptions)
    }

    // ---------- Watch history ----------
    var watchHistory by mutableStateOf(Store.watchHistory)
        private set

    fun recordWatched(item: VideoItem) {
        if (!watchHistoryOn) return
        if (watchHistory.firstOrNull()?.url == item.url) return
        watchHistory = listOf(item) + watchHistory.filterNot { it.url == item.url }
        Store.watchHistory = watchHistory
    }

    fun clearHistory() {
        watchHistory = emptyList()
        Store.watchHistory = emptyList()
    }

    // ---------- Watch page ----------
    var watchExpanded by mutableStateOf(false)
    var details by mutableStateOf<Details?>(null)
        private set
    var detailsError by mutableStateOf<String?>(null)
        private set
    private var detailsJob: Job? = null
    private var detailsUrl: String? = null

    fun loadDetails(url: String?) {
        if (url == null || url == detailsUrl) return
        detailsUrl = url
        details = null
        detailsError = null
        detailsJob?.cancel()
        detailsJob = viewModelScope.launch {
            try {
                details = Repo.details(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                detailsError = e.message ?: "Không tải được thông tin video"
            }
        }
    }

    fun retryDetails() {
        val u = detailsUrl
        detailsUrl = null
        loadDetails(u)
    }

    // ---------- Settings ----------
    var audioOnly by mutableStateOf(Store.audioOnly)
    var maxQuality by mutableStateOf(Store.maxQuality)
    var autoplay by mutableStateOf(Store.autoplay)
        private set

    /** Works on its own, whatever "Đề xuất" is set to. Turning it on queues the next video right away. */
    fun changeAutoplay(on: Boolean) {
        autoplay = on
        Store.autoplay = on
        if (on) StreamResolver.onResolved?.invoke("")
    }

    var watchHistoryOn by mutableStateOf(Store.watchHistoryOn)
        private set
    var searchHistoryOn by mutableStateOf(Store.searchHistoryOn)
        private set
    var recommendationsOn by mutableStateOf(Store.recommendationsOn)
        private set

    /** Turning history off also deletes what was saved. */
    fun setWatchHistory(on: Boolean) {
        watchHistoryOn = on
        Store.watchHistoryOn = on
        if (!on) clearHistory()
    }

    fun setSearchHistory(on: Boolean) {
        searchHistoryOn = on
        Store.searchHistoryOn = on
        if (!on) {
            searchHistory = emptyList()
            Store.searchHistory = emptyList()
        }
    }

    fun setRecommendations(on: Boolean) {
        recommendationsOn = on
        Store.recommendationsOn = on
        // The "Kênh đăng ký" tab exists only with recommendations on; go home if we were there.
        if (!on && stack.firstOrNull() == Route.Subscriptions) selectTab(Route.Home)
    }
}
