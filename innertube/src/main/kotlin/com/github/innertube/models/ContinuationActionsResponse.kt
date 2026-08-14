package com.github.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class ContinuationActionsResponse(
    val onResponseReceivedActions: List<Action>? = null
) {
    @Serializable
    data class Action(
        val appendContinuationItemsAction: AppendContinuationItemsAction? = null
    )

    @Serializable
    data class AppendContinuationItemsAction(
        val continuationItems: List<MusicPlaylistShelfRenderer.Content>? = null
    )
}
