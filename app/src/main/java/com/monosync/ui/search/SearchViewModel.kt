package com.monosync.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monosync.data.remote.MonochromeApiService
import com.monosync.data.remote.MonochromeResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val monochromeApiService: MonochromeApiService
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _results = MutableStateFlow<List<MonochromeResult>>(emptyList())
    val results = _results.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    fun search(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            _results.value = emptyList()
            _errorMessage.value = null
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                _results.value = monochromeApiService.searchFiles(trimmedQuery)
            } catch (e: Exception) {
                _results.value = emptyList()
                _errorMessage.value = e.message ?: "Search failed"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
