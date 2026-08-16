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

    data class BrainMode(
        val label: String,
        val source: String?
    )

    private val brainModes = listOf(
        BrainMode("AUTO", null),
        BrainMode("RABBIT HOLE", "ghost_rabbit_hole"),
        BrainMode("SUPERMIX", "ghost_supermix"),
        BrainMode("REDISCOVER", "ghost_rediscovery"),
        BrainMode("HEAVY ROTATION", "ghost_heavy_rotation"),
        BrainMode("TAKE A CHANCE", "ghost_take_a_chance")
    )

    var brainModeIndex: Int by mutableStateOf(0)
        private set

    val brainModeLabel: String
        get() = brainModes[brainModeIndex].label

    private val requestedSource: String?
        get() = brainModes[brainModeIndex].source

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

    fun cycleBrainMode() {
        brainModeIndex = (brainModeIndex + 1) % brainModes.size
        loadDiscoveryCard()
    }

    fun loadDiscoveryCard() {
        val currentHeadline = discoveryCard?.headline
        val source = requestedSource

        viewModelScope.launch(Dispatchers.IO) {
            val card = runCatching {
                repository.getDiscoveryCard(
                    excludeHeadline = currentHeadline,
                    requestedSource = source
                )
            }.getOrElse { e ->
                Log.e("GhostKernel-Brain", "Failed to load discovery card", e)
                null
            }
            Log.d("GhostKernel-Brain", "loadDiscoveryCard() called, previous=$currentHeadline new=${card?.headline}")
            withContext(Dispatchers.Main) {
                if (card != null) {
                    discoveryCard = card
                }
            }
        }
    }
}
