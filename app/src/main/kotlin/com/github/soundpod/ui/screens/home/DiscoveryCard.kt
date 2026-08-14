package com.github.soundpod.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.core.ui.LocalAppearance
import com.github.innertube.models.NavigationEndpoint
import com.github.soundpod.LocalPlayerServiceBinder
import com.github.soundpod.models.Song
import com.github.soundpod.utils.asMediaItem
import com.github.soundpod.utils.forcePlayAtIndex
import com.github.soundpod.viewmodels.home.GhostBrainViewModel

@Composable
fun DiscoveryCard() {
    val binder = LocalPlayerServiceBinder.current
    val viewModel: GhostBrainViewModel = viewModel()
    val (colorPalette) = LocalAppearance.current

    LaunchedEffect(Unit) {
        viewModel.loadDiscoveryCard()
    }

    val card = viewModel.discoveryCard ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(colorPalette.background2, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF7FD41A).copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GHOST BRAIN",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF7FD41A),
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = { viewModel.loadDiscoveryCard() },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Refresh",
                    tint = Color(0xFF7FD41A)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = card.headline,
            style = MaterialTheme.typography.titleMedium,
            color = colorPalette.text,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = card.subtext,
            style = MaterialTheme.typography.bodySmall,
            color = colorPalette.textSecondary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    binder?.stopRadio()
                    binder?.player?.forcePlayAtIndex(
                        card.seedSongs.map(Song::asMediaItem),
                        0
                    )
                    card.seedSongs.lastOrNull()?.let { lastSong ->
                        binder?.setupRadio(
                            NavigationEndpoint.Endpoint.Watch(videoId = lastSong.id)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7FD41A),
                    contentColor = Color.Black
                )
            ) {
                Text(text = card.actionLabel)
            }

            Button(
                onClick = { viewModel.syncToKornOs() },
                enabled = !viewModel.isSyncingToKornOs,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorPalette.background1,
                    contentColor = Color(0xFF7FD41A)
                )
            ) {
                if (viewModel.isSyncingToKornOs) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF7FD41A)
                    )
                } else {
                    Text(text = "Send to KornOS")
                }
            }
        }

        viewModel.bridgeMessage?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (message.startsWith("Sent") || message.contains("already")) Color(0xFF7FD41A) else colorPalette.textSecondary
            )
        }
    }
}
