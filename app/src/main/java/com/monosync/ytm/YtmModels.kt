package com.monosync.ytm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class YtmSearchRequest(
    @SerialName("context") val context: YtmContext,
    @SerialName("query") val query: String
)

@Serializable
data class YtmContext(
    @SerialName("client") val client: YtmClient
)

@Serializable
data class YtmClient(
    @SerialName("clientName") val clientName: String = "67",
    @SerialName("clientVersion") val clientVersion: String = "18.25.52",
    @SerialName("hl") val hl: String = "en",
    @SerialName("gl") val gl: String = "US"
)

@Serializable
data class YtmSearchResponse(
    @SerialName("contents") val contents: YtmContents? = null
)

@Serializable
data class YtmContents(
    @SerialName("tabbedSearchResultsRenderer") 
    val tabbedSearchResultsRenderer: TabbedSearchResultsRenderer? = null
)

@Serializable
data class TabbedSearchResultsRenderer(
    @SerialName("tabs") 
    val tabs: List<Tab>? = null
)

@Serializable
data class Tab(
    @SerialName("tabRenderer") 
    val tabRenderer: TabRenderer? = null
)

@Serializable
data class TabRenderer(
    @SerialName("content") 
    val content: SectionListRenderer? = null
)

@Serializable
data class SectionListRenderer(
    @SerialName("contents") 
    val contents: List<Content>? = null
)

@Serializable
data class Content(
    @SerialName("musicShelfRenderer") 
    val musicShelfRenderer: MusicShelfRenderer? = null
)

@Serializable
data class MusicShelfRenderer(
    @SerialName("contents") 
    val contents: List<MusicShelfContent>? = null
)

@Serializable
data class MusicShelfContent(
    @SerialName("musicResponsiveListItemRenderer") 
    val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null
)

@Serializable
data class MusicResponsiveListItemRenderer(
    @SerialName("flexColumn") 
    val flexColumn: List<FlexColumn>? = null,
    @SerialName("overlay") 
    val overlay: Overlay? = null,
    @SerialName("videoId") 
    val videoId: String? = null,
    @SerialName("thumbnail")
    val thumbnail: MusicThumbnailRenderer? = null
)

@Serializable
data class FlexColumn(
    @SerialName("musicResponsiveListItemFlexColumnRenderer") 
    val musicResponsiveListItemFlexColumnRenderer: MusicResponsiveListItemFlexColumnRenderer? = null
)

@Serializable
data class MusicResponsiveListItemFlexColumnRenderer(
    @SerialName("text") 
    val text: Runs? = null
)

@Serializable
data class Runs(
    @SerialName("runs") 
    val runs: List<Run>? = null
)

@Serializable
data class Run(
    @SerialName("text") 
    val text: String? = null
)

@Serializable
data class Overlay(
    @SerialName("musicItemThumbnailOverlayRenderer") 
    val musicItemThumbnailOverlayRenderer: MusicItemThumbnailOverlayRenderer? = null
)

@Serializable
data class MusicItemThumbnailOverlayRenderer(
    @SerialName("content") 
    val content: MusicItemThumbnailOverlay? = null
)

@Serializable
data class MusicItemThumbnailOverlay(
    @SerialName("musicTrack") 
    val musicTrack: MusicTrack? = null
)

@Serializable
data class MusicTrack(
    @SerialName("songDuration") 
    val songDuration: Runs? = null,
    @SerialName("explicitLyrics") 
    val explicitLyrics: Boolean? = null
)

@Serializable
data class MusicThumbnailRenderer(
    @SerialName("musicThumbnailRenderer")
    val musicThumbnailRenderer: MusicThumbnailRendererInner? = null
)

@Serializable
data class MusicThumbnailRendererInner(
    @SerialName("thumbnail")
    val thumbnail: Thumbnails? = null
)

@Serializable
data class Thumbnails(
    @SerialName("thumbnails")
    val thumbnails: List<Thumbnail>? = null
)

@Serializable
data class Thumbnail(
    @SerialName("url")
    val url: String? = null,
    @SerialName("width")
    val width: Int? = null,
    @SerialName("height")
    val height: Int? = null
)

@Serializable
data class YtmTrack(
    val title: String,
    val artist: String,
    val duration: String,
    val thumbnail: String,
    val videoId: String,
    val explicit: Boolean = false
)
