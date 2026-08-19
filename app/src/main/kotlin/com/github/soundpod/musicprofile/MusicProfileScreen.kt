package com.github.soundpod.musicprofile

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
fun MusicProfileScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val vm: MusicProfileViewModel =
        viewModel(
            factory =
                ViewModelProvider.AndroidViewModelFactory.getInstance(
                    context.applicationContext as Application
                )
        )

    val profile by vm.profile.collectAsStateWithLifecycle()
    val isInitialized by vm.isInitialized.collectAsStateWithLifecycle()

    var starterArtists by rememberSaveable {
        mutableStateOf("")
    }

    var starterGenres by rememberSaveable {
        mutableStateOf("")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "My Music DNA",
                        color = GhostGreen
                    )
                },
                navigationIcon = {
                    TextButton(
                        onClick = onBack
                    ) {
                        Text(
                            "Back",
                            color = GhostGreen
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0D0D0D)
                    )
            )
        },
        containerColor = Color(0xFF0D0D0D)
    ) { padding ->

        if (!isInitialized) {

            Column(
                modifier =
                    Modifier
                        .padding(padding)
                        .padding(20.dp),
                verticalArrangement =
                    Arrangement.spacedBy(16.dp)
            ) {

                Text(
                    text = "Teach GhostKernel",
                    style =
                        MaterialTheme.typography.headlineMedium,
                    color = GhostGreen
                )

                Text(
                    text =
                        "Start with up to 5 artists and 5 genres you love. " +
                        "These only give Ghost Brain a starting point. " +
                        "Your real listening, repeats, likes and skips will " +
                        "reshape your Music DNA over time.",
                    color = Color.White
                )

                OutlinedTextField(
                    value = starterArtists,
                    onValueChange = {
                        starterArtists = it
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    label = {
                        Text("Up to 5 artists")
                    },
                    placeholder = {
                        Text(
                            "Artist 1, Artist 2, Artist 3..."
                        )
                    }
                )

                OutlinedTextField(
                    value = starterGenres,
                    onValueChange = {
                        starterGenres = it
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    label = {
                        Text("Up to 5 genres")
                    },
                    placeholder = {
                        Text(
                            "Metalcore, Hip-Hop, Pop..."
                        )
                    }
                )

                Button(
                    onClick = {
                        val artists =
                            starterArtists
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .take(5)
                                .toSet()

                        val genres =
                            starterGenres
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .take(5)
                                .toSet()

                        vm.initializeStarterDNA(
                            artists = artists,
                            genres = genres
                        )
                    },
                    enabled =
                        starterArtists.isNotBlank() &&
                        starterGenres.isNotBlank(),
                    modifier =
                        Modifier.fillMaxWidth(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = GhostGreen,
                            contentColor = Color.Black
                        )
                ) {
                    Text("Start My Music DNA")
                }
            }

        } else {

            LazyColumn(
                modifier =
                    Modifier
                        .padding(padding)
                        .padding(20.dp),
                verticalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                item {
                    Text(
                        text =
                            "GhostKernel is learning from this device's listening history.",
                        color = Color.LightGray
                    )
                }

                item {
                    SectionHeader("Genres")
                }

                item {
                    DNAChipGroup(
                        profile.favoriteGenres
                            .toList()
                            .sorted()
                    )
                }

                item {
                    Spacer(
                        Modifier.height(8.dp)
                    )
                }

                item {
                    SectionHeader("Artists")
                }

                item {
                    DNAChipGroup(
                        profile.favoriteArtists
                            .toList()
                            .sorted()
                    )
                }

                if (profile.favoriteDecades.isNotEmpty()) {
                    item {
                        Spacer(
                            Modifier.height(8.dp)
                        )
                    }

                    item {
                        SectionHeader("Decades")
                    }

                    item {
                        DNAChipGroup(
                            profile.favoriteDecades
                                .toList()
                                .sorted()
                        )
                    }
                }

                if (profile.favoriteMoods.isNotEmpty()) {
                    item {
                        Spacer(
                            Modifier.height(8.dp)
                        )
                    }

                    item {
                        SectionHeader("Moods")
                    }

                    item {
                        DNAChipGroup(
                            profile.favoriteMoods
                                .toList()
                                .sorted()
                        )
                    }
                }

                item {
                    Spacer(
                        Modifier.height(24.dp)
                    )
                }

                item {
                    OutlinedButton(
                        onClick = {
                            vm.resetMusicDNA()
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Reset Music DNA",
                            color = GhostGreen
                        )
                    }
                }

                item {
                    Text(
                        text =
                            "Reset clears this device's Music DNA and returns " +
                            "to Teach GhostKernel setup.",
                        color = Color.Gray,
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String
) {
    Text(
        text = title,
        style =
            MaterialTheme.typography.titleMedium,
        color = GhostGreen,
        modifier =
            Modifier.padding(
                vertical = 4.dp
            )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DNAChipGroup(
    items: List<String>
) {
    if (items.isEmpty()) {
        Text(
            text = "Still learning...",
            color = Color.Gray
        )
        return
    }

    FlowRow(
        horizontalArrangement =
            Arrangement.spacedBy(8.dp),
        verticalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            Surface(
                color = Color(0xFF1A1A1A),
                shape =
                    MaterialTheme.shapes.medium
            ) {
                Text(
                    text = item,
                    color = Color.White,
                    modifier =
                        Modifier.padding(
                            horizontal = 14.dp,
                            vertical = 9.dp
                        )
                )
            }
        }
    }
}
