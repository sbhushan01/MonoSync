package com.monosync.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monosync.ytm.YtmRepository
import com.monosync.ytm.YtmTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val ytmRepository: YtmRepository
) : ViewModel() {
    
    private val _ytmTracks = MutableStateFlow<List<YtmTrack>>(emptyList())
    val ytmTracks = _ytmTracks.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun searchYtmMusic() {
        viewModelScope.launch {
            _ytmTracks.value = ytmRepository.search(_searchQuery.value)
        }
    }
}
