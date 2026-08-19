package com.github.soundpod.musicprofile

data class MusicProfile(
    val favoriteGenres: Set<String> = emptySet(),
    val favoriteArtists: Set<String> = emptySet(),
    val favoriteDecades: Set<String> = emptySet(),
    val favoriteMoods: Set<String> = emptySet(),
    val favoriteInstruments: Set<String> = emptySet(),
    val favoriteLabels: Set<String> = emptySet()
) {
    companion object {
        val defaultGenres = setOf(
            "Modern Metal", "Metalcore", "Post-Hardcore", "Nu Metal", "Grunge",
            "Classic Rock", "Blues Rock", "Hard Rock", "Alternative Metal",
            "Progressive Metal", "Hardcore", "Southern Rock", "Acoustic",
            "MTV Unplugged", "Vinyl Essentials"
        )
        val defaultArtists = setOf(
            "Sleep Token", "Pantera", "Alice In Chains", "Bad Omens", "Spiritbox",
            "Knocked Loose", "Dance Gavin Dance", "I Prevail", "Bring Me The Horizon",
            "Korn", "Limp Bizkit", "Deftones", "Metallica", "Nirvana", "Pearl Jam",
            "Black Sabbath", "Dire Straits", "Queen", "Led Zeppelin",
            "Christone \"Kingfish\" Ingram", "Buddy Guy"
        )
        val defaultDecades = setOf("1970s", "1980s", "1990s", "2000s", "2010s", "2020s")
        val defaultMoods = setOf(
            "Heavy", "Dark", "Emotional", "Groovy", "Angry",
            "Nostalgic", "Soulful", "Acoustic", "Road Trip", "Late Night"
        )
    }
}
