package com.github.soundpod.viewmodels.home

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.soundpod.repository.DiscoveryCardData
import com.github.soundpod.repository.GhostBrainRepository
import com.github.soundpod.repository.KornOsBridgeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GhostBrainViewModel : ViewModel() {
    var discoveryCard: DiscoveryCardData? by mutableStateOf(null)
        private set

    private val repository = GhostBrainRepository()
    private val bridgeRepository = KornOsBridgeRepository()

    var bridgeMessage: String? by mutableStateOf(null)
        private set
    var isSyncingToKornOs: Boolean by mutableStateOf(false)
        private set

    fun syncToKornOs() {
        if (isSyncingToKornOs) return
        isSyncingToKornOs = true
        bridgeMessage = null
        viewModelScope.launch(Dispatchers.IO) {
            val result = bridgeRepository.sync(discoveryCard)
            withContext(Dispatchers.Main) {
                isSyncingToKornOs = false
                bridgeMessage = result.message
            }
        }
    }

    fun clearBridgeMessage() {
        bridgeMessage = null
    }

    fun loadDiscoveryCard() {
        val currentHeadline = discoveryCard?.headline
        viewModelScope.launch(Dispatchers.IO) {
            val card = runCatching { repository.getDiscoveryCard(excludeHeadline = currentHeadline) }.getOrElse { e ->
                Log.e("GhostKernel-Brain", "Failed to load discovery card", e)
                null
            }
            Log.d("GhostKernel-Brain", "loadDiscoveryCard() called, previous=$currentHeadline new=${card?.headline}")
            withContext(Dispatchers.Main) {
                discoveryCard = card
            }
        }
    }
}
