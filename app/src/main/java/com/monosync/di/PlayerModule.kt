package com.monosync.di

import android.content.Context
import com.monosync.playback.MediaControllerManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    fun provideMediaControllerManager(@ApplicationContext context: Context): MediaControllerManager {
        return MediaControllerManager.getInstance(context)
    }
}
