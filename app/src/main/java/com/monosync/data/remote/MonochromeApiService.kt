package com.monosync.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Data model for results fetched from the monochrome.tf API.
 *
 * Backend JSON keys are mapped to idiomatic Kotlin property names
 * via [SerializedName].
 */
data class MonochromeResult(
    @SerializedName("file_id")
    val fileId: String,

    @SerializedName("track_name")
    val trackName: String,

    @SerializedName("artist_name")
    val artistName: String,

    @SerializedName("download_url")
    val downloadUrl: String
)

/**
 * Retrofit interface for the Monochrome API endpoint.
 */
interface MonochromeApiService {

    companion object {
        const val DEFAULT_USER_AGENT = "MonoSync/1.0 (Android)"
    }

    @GET("api/search")
    suspend fun searchFiles(
        @Query("q") query: String,
        @Header("User-Agent") userAgent: String = DEFAULT_USER_AGENT
    ): List<MonochromeResult>
}
