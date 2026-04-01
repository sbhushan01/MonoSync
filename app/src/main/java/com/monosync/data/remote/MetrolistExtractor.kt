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
    fun extractSynchronizedLyrics(browseResponseObj: JSONObject): String?
}

/**
 * YouTube Music Extractor Logic
 * Inspired by the MetrolistGroup/Metrolist GitHub repository.
 *
 * Makes real InnerTube API calls to retrieve player responses
 * containing streaming data, playability status, and metadata.
 */
class MetrolistYtmExtractor(
    private val httpClient: OkHttpClient
) : MetrolistExtractor {

    companion object {
        private const val TAG = "MetrolistYtmExtractor"

        private const val INNERTUBE_PLAYER_URL =
            "https://music.youtube.com/youtubei/v1/player"

        private val INNERTUBE_API_KEY = com.monosync.BuildConfig.YTM_API_KEY

        // WEB_REMIX client — the InnerTube client used by YouTube Music web
        private const val CLIENT_NAME = "WEB_REMIX"
        private const val CLIENT_VERSION = "1.20231214.01.00"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Calls the YouTube Music InnerTube player endpoint for the given [videoId].
     *
     * Constructs the POST body with WEB_REMIX client context, sends the request
     * on [Dispatchers.IO], and parses the response JSON.
     *
     * @param videoId The YouTube video ID to fetch player data for.
     * @return A [JSONObject] containing the full InnerTube player response.
     * @throws IOException on network failures after logging the error.
     */
    override suspend fun getPlayerResponse(videoId: String): JSONObject =
        withContext(Dispatchers.IO) {
            val requestBody = buildPlayerRequestBody(videoId)

            val request = Request.Builder()
                .url("$INNERTUBE_PLAYER_URL?key=$INNERTUBE_API_KEY&prettyPrint=false")
                .header("Content-Type", "application/json")
                .header("User-Agent", "com.google.android.youtube/17.36.4 (Linux; U; Android 12)")
                .header("X-Goog-Api-Format-Version", "2")
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            try {
                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                    Log.e(TAG, "InnerTube request failed: HTTP ${response.code}")
                    throw IOException(
                        "InnerTube player request failed with HTTP ${response.code}"
                    )
                }

                JSONObject(responseBody)

            } catch (e: SocketTimeoutException) {
                Log.e(TAG, "InnerTube request timed out for videoId=$videoId", e)
                throw IOException("InnerTube request timed out", e)

            } catch (e: UnknownHostException) {
                Log.e(TAG, "No network — cannot reach InnerTube for videoId=$videoId", e)
                throw IOException("No network connection available", e)

            } catch (e: IOException) {
                Log.e(TAG, "Network error during InnerTube call for videoId=$videoId", e)
                throw e

            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error parsing InnerTube response for videoId=$videoId", e)
                throw IOException("Failed to parse InnerTube response", e)
            }
        }

    /**
     * Builds the InnerTube JSON request body for the player endpoint.
     */
    private fun buildPlayerRequestBody(videoId: String): JSONObject {
        return JSONObject().apply {
            put("videoId", videoId)

            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", CLIENT_NAME)
                    put("clientVersion", CLIENT_VERSION)
                    put("hl", "en")
                    put("gl", "US")
                    put("platform", "DESKTOP")
                    put("userAgent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/120.0.0.0 Safari/537.36"
                    )
                })

                put("user", JSONObject().apply {
                    put("lockedSafetyMode", false)
                })

                put("request", JSONObject().apply {
                    put("useSsl", true)
                    put("internalExperimentFlags", org.json.JSONArray())
                    put("consistencyTokenJars", org.json.JSONArray())
                })
            })

            put("playbackContext", JSONObject().apply {
                put("contentPlaybackContext", JSONObject().apply {
                    put("signatureTimestamp", "19635")
                })
            })

            put("racyCheckOk", true)
            put("contentCheckOk", true)
        }
    }

    /**
     * Parses the InnerTube JSON response for playback URL extraction.
     * Selects the highest-bitrate audio-only adaptive format with a direct URL.
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

            if (mimeType.startsWith("audio/webm") || mimeType.startsWith("audio/mp4")) {
                if (bitrate > highestBitrate) {
                    highestBitrate = bitrate
                    val directUrl = format.optString("url", "")
                    if (directUrl.isNotEmpty()) {
                        bestUrl = directUrl
                    }
                    // Skip signatureCipher URLs — they require JS runtime deciphering
                }
            }
        }
        return bestUrl
    }

    /**
     * Parses the "next" endpoint JSON response to extract synchronized LRC lyrics.
     */
    override fun extractSynchronizedLyrics(browseResponseObj: JSONObject): String? {
        return try {
            val tabs = browseResponseObj
                .optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")

            tabs?.optJSONObject(1)
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
            e.printStackTrace()
            null
        }
    }
}
