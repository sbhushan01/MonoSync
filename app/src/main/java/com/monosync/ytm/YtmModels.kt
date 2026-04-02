package com.monosync.ytm

/**
 * Represents a track from YouTube Music search results.
 *
 * @param title    Track title
 * @param artist   Primary artist name
 * @param duration Display duration string (e.g. "3:45")
 * @param thumbnail URL to the track/album thumbnail image
 * @param videoId  YouTube video ID for playback
 * @param explicit Whether the track is marked explicit
 */
data class YtmTrack(
    val title: String,
    val artist: String,
    val duration: String,
    val thumbnail: String,
    val videoId: String,
    val explicit: Boolean = false
)
