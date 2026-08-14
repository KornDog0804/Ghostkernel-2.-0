package com.github.innertube.requests

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import com.github.innertube.Innertube
import com.github.innertube.models.BrowseResponse
import com.github.innertube.models.ContinuationResponse
import com.github.innertube.models.ContinuationActionsResponse
import com.github.innertube.models.MusicCarouselShelfRenderer
import com.github.innertube.models.MusicShelfRenderer
import com.github.innertube.models.MusicPlaylistShelfRenderer
import com.github.innertube.models.bodies.BrowseBody
import com.github.innertube.models.bodies.ContinuationBody
import com.github.innertube.utils.from
import com.github.innertube.utils.runCatchingNonCancellable

private val innertubeJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

var lastPlaylistPageDebug: List<String> = emptyList()

suspend fun Innertube.playlistPage(
    browseId: String,
    params: String? = null
) = runCatchingNonCancellable {
    val httpResponse = client.post(BROWSE) {
        setBody(
            BrowseBody(
                browseId = browseId,
                params = params
            )
        )
    }
    val rawText = httpResponse.bodyAsText()
    println(
        "GhostKernel-RawJSON: browseId=$browseId length=${rawText.length} " +
            "hasContinuationItemRenderer=${rawText.contains("continuationItemRenderer")} " +
            "hasNextContinuationData=${rawText.contains("nextContinuationData")}"
    )
    val response = innertubeJson.decodeFromString<BrowseResponse>(rawText)

    val header = response
        .contents
        ?.twoColumnBrowseResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.contents
        ?.firstOrNull()
        ?.musicResponsiveHeaderRenderer

    val contents = response
        .contents
        ?.twoColumnBrowseResultsRenderer
        ?.secondaryContents
        ?.sectionListRenderer
        ?.contents

    contents?.forEachIndexed { index, section ->
        println(
            "GhostKernel-PaginationSection[$index]: " +
                "musicShelf=${section.musicShelfRenderer != null} " +
                "musicPlaylistShelf=${section.musicPlaylistShelfRenderer != null}"
        )
    }

    // Standard playlists (PL/VL) -- search ALL sections, not just index 0
    val musicShelfRenderer = contents?.firstNotNullOfOrNull { it.musicShelfRenderer }

    // Mixes and Charts (RDCLAK/Mixes) -- search ALL sections, not just index 0
    val musicPlaylistShelfRenderer = contents?.firstNotNullOfOrNull { it.musicPlaylistShelfRenderer }

    println(
        "GhostKernel-PaginationShape: browseId=$browseId " +
            "sections=${contents?.size ?: 0} " +
            "musicShelf=${musicShelfRenderer != null} " +
            "musicPlaylistShelf=${musicPlaylistShelfRenderer != null} " +
            "musicShelfItems=${musicShelfRenderer?.contents?.size ?: 0} " +
            "musicPlaylistShelfItems=${musicPlaylistShelfRenderer?.contents?.size ?: 0}"
    )

    val otherVersionsSection = if (contents?.size == 3) contents.getOrNull(1)
    else {
        val section = contents?.getOrNull(1)
        if (section?.musicCarouselShelfRenderer?.contents?.size == 10) null
        else section
    }

    val relatedAlbumsSection = if (contents?.size == 3) contents.getOrNull(2)
    else {
        val section = contents?.getOrNull(1)
        if (section?.musicCarouselShelfRenderer?.contents?.size == 10) section
        else null
    }

    val standardPage = musicShelfRenderer?.toSongsPage()
    val playlistShelfPage = musicPlaylistShelfRenderer?.toSongsPage()

    val selectedSongsPage = when {
        !standardPage?.items.isNullOrEmpty() -> standardPage
        !playlistShelfPage?.items.isNullOrEmpty() -> playlistShelfPage
        standardPage != null -> standardPage
        else -> playlistShelfPage
    }

    Innertube.PlaylistOrAlbumPage(
        title = Innertube.Info.cleanName(header
            ?.title
            ?.text),
        thumbnail = header
            ?.thumbnail
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.firstOrNull(),
        authors = header
            ?.straplineTextOne
            ?.splitBySeparator()
            ?.getOrNull(0)
            ?.map(Innertube::Info),
        year = header
            ?.subtitle
            ?.splitBySeparator()
            ?.getOrNull(1)
            ?.firstOrNull()
            ?.text,
        url = response
            .microformat
            ?.microformatDataRenderer
            ?.urlCanonical,
        songsPage = selectedSongsPage,
        otherVersions = otherVersionsSection
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(Innertube.AlbumItem::from),
        relatedAlbums = relatedAlbumsSection
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(Innertube.AlbumItem::from)
    )
}

