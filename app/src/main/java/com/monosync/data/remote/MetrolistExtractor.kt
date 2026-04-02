package com.monosync.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Data model representing metadata parsed from YouTube Music.
 */
data class YtmTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val durationSeconds: Int? = null
)

/**
 * Interface representing Metrolist's InnerTube Client for YTM.
 */
interface MetrolistExtractor {
    suspend fun getPlayerResponse(videoId: String): JSONObject
    suspend fun getNextResponse(videoId: String): JSONObject
    fun extractSynchronizedLyrics(responseObj: JSONObject): String?
}

/**
 * YouTube Music Extractor Logic
 * Inspired by the MetrolistGroup/Metrolist GitHub repository.
 *
 * Uses ANDROID_MUSIC client for player requests (returns direct stream URLs)
 * and WEB_REMIX client for browse/next requests (lyrics, metadata).
 */
class MetrolistYtmExtractor(
    private val httpClient: OkHttpClient
) : MetrolistExtractor {

    companion object {
        private const val TAG = "MetrolistYtmExtractor"

        private const val INNERTUBE_PLAYER_URL =
            "https://music.youtube.com/youtubei/v1/player"
        private const val INNERTUBE_NEXT_URL =
            "https://music.youtube.com/youtubei/v1/next"

        private val INNERTUBE_API_KEY = com.monosync.BuildConfig.YTM_API_KEY

        // ANDROID_MUSIC client — returns direct stream URLs without signature cipher
        private const val PLAYER_CLIENT_NAME = "ANDROID_MUSIC"
        private const val PLAYER_CLIENT_VERSION = "7.27.52"

        // WEB_REMIX client — for search and browse/next endpoints
        private const val WEB_CLIENT_NAME = "WEB_REMIX"
        private const val WEB_CLIENT_VERSION = "1.20241127.01.00"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Calls the YouTube Music InnerTube player endpoint using ANDROID_MUSIC client.
     * This client returns direct stream URLs in the `url` field of adaptive formats,
     * avoiding the need for signature deciphering.
     */
    override suspend fun getPlayerResponse(videoId: String): JSONObject =
        withContext(Dispatchers.IO) {
            val requestBody = buildPlayerRequestBody(videoId)

            val request = Request.Builder()
                .url("$INNERTUBE_PLAYER_URL?key=$INNERTUBE_API_KEY&prettyPrint=false")
                .header("Content-Type", "application/json")
                .header("User-Agent",
                    "com.google.android.apps.youtube.music/$PLAYER_CLIENT_VERSION " +
                    "(Linux; U; Android 14; Pixel 8 Pro) gzip")
                .header("X-Goog-Api-Format-Version", "2")
                .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            executeRequest(request, videoId, "player")
        }

    /**
     * Calls the YouTube Music InnerTube /next endpoint using WEB_REMIX client.
     * This endpoint returns lyrics, related tracks, and other metadata.
     */
    override suspend fun getNextResponse(videoId: String): JSONObject =
        withContext(Dispatchers.IO) {
            val requestBody = buildNextRequestBody(videoId)

            val request = Request.Builder()
                .url("$INNERTUBE_NEXT_URL?key=$INNERTUBE_API_KEY&prettyPrint=false")
                .header("Content-Type", "application/json")
                .header("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Safari/537.36")
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            executeRequest(request, videoId, "next")
        }

    private suspend fun executeRequest(
        request: Request, videoId: String, endpoint: String
    ): JSONObject {
        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                Log.e(TAG, "InnerTube $endpoint request failed: HTTP ${response.code}")
                throw IOException(
                    "InnerTube $endpoint request failed with HTTP ${response.code}"
                )
            }

            return JSONObject(responseBody)
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "InnerTube $endpoint timed out for videoId=$videoId", e)
            throw IOException("InnerTube request timed out", e)
        } catch (e: UnknownHostException) {
            Log.e(TAG, "No network for videoId=$videoId", e)
            throw IOException("No network connection available", e)
        } catch (e: IOException) {
            Log.e(TAG, "Network error during $endpoint for videoId=$videoId", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error parsing $endpoint for videoId=$videoId", e)
            throw IOException("Failed to parse InnerTube response", e)
        }
    }

    /**
     * Builds the InnerTube JSON request body for the player endpoint.
     * Uses ANDROID_MUSIC client context for direct stream URLs.
     */
    private fun buildPlayerRequestBody(videoId: String): JSONObject {
        return JSONObject().apply {
            put("videoId", videoId)

            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", PLAYER_CLIENT_NAME)
                    put("clientVersion", PLAYER_CLIENT_VERSION)
                    put("androidSdkVersion", 34)
                    put("osName", "Android")
                    put("osVersion", "14")
                    put("platform", "MOBILE")
                    put("hl", "en")
                    put("gl", "US")
                })
            })

            put("racyCheckOk", true)
            put("contentCheckOk", true)
        }
    }

    /**
     * Builds the InnerTube JSON request body for the /next endpoint.
     * Uses WEB_REMIX client context (needed for lyrics/browse data).
     */
    private fun buildNextRequestBody(videoId: String): JSONObject {
        return JSONObject().apply {
            put("videoId", videoId)
            put("isAudioOnly", true)

            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", WEB_CLIENT_NAME)
                    put("clientVersion", WEB_CLIENT_VERSION)
                    put("hl", "en")
                    put("gl", "US")
                    put("platform", "DESKTOP")
                })
            })
        }
    }

    /**
     * Parses the InnerTube player response for the highest-bitrate audio stream URL.
     */
    fun extractStreamUrl(playerResponseObj: JSONObject): String? {
        val playabilityStatus = playerResponseObj.optJSONObject("playabilityStatus")
        if (playabilityStatus?.optString("status") != "OK") return null

        val streamingData = playerResponseObj.optJSONObject("streamingData") ?: return null
        val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: return null

        var bestUrl: String? = null
        var highestBitrate = 0

        for (i in 0 until adaptiveFormats.length()) {
            val format = adaptiveFormats.getJSONObject(i)
            val mimeType = format.optString("mimeType", "")
            val bitrate = format.optInt("bitrate", 0)

            if (mimeType.startsWith("audio/")) {
                if (bitrate > highestBitrate) {
                    highestBitrate = bitrate
                    val directUrl = format.optString("url", "")
                    if (directUrl.isNotEmpty()) {
                        bestUrl = directUrl
                    }
                }
            }
        }
        return bestUrl
    }

    /**
     * Extracts synchronized lyrics from the /next endpoint response.
     *
     * Tries two response structures:
     * 1. singleColumnMusicWatchNextResultsRenderer (from /next endpoint)
     * 2. singleColumnBrowseResultsRenderer (from /browse endpoint, legacy)
     */
    override fun extractSynchronizedLyrics(responseObj: JSONObject): String? {
        return try {
            // Path 1: /next endpoint response structure
            val watchNextTabs = responseObj
                .optJSONObject("contents")
                ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                ?.optJSONObject("tabbedRenderer")
                ?.optJSONObject("watchNextTabbedResultsRenderer")
                ?.optJSONArray("tabs")

            if (watchNextTabs != null && watchNextTabs.length() > 1) {
                val lyricsTab = watchNextTabs.optJSONObject(1)
                val endpoint = lyricsTab
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("endpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")

                // If we have a browseId, the lyrics need a separate /browse call.
                // For now, try to extract inline lyrics if present.
                val inlineLyrics = lyricsTab
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")
                    ?.optJSONObject(0)
                    ?.optJSONObject("musicDescriptionShelfRenderer")
                    ?.optJSONObject("description")
                    ?.optJSONArray("runs")
                    ?.optJSONObject(0)
                    ?.optString("text")

                if (!inlineLyrics.isNullOrBlank()) return inlineLyrics
            }

            // Path 2: Legacy /browse endpoint structure
            val browseTabs = responseObj
                .optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")

            browseTabs?.optJSONObject(1)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?.optJSONObject(0)
                ?.optJSONObject("musicDescriptionShelfRenderer")
                ?.optJSONObject("description")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optString("text")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract lyrics", e)
            null
        }
    }
}
