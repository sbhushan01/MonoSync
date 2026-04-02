package com.monosync.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monosync.model.Track
import com.monosync.ytm.YtmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val ytmRepository: YtmRepository
) : ViewModel() {

    private val _quickPicks = MutableStateFlow<List<Track>>(emptyList())
    val quickPicks = _quickPicks.asStateFlow()

    private val _recentTracks = MutableStateFlow<List<Track>>(emptyList())
    val recentTracks = _recentTracks.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    init {
        loadFeaturedTracks()
    }

    private fun loadFeaturedTracks() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Search for popular tracks to populate the home screen
                val trendingResults = ytmRepository.search("trending music 2025")
                val popularResults = ytmRepository.search("popular songs")

                _quickPicks.value = trendingResults.take(6).map { ytm ->
                    Track(
                        id = ytm.videoId,
                        title = ytm.title,
                        artist = ytm.artist,
                        albumArtUrl = ytm.thumbnail,
                        durationMs = parseDurationToMs(ytm.duration)
                    )
                }

                _recentTracks.value = popularResults.take(8).map { ytm ->
                    Track(
                        id = ytm.videoId,
                        title = ytm.title,
                        artist = ytm.artist,
                        albumArtUrl = ytm.thumbnail,
                        durationMs = parseDurationToMs(ytm.duration)
                    )
                }
            } catch (e: Exception) {
                // Show placeholder tracks if network fails
                _quickPicks.value = emptyList()
                _recentTracks.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        loadFeaturedTracks()
    }

    private fun parseDurationToMs(duration: String): Long {
        val parts = duration.split(":")
        return try {
            when (parts.size) {
                2 -> (parts[0].toLong() * 60 + parts[1].toLong()) * 1000
                3 -> (parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()) * 1000
                else -> 0L
            }
        } catch (e: NumberFormatException) {
            0L
        }
    }
}
