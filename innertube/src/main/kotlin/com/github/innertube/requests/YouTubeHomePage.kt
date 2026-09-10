package com.github.innertube.requests

import com.github.innertube.Innertube
import com.github.innertube.models.BrowseResponse
import com.github.innertube.models.MusicCarouselShelfRenderer
import com.github.innertube.models.MusicTwoRowItemRenderer
import com.github.innertube.models.YouTubeClient
import com.github.innertube.models.bodies.BrowseBody
import com.github.innertube.utils.from
import com.github.innertube.utils.runCatchingNonCancellable
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import java.util.Locale

/**
 * Full authenticated YouTube Music Home feed.
 *
 * Unlike recommendations(), this deliberately preserves every usable
 * carousel returned by FEmusic_home instead of selecting one shelf.
 */
suspend fun Innertube.youtubeHomePage():
    Result<Innertube.YouTubeHomePage?>? = runCatchingNonCancellable {

    if (!hasRequiredTokens) {
        waitForSession(timeoutMs = 10000)
    }

    val response = client.post(BROWSE) {
        attributes.put(Innertube.Attributes.UseCookies, true)

        cookies?.let { cookieString ->
            header("Cookie", cookieString)
            header("X-Goog-AuthUser", "0")

            visitorData?.let {
                header("X-Goog-Visitor-Id", it)
            }

            header("Origin", "https://music.youtube.com")
            header("Referer", "https://music.youtube.com/")
            header("X-Origin", "https://music.youtube.com")

            generateSapisidHash(cookieString)?.let {
                header("Authorization", "SAPISIDHASH $it")
            }
        }

        setBody(
            BrowseBody(
                browseId = "FEmusic_home",
                context = YouTubeClient.WEB_REMIX.toContext(
                    gl = Locale.getDefault().country.ifBlank { "US" },
                    visitorData = visitorData
                )
            )
        )
    }.body<BrowseResponse>()

    val sectionListRenderer =
        response.contents?.sectionListRenderer
            ?: response.contents
                ?.singleColumnBrowseResultsRenderer
                ?.tabs
                ?.firstOrNull()
                ?.tabRenderer
                ?.content
                ?.sectionListRenderer

    val sections = sectionListRenderer
        ?.contents
        .orEmpty()
        .mapNotNull { section ->

            val carousel = section.musicCarouselShelfRenderer
                ?: return@mapNotNull null

            val header =
                carousel.header?.musicCarouselShelfBasicHeaderRenderer

            val title = header
                ?.title
                ?.runs
                ?.firstOrNull()
                ?.text
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null

            val strapline = header
                .strapline
                ?.runs
                ?.firstOrNull()
                ?.text
                ?.trim()
                ?.takeIf { it.isNotBlank() }

            val items = carousel
                .contents
                .orEmpty()
                .mapNotNull { content ->
                    content.toHomeItem()
                }

            if (items.isEmpty()) {
                null
            } else {
                Innertube.YouTubeHomeSection(
                    title = title,
                    strapline = strapline,
                    items = items
                )
            }
        }

    Innertube.YouTubeHomePage(
        sections = sections,
        continuation = sectionListRenderer
            ?.continuations
            ?.firstOrNull()
            ?.nextContinuationData
            ?.continuation
    ).takeIf { it.sections.isNotEmpty() }
}

private fun MusicCarouselShelfRenderer.Content.toHomeItem():
    Innertube.Item? {

    musicResponsiveListItemRenderer?.let { renderer ->
        Innertube.SongItem.from(renderer)?.let {
            return it
        }
    }

    musicTwoRowItemRenderer?.let { renderer ->
        return renderer.toHomeItem()
    }

    return null
}

private fun MusicTwoRowItemRenderer.toHomeItem():
    Innertube.Item? {

    val browse = navigationEndpoint?.browseEndpoint
        ?: title
            ?.runs
            ?.firstOrNull()
            ?.navigationEndpoint
            ?.browseEndpoint
        ?: return null

    val pageType = browse.type.orEmpty().uppercase()
    val browseId = browse.browseId.orEmpty()

    return when {
        "ALBUM" in pageType ->
            Innertube.AlbumItem.from(this)

        "ARTIST" in pageType ->
            Innertube.ArtistItem.from(this)

        "PLAYLIST" in pageType ->
            Innertube.PlaylistItem.from(this)

        browseId.startsWith("MPRE", ignoreCase = true) ->
            Innertube.AlbumItem.from(this)

        browseId.startsWith("UC", ignoreCase = true) ->
            Innertube.ArtistItem.from(this)

        browseId.startsWith("VL", ignoreCase = true) ||
            browseId.startsWith("PL", ignoreCase = true) ||
            browseId.startsWith("RD", ignoreCase = true) ->
            Innertube.PlaylistItem.from(this)

        else -> null
    }
}
