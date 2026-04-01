package com.monosync.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artists: List<String>,
    val albumArtUrl: String,
    val durationMs: Long,
    val resolvedAudioUrl: String? = null
)

@Entity(tableName = "track_cache")
data class CacheTable(
    @PrimaryKey val trackId: String,
    val resolvedAudioUrl: String,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val playlistId: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_track_cross_ref",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["trackId"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("playlistId"),
        Index("trackId"),
        Index(value = ["playlistId", "position"], unique = true)
    ]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: String,
    val position: Int
)

class DatabaseConverters {
    private val separator = "|||"

    @TypeConverter
    fun fromArtistList(artists: List<String>?): String {
        return artists?.joinToString(separator) ?: ""
    }

    @TypeConverter
    fun toArtistList(artistsString: String?): List<String> {
        if (artistsString.isNullOrEmpty()) return emptyList()
        return artistsString.split(separator)
    }
}
