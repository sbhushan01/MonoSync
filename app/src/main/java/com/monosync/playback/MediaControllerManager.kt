package com.monosync.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.monosync.model.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CancellationException

/**
 * Application-scoped singleton managing the MediaController connection.
 * Survives configuration changes (screen rotation) since it's tied to appContext.
 */
class MediaControllerManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val _playerCurrentState = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val playerCurrentState = _playerCurrentState.asStateFlow()

    var mediaController: MediaController? = null
        private set

    init {
        initializeController()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(
            appContext,
            ComponentName(appContext, PlaybackService::class.java)
        )
        val future = MediaController.Builder(appContext, sessionToken).buildAsync()

        future.addListener(
            {
                try {
                    val controller = future.get()
                    mediaController = controller
                    setupPlayerListeners(controller)
                } catch (e: CancellationException) {
                    // Controller connection was cancelled
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            MoreExecutors.directExecutor()
        )
        controllerFuture = future
    }

    private fun setupPlayerListeners(controller: MediaController) {
        updateState(controller)

        controller.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                updateState(controller)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                updateState(controller)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateState(controller)
            }
        })
    }

    private fun updateState(controller: Player) {
        val state = when (controller.playbackState) {
            Player.STATE_BUFFERING -> PlayerState.Buffering
            Player.STATE_READY -> if (controller.playWhenReady) PlayerState.Playing else PlayerState.Paused
            Player.STATE_ENDED -> PlayerState.Ended
            else -> PlayerState.Idle
        }
        _playerCurrentState.value = state
    }

    fun playMedia(url: String) {
        mediaController?.let { controller ->
            val mediaItem = MediaItem.fromUri(url)
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
    }

    fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }

    companion object {
        @Volatile
        private var INSTANCE: MediaControllerManager? = null

        fun getInstance(context: Context): MediaControllerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MediaControllerManager(context).also { INSTANCE = it }
            }
        }
    }
}
