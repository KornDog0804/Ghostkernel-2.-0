package com.github.soundpod.musicprofile

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.musicProfileDataStore by preferencesDataStore(name = "music_profile")

class MusicProfileRepository(private val context: Context) {

    private object Keys {
        val GENRES = stringSetPreferencesKey("favorite_genres")
        val ARTISTS = stringSetPreferencesKey("favorite_artists")
        val DECADES = stringSetPreferencesKey("favorite_decades")
        val MOODS = stringSetPreferencesKey("favorite_moods")
        val INSTRUMENTS = stringSetPreferencesKey("favorite_instruments")
        val LABELS = stringSetPreferencesKey("favorite_labels")
    }

    val profile: Flow<MusicProfile> = context.musicProfileDataStore.data.map { prefs ->
        MusicProfile(
            favoriteGenres = prefs[Keys.GENRES] ?: emptySet(),
            favoriteArtists = prefs[Keys.ARTISTS] ?: emptySet(),
            favoriteDecades = prefs[Keys.DECADES] ?: emptySet(),
            favoriteMoods = prefs[Keys.MOODS] ?: emptySet(),
            favoriteInstruments = prefs[Keys.INSTRUMENTS] ?: emptySet(),
            favoriteLabels = prefs[Keys.LABELS] ?: emptySet()
        )
    }


    suspend fun mergeLearnedArtists(artists: Collection<String>) {
        val cleanArtists =
            artists
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()

        if (cleanArtists.isEmpty()) return

        context.musicProfileDataStore.edit { prefs ->
            val existing = prefs[Keys.ARTISTS] ?: emptySet()

            // Keep starter choices while allowing Ghost Brain to grow the DNA.
            prefs[Keys.ARTISTS] =
                (existing + cleanArtists)
                    .take(25)
                    .toSet()
        }
    }

    suspend fun updateGenres(genres: Set<String>) {
        context.musicProfileDataStore.edit { it[Keys.GENRES] = genres }
    }
    suspend fun updateArtists(artists: Set<String>) {
        context.musicProfileDataStore.edit { it[Keys.ARTISTS] = artists }
    }
    suspend fun updateDecades(decades: Set<String>) {
        context.musicProfileDataStore.edit { it[Keys.DECADES] = decades }
    }
    suspend fun updateMoods(moods: Set<String>) {
        context.musicProfileDataStore.edit { it[Keys.MOODS] = moods }
    }
}
