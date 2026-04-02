package com.monosync.data.resolver

import android.util.Log
import com.monosync.data.remote.MetrolistExtractor
import com.monosync.data.remote.YtmTrack
import com.monosync.ytm.YtmRepository
import com.monosync.ytm.YtmTrack as YtmSearchResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves audio stream URLs for tracks.
 *
 * Strategy:
 * 1. If the track has a YouTube video ID (non-numeric), use InnerTube directly.
 * 2. If the track has a Tidal ID (numeric, from Monochrome search), search YTM
 *    by title+artist to find a matching video ID, then use InnerTube.
 *
 * InnerTube is the ONLY reliable source for actual stream URLs.
 * The Monochrome hifi-api is used only for search/discovery, not streaming.
 */
@Singleton
class HybridResolver @Inject constructor(
    private val ytmExtractor: MetrolistExtractor,
    private val ytmRepository: YtmRepository
) {

    companion object {
        private const val TAG = "HybridResolver"
    }

    /**
     * Resolves the best streamable URL for the given [track].
     */
    suspend fun resolveStreamUrl(track: YtmTrack): String {
        val isTidalId = track.videoId.toLongOrNull() != null

        if (!isTidalId) {
            // ── Direct YouTube video ID — resolve via InnerTube ─────────────
            return resolveYtmStream(track.videoId)
        }

        // ── Tidal ID — need to find a YouTube video ID first ───────────────
        Log.d(TAG, "Track '${track.title}' has Tidal ID ${track.videoId}, searching YTM...")
        val searchQuery = "${track.title} ${track.artist}".trim()
        val ytmResults = ytmRepository.search(searchQuery)

        if (ytmResults.isNotEmpty()) {
            // Use the first result's video ID
            val bestMatch = ytmResults.first()
            Log.d(TAG, "Found YTM match: '${bestMatch.title}' by '${bestMatch.artist}' [${bestMatch.videoId}]")
            return resolveYtmStream(bestMatch.videoId)
        }

        throw Exception("No playable source found for '${track.title}' by '${track.artist}'")
    }

    /**
     * Resolves an audio stream URL from a YouTube video ID via InnerTube.
     */
    private suspend fun resolveYtmStream(videoId: String): String {
        return try {
            val playerResponse = ytmExtractor.getPlayerResponse(videoId)

            val playabilityStatus = playerResponse.optJSONObject("playabilityStatus")
            if (playabilityStatus?.optString("status") != "OK") {
                val reason = playabilityStatus?.optString("reason") ?: "Unknown"
                throw Exception("Video not playable: $reason")
            }

            val streamingData = playerResponse.optJSONObject("streamingData")
                ?: throw Exception("No streaming data in response")

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

            bestUrl ?: throw Exception("No direct audio URL found (may require signature deciphering)")

        } catch (e: Exception) {
            Log.e(TAG, "InnerTube stream resolution failed for $videoId", e)
            throw Exception("Stream resolution failed for $videoId: ${e.message}", e)
        }
    }
}
