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
            favoriteGenres = prefs[Keys.GENRES] ?: MusicProfile.defaultGenres,
            favoriteArtists = prefs[Keys.ARTISTS] ?: MusicProfile.defaultArtists,
            favoriteDecades = prefs[Keys.DECADES] ?: MusicProfile.defaultDecades,
            favoriteMoods = prefs[Keys.MOODS] ?: MusicProfile.defaultMoods,
            favoriteInstruments = prefs[Keys.INSTRUMENTS] ?: emptySet(),
            favoriteLabels = prefs[Keys.LABELS] ?: emptySet()
        )
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
