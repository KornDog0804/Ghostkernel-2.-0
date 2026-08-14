package com.github.innertube.models.bodies

import com.github.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class PlayerBody(
    val context: Context,
    val videoId: String,
    val playlistId: String? = null,
    val serviceIntegrityDimensions: ServiceIntegrityDimensions? = null,
    val contentCheckOk: Boolean = true,
    val racyCheckOk: Boolean = true,
    val playbackContext: PlaybackContext = PlaybackContext()
)

@Serializable
data class PlaybackContext(
    val contentPlaybackContext: ContentPlaybackContext = ContentPlaybackContext()
)

@Serializable
data class ContentPlaybackContext(
    val referer: String = "https://music.youtube.com/",
    val html5Preference: String = "HTML5_PREF_WANTS"
)
