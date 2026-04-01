package com.monosync.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Data model for results fetched from the monochrome.tf API.
 */
data class MonochromeResult(
    val id: String,
    val title: String,
    val artist: String,
    val streamUrl: String
)

/**
 * Retrofit interface for the Monochrome API endpoint.
 */
interface MonochromeApiService {
    @GET("api/search")
    suspend fun searchFiles(@Query("q") query: String): List<MonochromeResult>
}
