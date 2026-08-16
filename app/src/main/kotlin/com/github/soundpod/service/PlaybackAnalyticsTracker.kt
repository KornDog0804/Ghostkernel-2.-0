package com.github.soundpod.service

import android.database.SQLException
import android.util.Log
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

    companion object {
        private const val TAG = "GhostBrain"

        private const val MIN_TRACKED_PLAY_MS = 5_000L
        private const val SKIP_THRESHOLD_MS = 20_000L
        private const val MEANINGFUL_THRESHOLD_MS = 30_000L
        private const val MEANINGFUL_PERCENT = 40f
        private const val COMPLETE_PERCENT = 85f
    }

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats
    ) {
        val window =
            eventTime.timeline.getWindow(
                eventTime.windowIndex,
                Timeline.Window()
            )

        val mediaItem = window.mediaItem
        val totalPlayTimeMs = playbackStats.totalPlayTimeMs
        val durationMs = window.durationMs

        val completedPercent =
            if (durationMs > 0) {
                (
                    totalPlayTimeMs.toFloat() /
                        durationMs.toFloat()
                    ) * 100f
            } else {
                null
            }

        if (totalPlayTimeMs > MIN_TRACKED_PLAY_MS) {
            query {
                try {
                    db.incrementTotalPlayTimeMs(
                        mediaItem.mediaId,
                        totalPlayTimeMs
                    )
                } catch (e: SQLException) {
                    Log.e(
                        TAG,
                        "Failed to increment play time for ${mediaItem.mediaId}",
                        e
                    )
                }
            }
        }

        val reachedMeaningfulPercent =
            completedPercent != null &&
                completedPercent >= MEANINGFUL_PERCENT

        val reachedCompletePercent =
            completedPercent != null &&
                completedPercent >= COMPLETE_PERCENT

        val listenType =
            when {
                reachedCompletePercent ->
                    "complete"

                totalPlayTimeMs > MEANINGFUL_THRESHOLD_MS ||
                    reachedMeaningfulPercent ->
                    "meaningful"

                totalPlayTimeMs < SKIP_THRESHOLD_MS ->
                    "skip"

                else ->
                    "neutral"
            }

        val skipped = listenType == "skip"

        /*
         * Ghost Brain cards tag their MediaItems before playback.
         * Normal playback remains "unknown" for now.
         */
        val playbackSource =
            mediaItem.mediaMetadata.extras
                ?.getString("ghost_source")
                ?.takeIf { it.isNotBlank() }
                ?: "unknown"

        val source = "$listenType:$playbackSource"

        Log.i(
            TAG,
            "Playback event type=$listenType " +
                "songId=${mediaItem.mediaId} " +
                "title=${mediaItem.mediaMetadata.title} " +
                "playTimeMs=$totalPlayTimeMs " +
                "completedPercent=$completedPercent"
        )

        /*
         * Do not create brain events for extremely short accidental starts.
         */
        if (totalPlayTimeMs <= MIN_TRACKED_PLAY_MS) {
            return
        }

        query {
            try {
                db.insert(
                    Event(
                        songId = mediaItem.mediaId,
                        timestamp = System.currentTimeMillis(),
                        playTime = totalPlayTimeMs,
                        title =
                            mediaItem.mediaMetadata.title?.toString(),
                        artist =
                            mediaItem.mediaMetadata.artist?.toString(),
                        album =
                            mediaItem.mediaMetadata.albumTitle?.toString(),
                        completedPercent = completedPercent,
                        source = source,
                        skipped = skipped
                    )
                )

                Log.d(
                    TAG,
                    "Saved $listenType event for ${mediaItem.mediaId}"
                )
            } catch (e: SQLException) {
                Log.e(
                    TAG,
                    "Failed to save $listenType playback event for ${mediaItem.mediaId}",
                    e
                )
            }
        }
    }
}
