package com.github.innertube.requests

import com.github.innertube.Innertube
import com.github.innertube.models.Context
import com.github.innertube.models.YouTubeClient
import com.github.innertube.utils.runCatchingNonCancellable
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.Serializable

@Serializable
private data class PlaylistCreateBody(
    val context: Context,
    val title: String,
    val description: String = "",
    val privacyStatus: String = "PRIVATE",
    val videoIds: List<String> = emptyList()
)

@Serializable
private data class PlaylistCreateResponse(
    val playlistId: String? = null
)

@Serializable
private data class PlaylistEditBody(
    val context: Context,
    val playlistId: String,
    val actions: List<PlaylistEditAction>
)

@Serializable
private data class PlaylistEditAction(
    val action: String,
    val addedVideoId: String? = null,
    val removedVideoId: String? = null,
    val setVideoId: String? = null
)

@Serializable
private data class PlaylistDeleteBody(
    val context: Context,
    val playlistId: String
)

/*
 * IMPORTANT:
 *
 * Innertube's defaultRequest executes before request-level attributes are
 * available. LibraryPage already works around that behavior.
 *
 * Playlist mutations therefore inject the authenticated YouTube Music
 * headers explicitly instead of relying only on Attributes.UseCookies.
 */
private fun Innertube.authenticatedHeaders(
    addHeader: (String, String) -> Unit
) {
    val cookieString = cookies
        ?: throw IllegalStateException("YouTube account is not signed in")

    addHeader("Cookie", cookieString)
    addHeader("X-Goog-AuthUser", "0")

    visitorData?.let {
        addHeader("X-Goog-Visitor-Id", it)
    }

    generateSapisidHash(cookieString)?.let {
        addHeader("Authorization", "SAPISIDHASH $it")
    } ?: throw IllegalStateException(
        "Unable to generate YouTube account authorization"
    )
}

suspend fun Innertube.createYouTubePlaylist(
    title: String,
    description: String = "",
    privacyStatus: String = "PRIVATE"
) = runCatchingNonCancellable {
    require(isLoggedIn) {
        "YouTube account is not signed in"
    }

    require(title.isNotBlank()) {
        "Playlist title cannot be blank"
    }

    client.post(PLAYLIST_CREATE) {
        authenticatedHeaders { name, value ->
            header(name, value)
        }

        setBody(
            PlaylistCreateBody(
                context = YouTubeClient.WEB_REMIX.toContext(
                    visitorData = visitorData
                ),
                title = title.trim(),
                description = description,
                privacyStatus = privacyStatus
            )
        )
    }.body<PlaylistCreateResponse>().playlistId
        ?: error("YouTube did not return a playlist ID")
}

suspend fun Innertube.addVideoToYouTubePlaylist(
    playlistId: String,
    videoId: String
) = runCatchingNonCancellable {
    require(isLoggedIn) {
        "YouTube account is not signed in"
    }

    require(playlistId.isNotBlank()) {
        "Missing YouTube playlist ID"
    }

    require(videoId.isNotBlank()) {
        "Missing YouTube video ID"
    }

    val response = client.post(PLAYLIST_EDIT) {
        authenticatedHeaders { name, value ->
            header(name, value)
        }

        setBody(
            PlaylistEditBody(
                context = YouTubeClient.WEB_REMIX.toContext(
                    visitorData = visitorData
                ),
                playlistId = playlistId.removePrefix("VL"),
                actions = listOf(
                    PlaylistEditAction(
                        action = "ACTION_ADD_VIDEO",
                        addedVideoId = videoId
                    )
                )
            )
        )
    }

    check(response.status.value in 200..299) {
        "YouTube add request failed with HTTP ${response.status.value}"
    }

    response
}

suspend fun Innertube.removeVideoFromYouTubePlaylist(
    playlistId: String,
    videoId: String,
    setVideoId: String
) = runCatchingNonCancellable {
    require(isLoggedIn) {
        "YouTube account is not signed in"
    }

    require(playlistId.isNotBlank()) {
        "Missing YouTube playlist ID"
    }

    require(videoId.isNotBlank()) {
        "Missing YouTube video ID"
    }

    require(setVideoId.isNotBlank()) {
        "Missing playlistSetVideoId for this YouTube playlist entry"
    }

    val response = client.post(PLAYLIST_EDIT) {
        authenticatedHeaders { name, value ->
            header(name, value)
        }

        setBody(
            PlaylistEditBody(
                context = YouTubeClient.WEB_REMIX.toContext(
                    visitorData = visitorData
                ),
                playlistId = playlistId.removePrefix("VL"),
                actions = listOf(
                    PlaylistEditAction(
                        action = "ACTION_REMOVE_VIDEO",
                        removedVideoId = videoId,
                        setVideoId = setVideoId
                    )
                )
            )
        )
    }

    check(response.status.value in 200..299) {
        "YouTube remove request failed with HTTP ${response.status.value}"
    }

    response
}

suspend fun Innertube.deleteYouTubePlaylist(
    playlistId: String
) = runCatchingNonCancellable {
    require(isLoggedIn) {
        "YouTube account is not signed in"
    }

    require(playlistId.isNotBlank()) {
        "Missing YouTube playlist ID"
    }

    client.post(PLAYLIST_DELETE) {
        authenticatedHeaders { name, value ->
            header(name, value)
        }

        setBody(
            PlaylistDeleteBody(
                context = YouTubeClient.WEB_REMIX.toContext(
                    visitorData = visitorData
                ),
                playlistId = playlistId.removePrefix("VL")
            )
        )
    }
}
