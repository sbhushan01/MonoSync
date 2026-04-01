package com.monosync.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TrackEntity::class,
        CacheTable::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(DatabaseConverters::class)
abstract class MonoSyncDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun cacheDao(): CacheDao
    abstract fun playlistDao(): PlaylistDao
}
