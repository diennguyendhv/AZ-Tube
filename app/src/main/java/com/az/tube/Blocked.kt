package com.az.tube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Channels the user chose "Không đề xuất kênh này" for. Their videos are hidden everywhere. */
object Blocked {
    private var loaded = false
    private var state by mutableStateOf<List<ChannelItem>>(emptyList())

    val channels: List<ChannelItem>
        get() {
            if (!loaded) { state = Store.blockedChannels; loaded = true }
            return state
        }

    /** Last part of a channel URL (channel id or handle), so differently formatted URLs still match. */
    fun key(url: String?): String = url.orEmpty().trimEnd('/').substringAfterLast('/').substringBefore('?')

    fun isBlocked(channelUrl: String?): Boolean {
        val k = key(channelUrl)
        return k.isNotEmpty() && channels.any { key(it.url) == k }
    }

    fun block(channel: ChannelItem) {
        if (isBlocked(channel.url)) return
        state = listOf(channel) + channels
        Store.blockedChannels = state
    }

    fun unblock(channelUrl: String) {
        val k = key(channelUrl)
        state = channels.filter { key(it.url) != k }
        Store.blockedChannels = state
    }
}
