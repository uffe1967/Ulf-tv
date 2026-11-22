package se.ulf.tv

import android.content.Context

class Store(context: Context) {
    private val prefs = context.getSharedPreferences("ulf_tv_prefs", Context.MODE_PRIVATE)

    var m3uUrl: String
        get() = prefs.getString("m3u_url", "") ?: ""
        set(value) = prefs.edit().putString("m3u_url", value.trim()).apply()

    var xtreamServer: String
        get() = prefs.getString("xtream_server", "") ?: ""
        set(value) = prefs.edit().putString("xtream_server", value.trim()).apply()

    var xtreamUser: String
        get() = prefs.getString("xtream_user", "") ?: ""
        set(value) = prefs.edit().putString("xtream_user", value.trim()).apply()

    var xtreamPass: String
        get() = prefs.getString("xtream_pass", "") ?: ""
        set(value) = prefs.edit().putString("xtream_pass", value.trim()).apply()

    var language: String
        get() = prefs.getString("lang", "sv") ?: "sv"
        set(value) = prefs.edit().putString("lang", value).apply()

    var bigBuffer: Boolean
        get() = prefs.getBoolean("big_buffer", false)
        set(value) = prefs.edit().putBoolean("big_buffer", value).apply()

    fun getFavorites(): Set<String> =
        prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()

    fun toggleFavorite(channelId: String): Set<String> {
        val current = getFavorites().toMutableSet()
        if (current.contains(channelId)) current.remove(channelId) else current.add(channelId)
        prefs.edit().putStringSet("favorites", current).apply()
        return current
    }

    fun getRecents(): List<String> {
        val raw = prefs.getString("recents", "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("|").filter { it.isNotBlank() }
    }

    fun addRecent(channelId: String): List<String> {
        val list = getRecents().toMutableList()
        list.remove(channelId)
        list.add(0, channelId)
        val trimmed = list.take(15)
        prefs.edit().putString("recents", trimmed.joinToString("|")).apply()
        return trimmed
    }

    fun clearSource() {
        prefs.edit()
            .remove("m3u_url")
            .remove("xtream_server")
            .remove("xtream_user")
            .remove("xtream_pass")
            .apply()
    }
}
