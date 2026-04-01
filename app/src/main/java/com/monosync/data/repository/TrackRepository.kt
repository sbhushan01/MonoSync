package com.monosync.data.repository

import android.util.Log
import com.monosync.data.db.CacheDao
import com.monosync.data.db.CacheTable
import com.monosync.data.db.TrackDao
import com.monosync.data.remote.MetrolistExtractor
import com.monosync.data.remote.YtmTrack
import com.monosync.data.resolver.HybridResolver
import com.monosync.model.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result holder for [TrackRepository.getTrackDetails].
 *
 * @param streamUrlResult The resolved audio stream URL wrapped in [Resource].
 * @param lyricsLrc       The raw LRC lyrics string, or null if unavailable / fetch failed.
 */
data class TrackDetails(
    val streamUrlResult: Resource<String>,
    val lyricsLrc: String?
)

@Singleton
class TrackRepository @Inject constructor(
    private val cacheDao: CacheDao,
    private val trackDao: TrackDao,
    private val hybridResolver: HybridResolver,
    private val extractor: MetrolistExtractor
) {
    companion object {
        private const val TAG = "TrackRepository"
        private const val CACHE_TTL_MS = 21_600_000L // 6 hours
    }

    /**
     * Fetches both the stream URL and synchronized lyrics **in parallel**.
     *
     * - **Stream URL**: Checked against Room cache first, then resolved via
     *   [HybridResolver] (monochrome.tf → YTM fallback). Cached on success.
     * - **Lyrics**: Checked against Room cache ([TrackDao.getLyricsById]) first.
     *   If not cached, fetches from YTM InnerTube browse endpoint and persists
     *   the raw LRC to the `tracks` table for offline access.
     *
     * If the lyrics fetch fails, the stream URL result is still returned —
     * playback is **never** blocked by a lyrics failure.
     *
     * @param track The YouTube Music track metadata to resolve.
     * @return A [TrackDetails] containing both the stream [Resource] and optional lyrics.
     */
    suspend fun getTrackDetails(track: YtmTrack): TrackDetails = withContext(Dispatchers.IO) {
        coroutineScope {
            // ── Launch both fetches in parallel ──────────────────────────────

            val streamDeferred = async { resolveStreamUrl(track) }
            val lyricsDeferred = async { fetchAndCacheLyrics(track.videoId) }

            // ── Await results ────────────────────────────────────────────────

            val streamResult = streamDeferred.await()

            // Lyrics failure must never crash the caller
            val lyrics = try {
                lyricsDeferred.await()
            } catch (e: Exception) {
                Log.w(TAG, "Lyrics fetch failed for ${track.videoId}, continuing without", e)
                null
            }

            TrackDetails(
                streamUrlResult = streamResult,
                lyricsLrc = lyrics
            )
        }
    }

    /**
     * Resolves the audio URL for a given track.
     * 1. Checks Room Cache for a non-expired URL.
     * 2. If not found, uses HybridResolver (monochrome.tf → YTM fallback).
     * 3. Returns Resource.Error if resolution fails.
     */
    suspend fun getStreamUrl(track: YtmTrack): Resource<String> = withContext(Dispatchers.IO) {
        resolveStreamUrl(track)
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /**
     * Core stream-URL resolution logic, shared by [getStreamUrl] and [getTrackDetails].
     */
    private suspend fun resolveStreamUrl(track: YtmTrack): Resource<String> {
        return try {
            val cached = cacheDao.getCacheById(track.videoId)
            val isCacheValid = cached != null &&
                (System.currentTimeMillis() - cached.lastUpdated) < CACHE_TTL_MS

            if (isCacheValid && cached != null && cached.resolvedAudioUrl.isNotEmpty()) {
                return Resource.Success(cached.resolvedAudioUrl)
            }

            val streamUrl = hybridResolver.resolveStreamUrl(track)

            if (streamUrl.isBlank()) {
                return Resource.Error("Source unavailable or no match found.")
            }

            cacheDao.insertCache(
                CacheTable(
                    trackId = track.videoId,
                    resolvedAudioUrl = streamUrl,
                    lastUpdated = System.currentTimeMillis()
                )
            )

            Resource.Success(streamUrl)
        } catch (e: Exception) {
            Resource.Error("Source Unavailable", exception = e)
        }
    }

    /**
     * Fetches synchronized lyrics, checking the Room cache first.
     *
     * 1. If lyrics are already stored in [TrackDao], returns them immediately.
     * 2. Otherwise calls YTM InnerTube browse endpoint via [MetrolistExtractor],
     *    persists the raw LRC string to the `tracks` table, and returns it.
     * 3. Returns null if no lyrics are available or parsing fails.
     */
    private suspend fun fetchAndCacheLyrics(videoId: String): String? {
        // 1. Check Room cache
        val cachedLyrics = trackDao.getLyricsById(videoId)
        if (!cachedLyrics.isNullOrBlank()) {
            Log.d(TAG, "Lyrics cache hit for $videoId")
            return cachedLyrics
        }

        // 2. Fetch from network
        return try {
            val browseResponse = extractor.getPlayerResponse(videoId)
            val lrcString = extractor.extractSynchronizedLyrics(browseResponse)

            if (!lrcString.isNullOrBlank()) {
                // 3. Persist to Room for offline access
                trackDao.updateLyrics(videoId, lrcString)
                Log.d(TAG, "Fetched and cached lyrics for $videoId")
                lrcString
            } else {
                Log.d(TAG, "No synchronized lyrics available for $videoId")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch lyrics for $videoId", e)
            null
        }
    }

    /**
     * Cleans up expired cache entries. Call this once per app session
     * rather than on every cache miss to avoid repeated full table scans.
     */
    suspend fun cleanupExpiredCache() = withContext(Dispatchers.IO) {
        cacheDao.deleteOldCache(System.currentTimeMillis() - CACHE_TTL_MS)
    }
}
