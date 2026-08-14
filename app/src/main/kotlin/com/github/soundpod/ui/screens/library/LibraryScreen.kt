package com.github.soundpod.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.core.ui.LocalAppearance
import com.github.soundpod.viewmodels.LibraryViewModel

@Composable
fun LibraryScreen(
    onPlaylistClick: (String) -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    val playlists by viewModel.playlists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val debugInfo by viewModel.debugInfo.collectAsState()
    val (colorPalette) = LocalAppearance.current

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFF7FD41A)
            )
            playlists.isNotEmpty() -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(playlists) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlaylistClick(playlist.id) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = playlist.title,
                            color = Color(0xFF7FD41A),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            else -> Text(
                text = error ?: "Sign in to see your playlists",
                color = colorPalette.text,
                modifier = Modifier.align(Alignment.Center).padding(16.dp)
            )
        }
        }
    }
}
