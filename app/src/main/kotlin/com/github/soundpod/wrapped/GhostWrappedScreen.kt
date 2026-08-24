package com.github.soundpod.wrapped

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private val GhostGreen = Color(0xFF7FD41A)

@Composable
fun GhostWrappedScreen(
    onBack: () -> Unit,
    viewModel: GhostWrappedViewModel = viewModel()
) {
    val state = viewModel.state

    LazyColumn(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Ghost Wrapped",
                style = MaterialTheme.typography.headlineLarge,
                color = GhostGreen
            )

            Text(
                text = "Your listening. Your numbers. No borrowed algorithm.",
                color = Color.LightGray
            )
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.load(WrappedPeriod.MONTH)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            if (state.period == WrappedPeriod.MONTH)
                                GhostGreen
                            else
                                Color.DarkGray
                    )
                ) {
                    Text(
                        "This Month",
                        color =
                            if (state.period == WrappedPeriod.MONTH)
                                Color.Black
                            else
                                Color.White
                    )
                }

                Button(
                    onClick = {
                        viewModel.load(WrappedPeriod.YEAR)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            if (state.period == WrappedPeriod.YEAR)
                                GhostGreen
                            else
                                Color.DarkGray
                    )
                ) {
                    Text(
                        "This Year",
                        color =
                            if (state.period == WrappedPeriod.YEAR)
                                Color.Black
                            else
                                Color.White
                    )
                }
            }
        }

        item {
            WrappedHero(
                minutes = state.totalMinutes,
                songs = state.distinctSongs,
                artists = state.distinctArtists
            )
        }

        item {
            WrappedCard(
                title = "Listening",
                lines = listOf(
                    "${state.events} meaningful listening events",
                    "${state.completed} completed listens",
                    "${state.skipped} quick skips"
                )
            )
        }

        if (state.topArtists.isNotEmpty()) {
            item {
                WrappedCard(
                    title = "Top Artists by Minutes",
                    lines =
                        state.topArtists.mapIndexed { index, artist ->
                            "${index + 1}. ${artist.name} • ${artist.totalPlayTime / 60_000L} min"
                        }
                )
            }
        }

        if (state.topSongs.isNotEmpty()) {
            item {
                WrappedCard(
                    title = "Top Songs by Minutes",
                    lines =
                        state.topSongs.mapIndexed { index, song ->
                            val name =
                                song.title
                                    ?.takeIf { it.isNotBlank() }
                                    ?: song.songId

                            "${index + 1}. $name • ${song.totalPlayTime / 60_000L} min"
                        }
                )
            }
        }

        if (state.topAlbums.isNotEmpty()) {
            item {
                WrappedCard(
                    title = "Top Albums by Minutes",
                    lines =
                        state.topAlbums.mapIndexed { index, album ->
                            "${index + 1}. ${album.name} • ${album.totalPlayTime / 60_000L} min"
                        }
                )
            }
        }

        item {
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GhostGreen,
                    contentColor = Color.Black
                )
            ) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun WrappedHero(
    minutes: Long,
    songs: Int,
    artists: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF171717)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "$minutes",
                style = MaterialTheme.typography.displayMedium,
                color = GhostGreen
            )

            Text(
                text = "minutes listened",
                color = Color.White
            )

            Text(
                text = "$songs songs • $artists artists",
                color = Color.LightGray
            )
        }
    }
}

@Composable
private fun WrappedCard(
    title: String,
    lines: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF171717)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = GhostGreen
            )

            lines.forEach { line ->
                Text(
                    text = line,
                    color = Color.White
                )
            }
        }
    }
}
