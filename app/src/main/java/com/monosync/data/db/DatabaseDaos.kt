package com.monosync.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)

    @Query("SELECT * FROM tracks WHERE trackId = :trackId")
    suspend fun getTrackById(trackId: String): TrackEntity?

    @Query("SELECT * FROM tracks")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Query("UPDATE tracks SET lyrics_lrc = :lyricsLrc WHERE trackId = :trackId")
    suspend fun updateLyrics(trackId: String, lyricsLrc: String)

    @Query("SELECT lyrics_lrc FROM tracks WHERE trackId = :trackId")
    suspend fun getLyricsById(trackId: String): String?
}

@Dao
interface CacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: CacheTable)

    @Query("SELECT * FROM track_cache WHERE trackId = :trackId")
    suspend fun getCacheById(trackId: String): CacheTable?

    @Query("DELETE FROM track_cache WHERE last_updated < :timestamp")
    suspend fun deleteOldCache(timestamp: Long)

    @Query("SELECT COUNT(*) FROM track_cache")
    suspend fun getCacheCount(): Int
}

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(crossRef: PlaylistTrackCrossRef)

    @Query("SELECT * FROM playlists")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Transaction
    @Query("""
        SELECT tracks.* FROM tracks 
        INNER JOIN playlist_track_cross_ref 
        ON tracks.trackId = playlist_track_cross_ref.trackId 
        WHERE playlist_track_cross_ref.playlistId = :playlistId 
        ORDER BY playlist_track_cross_ref.position ASC
    """)
    fun getOrderedTracksForPlaylist(playlistId: Long): Flow<List<TrackEntity>>
}
