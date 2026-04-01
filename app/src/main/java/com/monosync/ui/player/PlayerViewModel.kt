package com.monosync.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monosync.data.remote.YtmTrack
import com.monosync.data.repository.TrackRepository
import com.monosync.model.PlayerState
import com.monosync.model.Resource
import com.monosync.model.Track
import com.monosync.playback.MediaControllerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val mediaManager: MediaControllerManager,
    private val trackRepository: TrackRepository
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = mediaManager.playerCurrentState

    val isPlaying: StateFlow<Boolean> = playerState.map { state ->
        state == PlayerState.Playing || state == PlayerState.Buffering
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    private val _currentTrack = MutableStateFlow(
        Track(title = "Nothing playing", artist = "No artist")
    )
    val currentTrack = _currentTrack.asStateFlow()

    private val _streamState = MutableStateFlow<Resource<String>?>(null)
    val streamState = _streamState.asStateFlow()

    // Seek bar state — polled from MediaController
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    // Shuffle & Repeat state
    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(0) // 0=off, 1=all, 2=one
    val repeatMode = _repeatMode.asStateFlow()

    init {
        // Poll playback position every 250ms for seek bar sync
        viewModelScope.launch {
            while (true) {
                mediaManager.mediaController?.let { controller ->
                    _currentPosition.value = controller.currentPosition.coerceAtLeast(0)
                    val dur = controller.duration
                    if (dur > 0) _duration.value = dur
                }
                delay(250)
            }
        }

        // Cleanup expired cache once per session
        viewModelScope.launch {
            trackRepository.cleanupExpiredCache()
        }
    }

    fun togglePlayPause() {
        val controller = mediaManager.mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun skipNext() {
        mediaManager.mediaController?.seekToNext()
    }

    fun skipPrevious() {
        mediaManager.mediaController?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        mediaManager.mediaController?.seekTo(positionMs)
    }

    fun toggleShuffle() {
        val controller = mediaManager.mediaController ?: return
        val newValue = !_isShuffleEnabled.value
        _isShuffleEnabled.value = newValue
        controller.shuffleModeEnabled = newValue
    }

    fun cycleRepeatMode() {
        val controller = mediaManager.mediaController ?: return
        val nextMode = (_repeatMode.value + 1) % 3
        _repeatMode.value = nextMode
        controller.repeatMode = nextMode
    }

    fun startNewPlayback(url: String, track: Track) {
        _currentTrack.value = track
        mediaManager.playMedia(url)
    }

    fun loadAndPlayTrack(ytmTrack: YtmTrack, trackInfo: Track) {
        val exceptionHandler = CoroutineExceptionHandler { _, exception ->
            _streamState.value = Resource.Error("Network error: ${exception.message}")
        }

        viewModelScope.launch(exceptionHandler) {
            _streamState.value = Resource.Loading
            _currentTrack.value = trackInfo

            val result = trackRepository.getStreamUrl(ytmTrack)
            _streamState.value = result

            if (result is Resource.Success) {
                mediaManager.playMedia(result.data)
            }
        }
    }
}
