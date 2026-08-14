package com.github.soundpod.service

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.exoplayer.ExoPlayer
import com.github.soundpod.db
import com.github.soundpod.utils.preferences
import com.github.soundpod.utils.volumeNormalizationKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AudioEffectManager(
    private val context: Context,
    private val player: ExoPlayer,
    private val coroutineScope: CoroutineScope,
    private val gainAudioProcessor: GainAudioProcessor
) {
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null

    // DNA EQ Presets (millibels: 1000 = +10dB, -1000 = -10dB)
    // Bands: 60Hz, 230Hz, 910Hz, 4kHz, 14kHz
    // Subtle presets (max +/-3dB) - flavor, not corrective EQ.
    // Aggressive boosts (+6dB etc.) eat headroom and force the effect chain
    // to reduce overall gain, which is what caused the "quieter/murky" bug.
    private val presetHeavy      = shortArrayOf(250, 150, 0, -50, -100)
    private val presetGroovy     = shortArrayOf(150, 100, 100, 100, 100)
    private val presetEmotional  = shortArrayOf(100, 150, 150, 100, -50)
    private val presetRoadTrip   = shortArrayOf(100, 100, 100, 100, 100)
    private val presetLateNight  = shortArrayOf(-100, 100, 150, 100, -100)
    private val presetFlat       = shortArrayOf(0, 0, 0, 0, 0)

    fun applyDnaPreset(moods: Set<String>) {
        if (player.audioSessionId == 0) return
        try {
            if (equalizer == null) {
                equalizer = Equalizer(0, player.audioSessionId)
            }
            val eq = equalizer ?: return
            eq.enabled = true
            val bands = when {
                moods.any { it in setOf("Heavy", "Angry") }          -> presetHeavy
                moods.any { it in setOf("Groovy", "Road Trip") }     -> presetGroovy
                moods.any { it in setOf("Emotional", "Soulful") }    -> presetEmotional
                moods.any { it in setOf("Road Trip") }               -> presetRoadTrip
                moods.any { it in setOf("Late Night", "Dark") }      -> presetLateNight
                else                                                   -> presetFlat
            }
            for (i in 0 until eq.numberOfBands) {
                eq.setBandLevel(i.toShort(), bands[i])
            }
        } catch (e: Exception) {
            android.util.Log.e("GhostKernel", "EQ preset failed: ${e.message}")
        }
    }
    private var volumeNormalizationJob: Job? = null

    fun maybeNormalizeVolume() {
        if (!context.preferences.getBoolean(volumeNormalizationKey, false)) {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
            loudnessEnhancer = null
            volumeNormalizationJob?.cancel()
            player.volume = 1f
        val savedMoods = context.preferences.getStringSet("dna_moods", emptySet()) ?: emptySet()
        applyDnaPreset(savedMoods)
            return
        }

        if (loudnessEnhancer == null && player.audioSessionId != 0) {
            try {
                loudnessEnhancer = LoudnessEnhancer(player.audioSessionId)
            } catch (e: Exception) {
                return
            }
        }

        player.currentMediaItem?.mediaId?.let { songId ->
            volumeNormalizationJob?.cancel()
            volumeNormalizationJob = coroutineScope.launch(Dispatchers.Main) {
                db.loudnessDb(songId).cancellable().collectLatest { loudnessDb ->
                    try {
                        loudnessEnhancer?.setTargetGain(-((loudnessDb ?: 0f) * 100).toInt() + 800)
                        loudnessEnhancer?.enabled = true
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    fun sendOpenEqualizerIntent() {
        context.sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
        )
    }

    fun sendCloseEqualizerIntent() {
        context.sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
            }
        )
    }


    fun applyDnaMoodPreset() {
        val savedMoods = context.preferences.getStringSet("dna_moods", emptySet()) ?: emptySet()
        applyDnaPreset(savedMoods)
    }
    fun setLoudnessBoost(gainMb: Int) {
        // Real PCM gain via GainAudioProcessor - bypasses Player.volume's 1.0 clamp
        // Clean automatic baseline with optional extra punch.
        val gain = 1.2f + (gainMb / 1000f) * 0.5f
        gainAudioProcessor.gain = gain.coerceIn(1.2f, 1.7f)
    }

    fun release() {
        equalizer?.release()
        equalizer = null
        loudnessEnhancer?.release()
        volumeNormalizationJob?.cancel()
    }
}