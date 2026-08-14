package com.github.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import com.github.innertube.Innertube
import com.github.innertube.models.BrowseResponse
import com.github.innertube.models.NextResponse
import com.github.innertube.models.bodies.BrowseBody
import com.github.innertube.models.bodies.NextBody
import com.github.innertube.utils.runCatchingNonCancellable

suspend fun Innertube.lyrics(videoId: String): Result<String?>? = runCatchingNonCancellable {
    val nextResponse = client.post(NEXT) {
        setBody(NextBody(videoId = videoId))
        mask("contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.tabRenderer(endpoint,title)")
    }.body<NextResponse>()

    val tabs = nextResponse
        .contents
        ?.singleColumnMusicWatchNextResultsRenderer
        ?.tabbedRenderer
        ?.watchNextTabbedResultsRenderer
        ?.tabs

    println("GhostKernel-Lyrics(Innertube): tabs found = ${tabs?.map { it.tabRenderer?.title }}")

    val lyricsTab = tabs?.firstOrNull {
        it.tabRenderer?.title?.equals("Lyrics", ignoreCase = true) == true
    } ?: tabs?.getOrNull(1)

    val browseId = lyricsTab
        ?.tabRenderer
        ?.endpoint
        ?.browseEndpoint
        ?.browseId
        ?: return@runCatchingNonCancellable null

    val response = client.post(BROWSE) {
        setBody(BrowseBody(browseId = browseId))
        mask("contents.sectionListRenderer.contents.musicDescriptionShelfRenderer.description")
    }.body<BrowseResponse>()

    response.contents
        ?.sectionListRenderer
        ?.contents
        ?.firstOrNull()
        ?.musicDescriptionShelfRenderer
        ?.description
        ?.text
}