package com.monosync.data.remote

import org.json.JSONObject

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
}

/**
 * YouTube Music Extractor Logic
 * Inspired by the MetrolistGroup/Metrolist GitHub repository.
 */
class MetrolistYtmExtractor : MetrolistExtractor {

    override suspend fun getPlayerResponse(videoId: String): JSONObject {
        // In production: make an InnerTube API call to YouTube Music
        // For now, return an empty response that the resolver handles gracefully
        return JSONObject()
    }

    /**
     * Parses the InnerTube JSON response for playback URL extraction.
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
    fun extractSynchronizedLyrics(browseResponseObj: JSONObject): String? {
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
