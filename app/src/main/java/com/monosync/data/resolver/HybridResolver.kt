package com.monosync.data.resolver

import android.util.Base64
import android.util.Log
import com.monosync.data.remote.MetrolistExtractor
import com.monosync.data.remote.MonochromeApiService
import com.monosync.data.remote.MonochromeTrackItem
import com.monosync.data.remote.YtmTrack
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * A hybrid resolver that attempts to find a high-quality stream from the
 * Monochrome hifi-api (Tidal proxy), falling back to YouTube Music InnerTube.
 *
 * Flow:
 * 1. Search Monochrome/Tidal for the track by title + artist.
 * 2. Fuzzy-match the best result using Levenshtein distance.
 * 3. Fetch the streaming manifest for the matched Tidal track ID.
 * 4. Decode the base64 manifest to extract a direct audio URL.
 * 5. If any step fails, fall back to YTM InnerTube.
 */
@Singleton
class HybridResolver @Inject constructor(
    private val monochromeApiService: MonochromeApiService,
    private val ytmExtractor: MetrolistExtractor
) {

    companion object {
        private const val TAG = "HybridResolver"
        private const val RELATIVE_THRESHOLD_RATIO = 0.3
        private const val MIN_THRESHOLD = 5
    }

    /**
     * Resolves the best streamable URL for the given [track].
     */
    suspend fun resolveStreamUrl(track: YtmTrack): String {
        val isTidalId = track.videoId.toLongOrNull() != null

        // ── Step 1: If we already have a Tidal ID, fetch manifest directly ─
        if (isTidalId) {
            try {
                val tidalId = track.videoId.toLong()
                val directUrl = fetchTidalStream(tidalId)
                if (!directUrl.isNullOrBlank()) {
                    Log.d(TAG, "Resolved directly via Tidal ID $tidalId")
                    return directUrl
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct Tidal fetch failed for ID ${track.videoId}", e)
            }
        }

        // ── Step 2: Try Monochrome search + match ──────────────────────────
        try {
            val monochromeUrl = resolveViaMonochrome(track)
            if (!monochromeUrl.isNullOrBlank()) {
                Log.d(TAG, "Resolved via Monochrome search for '${track.title}'")
                return monochromeUrl
            }
        } catch (e: Exception) {
            Log.w(TAG, "Monochrome search failed for '${track.title}', falling back to YTM", e)
        }

        // ── Step 3: Fallback to YTM InnerTube ──────────────────────────────
        if (isTidalId) {
            throw Exception("Cannot resolve stream for Tidal track ${track.videoId}")
        }
        return resolveYtmFallback(track.videoId)
    }

    /**
     * Fetches a streaming URL directly from a known Tidal track ID.
     */
    private suspend fun fetchTidalStream(tidalId: Long): String? {
        val trackResponse = monochromeApiService.getTrack(tidalId, quality = "LOSSLESS")
        val manifestData = trackResponse.data ?: return null
        return extractUrlFromManifest(manifestData.manifest, manifestData.manifestMimeType)
    }

    /**
     * Searches Monochrome, finds best match, fetches streaming manifest,
     * and extracts a direct audio URL.
     */
    private suspend fun resolveViaMonochrome(track: YtmTrack): String? {
        val searchQuery = "${track.title} ${track.artist}".trim()
        val response = monochromeApiService.searchTracks(searchQuery, limit = 10)
        val items = response.data?.items ?: return null

        if (items.isEmpty()) return null

        // Fuzzy match
        val bestMatch = findBestMatch(track, items) ?: return null

        return fetchTidalStream(bestMatch.id)
    }

    /**
     * Finds the best matching track from Monochrome results using Levenshtein distance.
     */
    private fun findBestMatch(track: YtmTrack, items: List<MonochromeTrackItem>): MonochromeTrackItem? {
        var bestMatch: MonochromeTrackItem? = null
        var minDistance = Int.MAX_VALUE

        for (item in items) {
            val resultArtist = item.artist?.name ?: item.artists?.firstOrNull()?.name ?: ""
            val titleDistance = levenshteinDistance(
                track.title.lowercase(),
                item.title.lowercase()
            )
            val artistDistance = levenshteinDistance(
                track.artist.lowercase(),
                resultArtist.lowercase()
            )
            val totalDistance = titleDistance + artistDistance

            if (totalDistance < minDistance) {
                minDistance = totalDistance
                bestMatch = item
            }
        }

        val searchQuery = "${track.title} ${track.artist}".trim()
        val maxLength = searchQuery.length
        val threshold = max((maxLength * RELATIVE_THRESHOLD_RATIO).toInt(), MIN_THRESHOLD)

        return if (bestMatch != null && minDistance <= threshold) bestMatch else null
    }

    /**
     * Extracts a direct audio URL from a base64-encoded Tidal manifest.
     * Handles "application/vnd.tidal.bts" (JSON with urls array).
     */
    private fun extractUrlFromManifest(manifest: String?, mimeType: String?): String? {
        if (manifest.isNullOrBlank()) return null

        return try {
            val decoded = String(Base64.decode(manifest, Base64.DEFAULT))

            if (mimeType == "application/vnd.tidal.bts") {
                // JSON manifest: {"mimeType":"audio/flac","codecs":"flac","urls":["https://..."]}
                val json = JSONObject(decoded)
                val urls = json.optJSONArray("urls")
                if (urls != null && urls.length() > 0) {
                    urls.getString(0)
                } else null
            } else {
                // MPD / DASH manifest — not directly playable as a URL
                // For now, use YTM fallback for HI_RES content
                Log.d(TAG, "DASH manifest received, falling back to YTM")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode manifest", e)
            null
        }
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
