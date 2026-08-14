package com.github.soundpod.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.innertube.Innertube
import com.github.innertube.requests.LibraryDebugInfo
import com.github.innertube.requests.YouTubePlaylist
import com.github.innertube.requests.lastLibraryDebugInfo
import com.github.innertube.requests.libraryPage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val TAG = "GhostLibraryDebug"

class LibraryViewModel : ViewModel() {
    private val _playlists = MutableStateFlow<List<YouTubePlaylist>>(emptyList())
    val playlists: StateFlow<List<YouTubePlaylist>> = _playlists

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _debugInfo = MutableStateFlow<LibraryDebugInfo?>(null)
    val debugInfo: StateFlow<LibraryDebugInfo?> = _debugInfo

    init {
        viewModelScope.launch {
            while (true) {
                if (Innertube.isLoggedIn && _playlists.value.isEmpty() && !_isLoading.value) {
                    load()
                }
                delay(2000)
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = Innertube.libraryPage()
                lastLibraryDebugInfo?.let { info ->
                    info.lines.forEach { Log.d(TAG, it) }
                    _debugInfo.value = info
                }
                result?.onSuccess {
                    if (it.isNullOrEmpty()) {
                        _error.value = "Library debug active. Check logs."
                    } else {
                        _playlists.value = it
                    }
                }
                ?.onFailure { _error.value = it.message }
                ?: run { _error.value = "Not logged in" }
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}")
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
}
