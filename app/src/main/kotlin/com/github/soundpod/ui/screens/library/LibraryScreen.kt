package com.github.soundpod.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.github.core.ui.LocalAppearance
import com.github.soundpod.R
import com.github.soundpod.viewmodels.LibraryViewModel

private val GhostGreen = Color(0xFF7FD41A)
private val GhostPurple = Color(0xFF9B6CFF)

@Composable
fun LibraryScreen(
    onPlaylistClick: (String) -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    val playlists by viewModel.playlists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val (colorPalette) = LocalAppearance.current

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                isLoading && playlists.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = GhostGreen
                    )
                }

                playlists.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = playlists,
                            key = { playlist -> playlist.id }
                        ) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onPlaylistClick(playlist.id)
                                    }
                                    .padding(
                                        horizontal = 14.dp,
                                        vertical = 7.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(MaterialTheme.shapes.medium),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.app_icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(30.dp),
                                        tint = MaterialTheme.colorScheme
                                            .onSurfaceVariant
                                            .copy(alpha = 0.45f)
                                    )

                                    AsyncImage(
                                        model = playlist.thumbnail,
                                        contentDescription = playlist.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(
                                    modifier = Modifier.width(12.dp)
                                )

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = playlist.title,
                                        color = colorPalette.text,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(
                                        modifier = Modifier.size(4.dp)
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = GhostPurple.copy(alpha = 0.16f),
                                            shape = MaterialTheme.shapes.extraSmall
                                        ) {
                                            Text(
                                                text = "YOUTUBE",
                                                color = GhostPurple,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(
                                                    horizontal = 6.dp,
                                                    vertical = 2.dp
                                                )
                                            )
                                        }

                                        Spacer(
                                            modifier = Modifier.width(7.dp)
                                        )

                                        Text(
                                            text = "Account playlist",
                                            color = colorPalette.textSecondary,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    Text(
                        text = error ?: "Sign in to see your playlists",
                        color = colorPalette.text,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}
