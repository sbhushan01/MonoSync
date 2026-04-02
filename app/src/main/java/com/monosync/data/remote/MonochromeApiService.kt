package com.monosync.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

// ── Search Response Models ──────────────────────────────────────────────────────

/**
 * Top-level search response envelope from the hifi-api.
 * Example: GET /search/?s=blinding+lights
 */
data class MonochromeSearchResponse(
    @SerializedName("version") val version: String?,
    @SerializedName("data") val data: MonochromeSearchData?
)

data class MonochromeSearchData(
    @SerializedName("limit") val limit: Int = 25,
    @SerializedName("offset") val offset: Int = 0,
    @SerializedName("totalNumberOfItems") val totalNumberOfItems: Int = 0,
    @SerializedName("items") val items: List<MonochromeTrackItem> = emptyList()
)

data class MonochromeTrackItem(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String = "",
    @SerializedName("duration") val duration: Int = 0,
    @SerializedName("artist") val artist: MonochromeArtist? = null,
    @SerializedName("artists") val artists: List<MonochromeArtist>? = null,
    @SerializedName("album") val album: MonochromeAlbum? = null,
    @SerializedName("audioQuality") val audioQuality: String? = null,
    @SerializedName("explicit") val explicit: Boolean = false,
    @SerializedName("popularity") val popularity: Int = 0
)

data class MonochromeArtist(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("picture") val picture: String? = null
)

data class MonochromeAlbum(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("title") val title: String = "",
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("vibrantColor") val vibrantColor: String? = null
) {
    /**
     * Tidal album art URL.
     * Format: https://resources.tidal.com/images/{uuid}/320x320.jpg
     */
    fun coverUrl(size: Int = 320): String {
        return if (!cover.isNullOrBlank()) {
            val formattedId = cover.replace("-", "/")
            "https://resources.tidal.com/images/$formattedId/${size}x${size}.jpg"
        } else ""
    }
}

// ── Track / Stream Response Models ──────────────────────────────────────────────

/**
 * Response from GET /track/?id=<tidal_id>&quality=LOSSLESS
 */
data class MonochromeTrackResponse(
    @SerializedName("version") val version: String?,
    @SerializedName("data") val data: MonochromeTrackData?
)

data class MonochromeTrackData(
    @SerializedName("trackId") val trackId: Long = 0,
    @SerializedName("audioQuality") val audioQuality: String? = null,
    @SerializedName("manifestMimeType") val manifestMimeType: String? = null,
    @SerializedName("manifest") val manifest: String? = null
)

// ── Legacy model kept for backward compat with existing code ────────────────

data class MonochromeResult(
    @SerializedName("file_id") val fileId: String,
    @SerializedName("track_name") val trackName: String,
    @SerializedName("artist_name") val artistName: String,
    @SerializedName("download_url") val downloadUrl: String
)

// ── Retrofit interface ──────────────────────────────────────────────────────────

/**
 * Retrofit interface for the Monochrome hifi-api.
 * Base URL: https://api.monochrome.tf/
 */
interface MonochromeApiService {

    companion object {
        const val DEFAULT_USER_AGENT = "MonoSync/1.0 (Android)"
    }

    /**
     * Search for tracks via the hifi-api.
     * @param query  The search term (track name, artist, etc.)
     * @param limit  Max results to return (1..500, default 25)
     */
    @GET("search/")
    suspend fun searchTracks(
        @Query("s") query: String,
        @Query("limit") limit: Int = 25,
        @Header("User-Agent") userAgent: String = DEFAULT_USER_AGENT
    ): MonochromeSearchResponse

    /**
     * Get streaming manifest for a Tidal track.
     * @param id       The Tidal track ID
     * @param quality  LOSSLESS, HI_RES_LOSSLESS, HIGH, LOW
     */
    @GET("track/")
    suspend fun getTrack(
        @Query("id") id: Long,
        @Query("quality") quality: String = "LOSSLESS",
        @Header("User-Agent") userAgent: String = DEFAULT_USER_AGENT
    ): MonochromeTrackResponse
}
