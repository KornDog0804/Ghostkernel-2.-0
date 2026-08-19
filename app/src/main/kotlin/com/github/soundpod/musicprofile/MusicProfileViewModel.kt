package com.github.soundpod.musicprofile

import android.app.Application
import com.github.soundpod.appContext
import com.github.soundpod.utils.quickPicksCustomGenreKey
import com.github.soundpod.utils.preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MusicProfileViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MusicProfileRepository(app)

    val profile = repo.profile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MusicProfile()
    )

    val isInitialized =
        repo.isInitialized.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )


    fun resetMusicDNA() {
        viewModelScope.launch {
            repo.resetProfile()

            appContext.preferences.edit()
                .remove(quickPicksCustomGenreKey)
                .remove("dna_moods")
                .apply()
        }
    }

    fun initializeStarterDNA(
        artists: Set<String>,
        genres: Set<String>
    ) {
        val cleanArtists =
            artists
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(5)
                .toSet()

        val cleanGenres =
            genres
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(5)
                .toSet()

        viewModelScope.launch {
            repo.initializeStarterProfile(
                artists = cleanArtists,
                genres = cleanGenres
            )

            val dnaQuery =
                cleanGenres
                    .take(3)
                    .joinToString(" ")

            appContext.preferences.edit()
                .putString(
                    quickPicksCustomGenreKey,
                    dnaQuery
                )
                .apply()
        }
    }

    fun toggleGenre(genre: String) {
        viewModelScope.launch {
            val current = profile.value.favoriteGenres.toMutableSet()
            if (genre in current) current.remove(genre) else current.add(genre)
            repo.updateGenres(current)
        }
    }

    fun toggleArtist(artist: String) {
        viewModelScope.launch {
            val current = profile.value.favoriteArtists.toMutableSet()
            if (artist in current) current.remove(artist) else current.add(artist)
            repo.updateArtists(current)
        }
    }

    fun toggleDecade(decade: String) {
        viewModelScope.launch {
            val current = profile.value.favoriteDecades.toMutableSet()
            if (decade in current) current.remove(decade) else current.add(decade)
            repo.updateDecades(current)
        }
    }

    fun saveProfile() {
        val current = profile.value
        viewModelScope.launch {
            repo.updateGenres(current.favoriteGenres)
            repo.updateArtists(current.favoriteArtists)
            repo.updateDecades(current.favoriteDecades)
            repo.updateMoods(current.favoriteMoods)
            // Wire DNA into Quick Picks custom genre query
            val genreParts = current.favoriteGenres.take(3).toList()
            val decadeParts = current.favoriteDecades.take(2).toList()
            val dnaQuery = (genreParts + decadeParts).joinToString(" ")
            // Save moods for EQ preset
            appContext.preferences.edit()
                .putStringSet("dna_moods", current.favoriteMoods)
                .apply()
            if (dnaQuery.isNotBlank()) {
                appContext.preferences.edit()
                    .putString(quickPicksCustomGenreKey, dnaQuery)
                    .apply()
            }
        }
    }

    fun toggleMood(mood: String) {
        viewModelScope.launch {
            val current = profile.value.favoriteMoods.toMutableSet()
            if (mood in current) current.remove(mood) else current.add(mood)
            repo.updateMoods(current)
        }
    }
}
