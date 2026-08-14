package com.github.soundpod.musicprofile

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

val GhostGreen = Color(0xFF7FD41A)
val GhostPurple = Color(0xFF6A0DAD)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicProfileScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: MusicProfileViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as Application
        )
    )
    val profile by vm.profile.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Music DNA", color = GhostGreen) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back", color = GhostGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D0D0D)
                )
            )
        },
        containerColor = Color(0xFF0D0D0D)
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SectionHeader("Genres") }
            item {
                ChipGroup(
                    items = MusicProfile.defaultGenres.toList(),
                    selected = profile.favoriteGenres,
                    onToggle = vm::toggleGenre
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
            item { SectionHeader("Artists") }
            item {
                ChipGroup(
                    items = MusicProfile.defaultArtists.toList(),
                    selected = profile.favoriteArtists,
                    onToggle = vm::toggleArtist
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
            item { SectionHeader("Decades") }
            item {
                ChipGroup(
                    items = MusicProfile.defaultDecades.toList(),
                    selected = profile.favoriteDecades,
                    onToggle = vm::toggleDecade
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
            item { SectionHeader("Moods") }
            item {
                ChipGroup(
                    items = MusicProfile.defaultMoods.toList(),
                    selected = profile.favoriteMoods,
                    onToggle = vm::toggleMood
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
            item {
                Button(
                    onClick = { vm.saveProfile(); onBack() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GhostGreen,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Save My DNA", style = MaterialTheme.typography.titleMedium)
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = GhostGreen,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChipGroup(
    items: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            FilterChip(
                selected = item in selected,
                onClick = { onToggle(item) },
                label = { Text(item, color = if (item in selected) Color.Black else Color.White) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GhostGreen,
                    containerColor = Color(0xFF1A1A1A)
                )
            )
        }
    }
}
