package com.monosync.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Track(
    val id: String = "",
    val title: String,
    val artist: String,
    val albumArtUrl: String = "",
    val durationMs: Long = 0L
) : Parcelable {
    val durationFormatted: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}
