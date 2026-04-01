package com.monosync.data.resolver

import com.monosync.data.remote.MetrolistExtractor
import com.monosync.data.remote.MonochromeApiService
import com.monosync.data.remote.MonochromeResult
import com.monosync.data.remote.YtmTrack
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * A hybrid resolver that attempts to find a high-quality stream from monochrome.tf,
 * falling back to YouTube Music's direct streaming URLs.
 */
@Singleton
class HybridResolver @Inject constructor(
    private val monochromeApiService: MonochromeApiService,
    private val ytmExtractor: MetrolistExtractor
) {

    companion object {
        private const val RELATIVE_THRESHOLD_RATIO = 0.2
        private const val MIN_THRESHOLD = 3
    }

    /**
     * Resolves the best streamable URL for the given [track].
     *
     * 1. Formats a query to search monochrome.tf.
     * 2. Uses Levenshtein distance with a relative threshold for fuzzy matching.
     * 3. Returns the monochrome.tf URL if matched, otherwise falls back to YTM InnerTube.
     */
    suspend fun resolveStreamUrl(track: YtmTrack): String {
        val searchQuery = "${track.title} ${track.artist}".trim()

        val monochromeResults = try {
            monochromeApiService.searchFiles(searchQuery)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }

        var bestMatch: MonochromeResult? = null
        var minDistance = Int.MAX_VALUE

        monochromeResults.forEach { result ->
            val titleDistance = levenshteinDistance(
                track.title.lowercase(),
                result.title.lowercase()
            )
            val artistDistance = levenshteinDistance(
                track.artist.lowercase(),
                result.artist.lowercase()
            )
            val totalDistance = titleDistance + artistDistance

            if (totalDistance < minDistance) {
                minDistance = totalDistance
                bestMatch = result
            }
        }

        // Relative threshold: allow up to 20% edit distance, minimum 3
        val maxLength = searchQuery.length
        val threshold = max((maxLength * RELATIVE_THRESHOLD_RATIO).toInt(), MIN_THRESHOLD)
        val isAcceptableMatch = bestMatch != null && minDistance <= threshold

        if (isAcceptableMatch) {
            return bestMatch!!.streamUrl
        }

        return resolveYtmFallback(track.videoId)
    }

    private suspend fun resolveYtmFallback(videoId: String): String {
        return try {
            val playerResponseObj = ytmExtractor.getPlayerResponse(videoId)

            val playabilityStatus = playerResponseObj.optJSONObject("playabilityStatus")
            if (playabilityStatus?.optString("status") != "OK") {
                throw Exception("Video is not playable on YTM.")
            }

            val streamingData = playerResponseObj.optJSONObject("streamingData")
                ?: throw Exception("No streaming data found in YTM response")

            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
                ?: throw Exception("No adaptive formats available")

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

            bestUrl ?: throw Exception("Failed to extract a valid audio URL from YTM fallback.")

        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("Fallback resolution failed for video ID $videoId", e)
        }
    }

    /**
     * Computes the Levenshtein distance between two strings for fuzzy matching.
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