suspend fun Innertube.playlistPageContinuation(continuation: String) = runCatchingNonCancellable {
    val httpResponse = client.post(BROWSE) {
        setBody(ContinuationBody(continuation = continuation))
    }
    val rawText = httpResponse.bodyAsText()
    println(
        "GhostKernel-ContinuationRaw: length=${rawText.length} " +
            "musicShelf=${rawText.contains("musicShelfContinuation")} " +
            "musicPlaylistShelf=${rawText.contains("musicPlaylistShelfContinuation")} " +
            "continuationItem=${rawText.contains("continuationItemRenderer")} " +
            "nextContinuation=${rawText.contains("nextContinuationData")} " +
            "hasError=${rawText.contains("\"error\"")} " +
            "sample=${rawText.take(300).replace("\n", " ")}"
    )

    val response = innertubeJson.decodeFromString<ContinuationResponse>(rawText)

    val standard = response.continuationContents?.musicShelfContinuation
    val playlist = response.continuationContents?.musicPlaylistShelfContinuation

    println(
        "GhostKernel-ContinuationShape: " +
            "musicShelf=${standard != null} " +
            "musicPlaylistShelf=${playlist != null}"
    )

    val fromOldShape = standard?.toSongsPage() ?: playlist?.toSongsPage()

    if (fromOldShape?.items?.isNotEmpty() == true) {
        return@runCatchingNonCancellable fromOldShape
    }

    // Fallback: modern onResponseReceivedActions / appendContinuationItemsAction shape
    val actionsResponse = innertubeJson.decodeFromString<ContinuationActionsResponse>(rawText)

    val continuationItems = actionsResponse
        .onResponseReceivedActions
        ?.firstNotNullOfOrNull { it.appendContinuationItemsAction?.continuationItems }

    val itemToken = continuationItems
        ?.firstNotNullOfOrNull {
            it.continuationItemRenderer
                ?.continuationEndpoint
                ?.continuationCommand
                ?.token
        }

    val songs = continuationItems
        ?.mapNotNull { it.musicResponsiveListItemRenderer }
        ?.mapNotNull(Innertube.SongItem::from)

    println(
        "GhostKernel-ContinuationActionsShape: " +
            "actionsPresent=${actionsResponse.onResponseReceivedActions != null} " +
            "songs=${songs?.size ?: 0} " +
            "itemToken=${itemToken != null}"
    )

    if (songs.isNullOrEmpty()) {
        fromOldShape
    } else {
        Innertube.ItemsPage(
            items = songs,
            continuation = itemToken
        )
    }
}

/**
 * Standard Shelf Converter (PL/VL)
 */
private fun MusicShelfRenderer?.toSongsPage() = this?.let { shelf ->
    val legacyToken = shelf.continuations
        ?.firstOrNull()
        ?.nextContinuationData
        ?.continuation

    val itemToken = shelf.contents
        ?.firstNotNullOfOrNull {
            it.continuationItemRenderer
                ?.continuationEndpoint
                ?.continuationCommand
                ?.token
        }

    val songs = shelf.contents
        ?.mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
        ?.mapNotNull(Innertube.SongItem::from)

    println(
        "GhostKernel-InitialToken: renderer=musicShelfRenderer " +
            "songs=${songs?.size ?: 0} " +
            "legacy=${legacyToken != null} " +
            "itemRenderer=${itemToken != null}"
    )

    Innertube.ItemsPage(
        items = songs,
        continuation = legacyToken ?: itemToken
    )
}

/**
 * Mix/Chart Shelf Converter (RDCLAK) -- also used as fallback shape for
 * standard playlists whose song shelf turns out to be musicPlaylistShelfRenderer
 */
private fun MusicPlaylistShelfRenderer?.toSongsPage() = this?.let { shelf ->
    val legacyToken = shelf.continuations
        ?.firstOrNull()
        ?.nextContinuationData
        ?.continuation

    val itemToken = shelf.contents
        ?.firstNotNullOfOrNull {
            it.continuationItemRenderer
                ?.continuationEndpoint
                ?.continuationCommand
                ?.token
        }

    val songs = shelf.contents
        ?.mapNotNull { it.musicResponsiveListItemRenderer }
        ?.mapNotNull(Innertube.SongItem::from)

    println(
        "GhostKernel-InitialToken: renderer=musicPlaylistShelfRenderer " +
            "songs=${songs?.size ?: 0} " +
            "legacy=${legacyToken != null} " +
            "itemRenderer=${itemToken != null}"
    )

    Innertube.ItemsPage(
        items = songs,
        continuation = legacyToken ?: itemToken
    )
}
