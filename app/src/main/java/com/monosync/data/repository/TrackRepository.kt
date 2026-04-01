package com.monosync.data.repository

import com.monosync.data.db.CacheDao
import com.monosync.data.db.CacheTable
import com.monosync.data.remote.YtmTrack
import com.monosync.data.resolver.HybridResolver
import com.monosync.model.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackRepository @Inject constructor(
    private val cacheDao: CacheDao,
    private val hybridResolver: HybridResolver
) {
    companion object {
        private const val CACHE_TTL_MS = 21_600_000L // 6 hours
    }

    /**
     * Resolves the audio URL for a given track.
     * 1. Checks Room Cache for a non-expired URL.
     * 2. If not found, uses HybridResolver (monochrome.tf → YTM fallback).
     * 3. Returns Resource.Error if resolution fails.
     */
    suspend fun getStreamUrl(track: YtmTrack): Resource<String> = withContext(Dispatchers.IO) {
        try {
            val cached = cacheDao.getCacheById(track.videoId)
            val isCacheValid = cached != null &&
                (System.currentTimeMillis() - cached.lastUpdated) < CACHE_TTL_MS

            if (isCacheValid && cached != null && cached.resolvedAudioUrl.isNotEmpty()) {
                return@withContext Resource.Success(cached.resolvedAudioUrl)
            }

            val streamUrl = hybridResolver.resolveStreamUrl(track)

            if (streamUrl.isBlank()) {
                return@withContext Resource.Error("Source unavailable or no match found.")
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
     * Cleans up expired cache entries. Call this once per app session
     * rather than on every cache miss to avoid repeated full table scans.
     */
    suspend fun cleanupExpiredCache() = withContext(Dispatchers.IO) {
        cacheDao.deleteOldCache(System.currentTimeMillis() - CACHE_TTL_MS)
    }
}
