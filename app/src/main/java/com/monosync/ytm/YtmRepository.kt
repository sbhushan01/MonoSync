package com.monosync.ytm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YtmRepository @Inject constructor(
    private val httpClient: OkHttpClient
) {

    companion object {
        private const val TAG = "YtmRepository"
        private const val SEARCH_URL = "https://music.youtube.com/youtubei/v1/search"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    suspend fun search(query: String): List<YtmTrack> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        try {
            val apiKey = com.monosync.BuildConfig.YTM_API_KEY

            val requestBody = JSONObject().apply {
                put("query", query)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20241127.01.00")
                        put("hl", "en")
                        put("gl", "US")
                        put("platform", "DESKTOP")
                    })
                })
            }

            val request = Request.Builder()
                .url("$SEARCH_URL?key=$apiKey&prettyPrint=false")
                .header("Content-Type", "application/json")
                .header("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Safari/537.36")
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body.isNullOrBlank()) {
                Log.e(TAG, "YTM search failed: HTTP ${response.code}")
                return@withContext emptyList()
            }

            parseSearchResults(JSONObject(body))
        } catch (e: Exception) {
            Log.e(TAG, "YTM search error: ${e.message}", e)
            emptyList()
        }
    }

    private fun parseSearchResults(root: JSONObject): List<YtmTrack> {
        val results = mutableListOf<YtmTrack>()

        try {
            // Path: contents.tabbedSearchResultsRenderer.tabs[0]
            //       .tabRenderer.content.sectionListRenderer.contents[]
            val tabs = root
                .optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs")
                ?: return emptyList()

            val firstTab = tabs.optJSONObject(0) ?: return emptyList()

            val sectionContents = firstTab
                .optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?: return emptyList()

            for (s in 0 until sectionContents.length()) {
                val section = sectionContents.optJSONObject(s) ?: continue

                // Handle musicShelfRenderer sections (songs, videos, etc.)
                val shelf = section.optJSONObject("musicShelfRenderer")
                if (shelf != null) {
                    val shelfItems = shelf.optJSONArray("contents") ?: continue
                    for (i in 0 until shelfItems.length()) {
                        val renderer = shelfItems.optJSONObject(i)
                            ?.optJSONObject("musicResponsiveListItemRenderer")
                            ?: continue

                        val track = parseTrackFromRenderer(renderer)
                        if (track != null) results.add(track)
                    }
                }

                // Handle musicCardShelfRenderer (top result card)
                val cardShelf = section.optJSONObject("musicCardShelfRenderer")
                if (cardShelf != null) {
                    val track = parseTrackFromCardShelf(cardShelf)
                    if (track != null) results.add(track)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}", e)
        }

        return results
    }

    private fun parseTrackFromRenderer(renderer: JSONObject): YtmTrack? {
        val videoId = extractVideoId(renderer) ?: return null

        // flexColumns (plural!) — the old model incorrectly used flexColumn (singular)
        val flexColumns = renderer.optJSONArray("flexColumns")

        val title = flexColumns?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text")
            ?: return null

        val artist = flexColumns.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text")
            ?: ""

        // Duration from the last run in flexColumns[1] text
        val durationRuns = flexColumns.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
        val duration = extractDurationFromRuns(durationRuns)

        val thumbnail = extractThumbnail(renderer)

        return YtmTrack(title, artist, duration, thumbnail, videoId)
    }

    private fun parseTrackFromCardShelf(cardShelf: JSONObject): YtmTrack? {
        val videoId = cardShelf
            .optJSONObject("onTap")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId", "")
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        val title = cardShelf
            .optJSONObject("title")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text", "")
            ?: return null

        val subtitle = cardShelf
            .optJSONObject("subtitle")
            ?.optJSONArray("runs")
        val artist = if (subtitle != null && subtitle.length() > 0) {
            subtitle.optJSONObject(0)?.optString("text", "") ?: ""
        } else ""

        val thumbnail = cardShelf
            .optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?.let { getBestThumbnail(it) }
            ?: ""

        return YtmTrack(title, artist, "?", thumbnail, videoId)
    }

    /**
     * Extracts videoId from multiple possible locations in the renderer JSON.
     * YouTube has moved this field across API versions.
     */
    private fun extractVideoId(renderer: JSONObject): String? {
        // Location 1: playlistItemData.videoId (most common for songs)
        renderer.optJSONObject("playlistItemData")
            ?.optString("videoId", "")
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        // Location 2: Direct videoId on renderer
        renderer.optString("videoId", "")
            .takeIf { it.isNotEmpty() }
            ?.let { return it }

        // Location 3: overlay → playButton → watchEndpoint
        renderer.optJSONObject("overlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId", "")
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        // Location 4: flexColumns[0] runs navigationEndpoint
        val flexColumns = renderer.optJSONArray("flexColumns")
        if (flexColumns != null && flexColumns.length() > 0) {
            val runs = flexColumns.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
            if (runs != null && runs.length() > 0) {
                runs.optJSONObject(0)
                    ?.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("watchEndpoint")
                    ?.optString("videoId", "")
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { return it }
            }
        }

        return null
    }

    private fun extractThumbnail(renderer: JSONObject): String {
        return renderer
            .optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?.let { getBestThumbnail(it) }
            ?: ""
    }

    private fun getBestThumbnail(thumbnails: JSONArray): String {
        if (thumbnails.length() == 0) return ""
        // Use the largest available thumbnail
        return thumbnails.optJSONObject(thumbnails.length() - 1)
            ?.optString("url", "") ?: ""
    }

    private fun extractDurationFromRuns(runs: JSONArray?): String {
        if (runs == null || runs.length() == 0) return "?"
        // Duration is typically the last run in the second flex column
        val lastRun = runs.optJSONObject(runs.length() - 1)
        val text = lastRun?.optString("text", "") ?: ""
        // Check if it looks like a duration (e.g. "3:45")
        return if (text.matches(Regex("\\d+:\\d+"))) text else "?"
    }
}
