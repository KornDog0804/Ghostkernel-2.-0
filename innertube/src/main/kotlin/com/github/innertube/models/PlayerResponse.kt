package com.github.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class PlayerResponse(
    val playabilityStatus: PlayabilityStatus?,
    val playerConfig: PlayerConfig?,
    val streamingData: StreamingData?,
    val videoDetails: VideoDetails?,
) {
    @Serializable
    data class PlayabilityStatus(
        val status: String?,
        val reason: String? = null,
        val messages: List<String>? = null
    )

    @Serializable
    data class PlayerConfig(
        val audioConfig: AudioConfig?
    ) {
        @Serializable
        data class AudioConfig(
            private val loudnessDb: Double?,
            private val perceptualLoudnessDb: Double?
        ) {
            // For music clients only
            val normalizedLoudnessDb: Float?
                get() = (loudnessDb ?: perceptualLoudnessDb)?.plus(7)?.toFloat()
        }
    }

    @Serializable
    data class StreamingData(
        val adaptiveFormats: List<AdaptiveFormat>?,
        val formats: List<AdaptiveFormat>? = null
    ) {
        val highestQualityFormat: AdaptiveFormat?
            get() {
                val combined = adaptiveFormats.orEmpty() + formats.orEmpty()
                val audioFormats = combined.filter { (it.url != null || it.signatureCipher != null) && it.mimeType.startsWith("audio/") }
                
                if (audioFormats.isEmpty()) {
                    println("GhostKernel: no audio-only formats in response. combined mimeTypes=${combined.map { it.mimeType }}")
                }
                if (audioFormats.isNotEmpty()) {
                    println("GhostKernel audio formats available:")
                    audioFormats.forEach {
                        val codec = Regex("codecs=\"([^\"]+)\"").find(it.mimeType)?.groupValues?.get(1) ?: it.mimeType
                        println("  itag=${it.itag} codec=$codec bitrate=${it.bitrate}")
                    }
                    fun codecOf(f: AdaptiveFormat) = Regex("codecs=\"([^\"]+)\"").find(f.mimeType)?.groupValues?.get(1) ?: f.mimeType
                    val selected = audioFormats.find { it.itag == 141 } // AAC 256kbps Premium
                        ?: audioFormats.filter { codecOf(it) == "opus" }.maxByOrNull { it.bitrate ?: 0L } // Highest Opus
                        ?: audioFormats.filter { codecOf(it).startsWith("mp4a") }.maxByOrNull { it.bitrate ?: 0L } // Highest AAC
                        ?: audioFormats.maxByOrNull { it.bitrate ?: 0L } // Highest bitrate remaining (original fallback)
                        selected?.let {
                        println("GhostKernel selected stream: itag=${it.itag} codec=${codecOf(it)} bitrate=${it.bitrate}")
                    }
                    return selected
                }

                println("GhostKernel: no usable audio-only stream, refusing muxed video fallback for this response")
                return null
            }

        @Serializable
        data class AdaptiveFormat(
            val itag: Int,
            val mimeType: String,
            val bitrate: Long?,
            val averageBitrate: Long?,
            val contentLength: Long?,
            val audioQuality: String?,
            val approxDurationMs: Long?,
            val lastModified: Long?,
            val loudnessDb: Double?,
            val audioSampleRate: Int?,
            val url: String? = null,
            val signatureCipher: String? = null,
        )
    }

    @Serializable
    data class VideoDetails(
        val videoId: String?
    )
}
