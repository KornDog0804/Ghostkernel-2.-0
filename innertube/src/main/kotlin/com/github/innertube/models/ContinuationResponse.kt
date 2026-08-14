package com.github.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class ContinuationResponse(
    val continuationContents: ContinuationContents?,
) {
    @Serializable
    data class ContinuationContents(
        val musicShelfContinuation: MusicShelfRenderer? = null,
        val musicPlaylistShelfContinuation: MusicPlaylistShelfRenderer? = null,
        val sectionListContinuation: SectionListRenderer? = null,
        val playlistPanelContinuation: NextResponse.MusicQueueRenderer.Content.PlaylistPanelRenderer? = null,
    )
}
