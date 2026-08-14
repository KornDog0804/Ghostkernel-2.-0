package com.github.soundpod.service

import android.database.SQLException
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import com.github.soundpod.db
import com.github.soundpod.models.Event
import com.github.soundpod.query

@UnstableApi
class PlaybackAnalyticsTracker : PlaybackStatsListener.Callback {

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats
    ) {
        val window = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window())
        val mediaItem = window.mediaItem
        val totalPlayTimeMs = playbackStats.totalPlayTimeMs
        val durationMs = window.durationMs
        val completedPercent = if (durationMs > 0) {
            (totalPlayTimeMs.toFloat() / durationMs.toFloat()) * 100f
        } else null

        if (totalPlayTimeMs > 5000) {
            query {
                db.incrementTotalPlayTimeMs(mediaItem.mediaId, totalPlayTimeMs)
            }
        }

        val reachedFortyPercent = completedPercent != null && completedPercent >= 40f
        val isMeaningfulListen = totalPlayTimeMs > 30000 || reachedFortyPercent
        val isSkipped = totalPlayTimeMs < 20000

        if (isMeaningfulListen) {
            println("GhostBrain: meaningful listen saved songId=${mediaItem.mediaId} title=${mediaItem.mediaMetadata.title} playTimeMs=$totalPlayTimeMs completedPercent=$completedPercent")
            query {
                try {
                    db.insert(
                        Event(
                            songId = mediaItem.mediaId,
                            timestamp = System.currentTimeMillis(),
                            playTime = totalPlayTimeMs,
                            title = mediaItem.mediaMetadata.title?.toString(),
                            artist = mediaItem.mediaMetadata.artist?.toString(),
                            album = mediaItem.mediaMetadata.albumTitle?.toString(),
                            completedPercent = completedPercent,
                            skipped = false
                        )
                    )
                } catch (_: SQLException) {
                }
            }
        } else if (isSkipped) {
            println("GhostBrain: skip recorded songId=${mediaItem.mediaId} playTimeMs=$totalPlayTimeMs")
            query {
                try {
                    db.insert(
                        Event(
                            songId = mediaItem.mediaId,
                            timestamp = System.currentTimeMillis(),
                            playTime = totalPlayTimeMs,
                            title = mediaItem.mediaMetadata.title?.toString(),
                            artist = mediaItem.mediaMetadata.artist?.toString(),
                            album = mediaItem.mediaMetadata.albumTitle?.toString(),
                            completedPercent = completedPercent,
                            skipped = true
                        )
                    )
                } catch (_: SQLException) {
                }
            }
        }
    }
}