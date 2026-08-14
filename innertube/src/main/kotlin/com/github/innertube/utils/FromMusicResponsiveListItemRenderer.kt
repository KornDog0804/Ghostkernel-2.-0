package com.github.innertube.utils

import com.github.innertube.Innertube
import com.github.innertube.models.MusicResponsiveListItemRenderer
import com.github.innertube.models.NavigationEndpoint
import com.github.innertube.models.Runs

private val videoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

fun Innertube.SongItem.Companion.from(renderer: MusicResponsiveListItemRenderer): Innertube.SongItem? {
    return Innertube.SongItem(
        info = renderer
            .flexColumns
            .getOrNull(0)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.getOrNull(0)
            ?.let { run ->
                // YouTube occasionally returns the raw video ID as display text
                // for tracks with missing/degraded metadata. Show something readable instead.
                if (run.text != null && videoIdPattern.matches(run.text)) {
                    run.copy(text = "Unknown Track")
                } else {
                    run
                }
            }
            ?.let(Innertube::Info),
        authors = renderer
            .flexColumns
            .getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.map<Runs.Run, Innertube.Info<NavigationEndpoint.Endpoint.Browse>>(Innertube::Info)
            ?.takeIf(List<Any>::isNotEmpty),
        durationText = renderer
            .fixedColumns
            ?.getOrNull(0)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.getOrNull(0)
            ?.text,
        album = renderer
            .flexColumns
            .getOrNull(2)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.firstOrNull()
            ?.let(Innertube::Info),
        thumbnail = renderer
            .thumbnail
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.lastOrNull()
    ).takeIf { it.info?.endpoint?.videoId != null }
}
