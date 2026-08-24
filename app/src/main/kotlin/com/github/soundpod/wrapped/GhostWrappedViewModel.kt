package com.github.soundpod.wrapped

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.soundpod.AlbumPlayStat
import com.github.soundpod.ArtistPlayStat
import com.github.soundpod.SongPlayStat
import com.github.soundpod.db
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

enum class WrappedPeriod {
    MONTH,
    YEAR
}

data class GhostWrappedState(
    val loading: Boolean = true,
    val period: WrappedPeriod = WrappedPeriod.MONTH,
    val totalMinutes: Long = 0,
    val events: Int = 0,
    val distinctSongs: Int = 0,
    val distinctArtists: Int = 0,
    val completed: Int = 0,
    val skipped: Int = 0,
    val topArtists: List<ArtistPlayStat> = emptyList(),
    val topAlbums: List<AlbumPlayStat> = emptyList(),
    val topSongs: List<SongPlayStat> = emptyList()
)

class GhostWrappedViewModel : ViewModel() {

    var state: GhostWrappedState by mutableStateOf(GhostWrappedState())
        private set

    init {
        load(WrappedPeriod.MONTH)
    }

    fun load(period: WrappedPeriod) {
        state = state.copy(
            loading = true,
            period = period
        )

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val since = startTimestamp(period)

                GhostWrappedState(
                    loading = false,
                    period = period,
                    totalMinutes =
                        db.totalPlayTimeSince(since).first() / 60_000L,
                    events =
                        db.eventsCountSince(since).first(),
                    distinctSongs =
                        db.distinctSongsSince(since).first(),
                    distinctArtists =
                        db.distinctArtistsSince(since).first(),
                    completed =
                        db.completedCountSince(since).first(),
                    skipped =
                        db.skippedCountSince(since).first(),
                    topArtists =
                        db.wrappedTopArtists(since, 5).first(),
                    topAlbums =
                        db.wrappedTopAlbums(since, 5).first(),
                    topSongs =
                        db.wrappedTopSongs(since, 5).first()
                )
            }

            state = result
        }
    }

    private fun startTimestamp(
        period: WrappedPeriod
    ): Long {
        return Calendar.getInstance().apply {
            when (period) {
                WrappedPeriod.MONTH -> {
                    set(Calendar.DAY_OF_MONTH, 1)
                }

                WrappedPeriod.YEAR -> {
                    set(Calendar.MONTH, Calendar.JANUARY)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
            }

            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
