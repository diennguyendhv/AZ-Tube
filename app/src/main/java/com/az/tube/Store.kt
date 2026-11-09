package com.az.tube

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray

/**
 * Settings, subscriptions and (only when turned on) history, saved on the phone.
 * History and recommendations are off until you switch them on.
 */
object Store {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    var audioOnly: Boolean
        get() = sp.getBoolean("audio_only", false)
        set(v) = sp.edit { putBoolean("audio_only", v) }

    /** Highest video height to download: 144..1080. */
    var maxQuality: Int
        get() = sp.getInt("max_quality", 720)
        set(v) = sp.edit { putInt("max_quality", v) }

    // ----- Switches, all off by default -----
    var watchHistoryOn: Boolean
        get() = sp.getBoolean("watch_history_on", false)
        set(v) = sp.edit { putBoolean("watch_history_on", v) }

    var searchHistoryOn: Boolean
        get() = sp.getBoolean("search_history_on", false)
        set(v) = sp.edit { putBoolean("search_history_on", v) }

    /** Recommended home feed, related videos, search suggestions. */
    var recommendationsOn: Boolean
        get() = sp.getBoolean("recommendations_on", false)
        set(v) = sp.edit { putBoolean("recommendations_on", v) }

    /** Keep playing related videos after the queue ends (needs recommendations on). */
    var autoplay: Boolean
        get() = sp.getBoolean("autoplay", true)
        set(v) = sp.edit { putBoolean("autoplay", v) }

    // ----- Data -----
    var searchHistory: List<String>
        get() = sp.getString("history", "").orEmpty().split('\n').filter { it.isNotBlank() }
        set(v) = sp.edit { putString("history", v.take(20).joinToString("\n")) }

    var subscriptions: List<ChannelItem>
        get() = readArray("subscriptions") { ChannelItem.fromJson(it) }
        set(v) = writeArray("subscriptions", v.map { it.toJson() })

    var watchHistory: List<VideoItem>
        get() = readArray("watch_history") { VideoItem.fromJson(it) }
        set(v) = writeArray("watch_history", v.take(200).map { it.toJson() })

    private fun <T> readArray(key: String, parse: (org.json.JSONObject) -> T): List<T> = try {
        val arr = JSONArray(sp.getString(key, "[]"))
        List(arr.length()) { parse(arr.getJSONObject(it)) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun writeArray(key: String, items: List<org.json.JSONObject>) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        sp.edit { putString(key, arr.toString()) }
    }
}
