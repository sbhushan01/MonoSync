package com.monosync.ytm

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YtmRepository @Inject constructor() {
    
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    suspend fun search(query: String): List<YtmTrack> = withContext(Dispatchers.IO) {
        try {
            val apiKey = com.monosync.BuildConfig.YTM_API_KEY
            val response: YtmSearchResponse = client.post("https://music.youtube.com/youtubei/v1/search?key=$apiKey") {
                contentType(ContentType.Application.Json)
                setBody(YtmSearchRequest(
                    context = YtmContext(YtmClient()),
                    query = query
                ))
                headers {
                    append("User-Agent", "com.google.android.music/5.35.53.28")
                    append("X-YouTube-Client-Name", "67")
                    append("X-YouTube-Client-Version", "18.25.52")
                    append("X-YouTube-Identity-Token", "")
                }
            }.body()
            
            response.contents?.tabbedSearchResultsRenderer
                ?.tabs
                ?.firstOrNull()
                ?.tabRenderer
                ?.content
                ?.contents
                ?.flatMap { it.musicShelfRenderer?.contents ?: emptyList() }
                ?.mapNotNull { content ->
                    val renderer = content.musicResponsiveListItemRenderer ?: return@mapNotNull null
                    val title = renderer.flexColumn?.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text ?: ""
                    val artist = renderer.flexColumn?.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text ?: ""
                    val duration = renderer.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicTrack?.songDuration?.runs?.firstOrNull()?.text ?: "?"
                    val thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.firstOrNull()?.url ?: ""
                    
                    YtmTrack(title, artist, duration, thumbnail, renderer.videoId ?: "")
                } ?: emptyList()
                
        } catch (e: Exception) {
            e.printStackTrace()
            println("YTM Search error: ${e.message}")
            emptyList()
        }
    }

    suspend fun getStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            // Simplified - get full player response in production
            "https://music.youtube.com/watch?v=$videoId"
        } catch (e: Exception) {
            null
        }
    }
}
