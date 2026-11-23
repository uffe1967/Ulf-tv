package se.ulf.tv

enum class ContentType {
    LIVE, MOVIE, SERIES
}

data class MediaItem(
    val id: String,
    val title: String,
    val category: String,
    val country: String = "Övriga",
    val subCategory: String = "Allmänt",
    val type: ContentType,
    val streamUrl: String,
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val rating: String = "",
    val year: String = "",
    val addedTimestamp: Long = 0L,
    val plot: String = "",
    val epgTitle: String = "",
    val epgNext: String = "",
    val seriesId: String = "",
    val extension: String = "mp4",
    val qualityBadge: String = ""
)

data class EpisodeItem(
    val id: String,
    val season: Int,
    val episodeNum: Int,
    val title: String,
    val streamUrl: String,
    val duration: String = "",
    val plot: String = "",
    val thumbUrl: String = ""
)

data class TrackOption(
    val label: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val isSelected: Boolean
)

data class StreamTechInfo(
    val resolution: String = "",
    val fps: String = "",
    val videoCodec: String = "",
    val audioCodec: String = "",
    val activeAudioLang: String = "",
    val activeSubtitleLang: String = "Av"
)

data class AccountProfile(
    val id: String,
    val name: String,
    val mode: String,
    val m3uUrl: String = "",
    val server: String = "",
    val username: String = "",
    val password: String = ""
)

data class AppSettings(
    val userAgent: String = "IPTVSmartersPro",
    val largeBuffer: Boolean = true,
    val hardwareDecoding: Boolean = true,
    val hideAdult: Boolean = true,
    val parentalPin: String = "0000",
    val parentalEnabled: Boolean = false,
    val autoRefreshHours: Int = 12,
    val aspectMode: Int = 0
)

data class CatalogData(
    val liveChannels: List<MediaItem> = emptyList(),
    val movies: List<MediaItem> = emptyList(),
    val series: List<MediaItem> = emptyList()
)
