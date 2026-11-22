package se.ulf.tv

data class Channel(
    val id: String,
    val name: String,
    val url: String,
    val group: String = "Allmänt",
    val logo: String = "",
    val description: String = ""
)

enum class AppTab(val svLabel: String, val enLabel: String) {
    HOME("Hem", "Home"),
    LIVE("Live TV", "Live TV"),
    FAVORITES("Favoriter", "Favorites"),
    SEARCH("Sök", "Search"),
    SETTINGS("Inställningar", "Settings")
}

object DemoChannels {
    val list = listOf(
        Channel(
            id = "demo_bbb",
            name = "Big Buck Bunny (Demo)",
            url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            group = "Demo • 4K / HD",
            logo = "https://peach.blender.org/wp-content/uploads/bbb-splash.png",
            description = "Öppen HLS-testström för att testa bildkvalitet, buffring och fjärrkontroll."
        ),
        Channel(
            id = "demo_sintel",
            name = "Sintel Open Movie (Demo)",
            url = "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8",
            group = "Demo • 4K / HD",
            logo = "https://durian.blender.org/wp-content/uploads/2010/06/05.8b_comp_000272.jpg",
            description = "Animerad kortfilm i HLS-format med hög bithastighet."
        ),
        Channel(
            id = "demo_tears",
            name = "Tears of Steel (Demo)",
            url = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
            group = "Demo • Film",
            logo = "https://mango.blender.org/wp-content/uploads/2012/09/01_thom_celia_bridge.jpg",
            description = "Sci-fi kortfilm från Blender Foundation för att verifiera uppspelning."
        ),
        Channel(
            id = "demo_akamai",
            name = "Live Test Feed (Demo)",
            url = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8",
            group = "Demo • Live",
            logo = "",
            description = "Kontinuerlig live-testström för snabbzappning och stabilitetstest."
        )
    )
}
