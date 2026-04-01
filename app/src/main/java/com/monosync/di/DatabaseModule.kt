package com.monosync.di

import android.content.Context
import androidx.room.Room
import com.monosync.data.db.CacheDao
import com.monosync.data.db.MonoSyncDatabase
import com.monosync.data.db.PlaylistDao
import com.monosync.data.db.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideRoomDatabase(@ApplicationContext context: Context): MonoSyncDatabase {
        return Room.databaseBuilder(
            context,
            MonoSyncDatabase::class.java,
            "monosync_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideTrackDao(database: MonoSyncDatabase): TrackDao = database.trackDao()

    @Provides
    @Singleton
    fun provideCacheDao(database: MonoSyncDatabase): CacheDao = database.cacheDao()

    @Provides
    @Singleton
    fun providePlaylistDao(database: MonoSyncDatabase): PlaylistDao = database.playlistDao()
}
