package com.github.soundpod.repository

import com.github.innertube.Innertube
import com.github.innertube.requests.relatedPage
import com.github.innertube.requests.searchPage
import com.github.innertube.utils.from
import com.github.soundpod.db
import com.github.soundpod.appContext
import com.github.soundpod.musicprofile.MusicProfileRepository
import com.github.soundpod.models.Song
import com.github.soundpod.utils.asMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar

data class DiscoveryCardData(
    val headline: String,
    val subtext: String,
    val actionLabel: String,
    val seedSongs: List<Song>,
    val source: String
)

class GhostBrainRepository {

    private val musicProfileRepository =
        MusicProfileRepository(appContext)

    suspend fun getDiscoveryCard(
        excludeHeadline: String? = null,
        requestedSource: String? = null
    ): DiscoveryCardData? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val candidates = mutableListOf<DiscoveryCardData>()
        // Earned/context-specific signals - these win outright over the generic pool
        // when eligible, instead of competing on a flat coin flip against SuperMix etc.
        val priorityCandidates = mutableListOf<DiscoveryCardData>()
        val mostPlayed = runCatching { db.mostPlayedSongs(60).first() }.getOrNull().orEmpty()

        /*
         * Music DNA ← Ghost Brain
         *
         * Real listening history gradually becomes part of this device's
         * personal Music DNA. Starter choices are preserved and learned
         * artists are merged in rather than replacing them.
         */
        val learnedArtists =
            runCatching {
                db.mostPlayedArtists(12)
                    .first()
                    .map { it.name }
            }.getOrNull().orEmpty()

        val dnaInitialized =
            runCatching {
                musicProfileRepository.isInitialized.first()
            }.getOrDefault(false)

        if (dnaInitialized && learnedArtists.isNotEmpty()) {
            runCatching {
                musicProfileRepository.mergeLearnedArtists(learnedArtists)
            }
        }

        // Rediscovery - songs not played in 21+ days
        val rediscoveryCutoff = now - (21L * 24 * 60 * 60 * 1000)
        val rediscovery = runCatching { db.rediscoveryCandidates(rediscoveryCutoff, 5).first() }.getOrNull().orEmpty()
        if (rediscovery.isNotEmpty()) {
            val song = rediscovery.random()
            candidates += DiscoveryCardData(
                headline = "Time to revisit ${song.artistsText ?: song.title}",
                source = "ghost_rediscovery",
                subtext = "You haven't played \"${song.title}\" in a while. Give it another spin.",
                actionLabel = "Play Again",
                seedSongs = listOf(song)
            )
        }

        // Heavy rotation - actually recent (7 day) top artist
        val weekCutoff = now - (7L * 24 * 60 * 60 * 1000)
        val recentArtists =
            runCatching {
                db.mostPlayedArtistsSince(weekCutoff, 8).first()
            }.getOrNull().orEmpty()
        var heavyRotationArtist: String? = null
        if (recentArtists.isNotEmpty()) {
            val artist = recentArtists.first()
            heavyRotationArtist = artist.name
            val artistSongs = mostPlayed.filter { it.artistsText == artist.name }.take(5)
            if (artistSongs.isNotEmpty()) {
                candidates += DiscoveryCardData(
                    headline = "You've been living in ${artist.name} lately",
                    source = "ghost_heavy_rotation",
                    subtext = "${artist.playCount} plays this week.",
                    actionLabel = "Keep Going",
                    seedSongs = artistSongs
                )
            }

            /*
             * Rabbit Hole 4.3
             *
             * Follow proven listening relationships, but build a deeper,
             * better-spaced queue for the learned artist chain.
             */
            val rabbitHole =
                recentArtists
                    .shuffled()
                    .mapNotNull { candidate ->
                        val candidateChain =
                            buildArtistChain(
                                candidate.name,
                                maxHops = 4
                            )

                        if (candidateChain.size >= 2) {
                            candidateChain
                        } else {
                            null
                        }
                    }
                    .firstOrNull()

            if (rabbitHole != null) {
                val cleanRabbitHole =
                    rabbitHole
                        .map(::cleanRabbitArtistName)
                        .filter { it.isNotBlank() }
                        .distinctBy(::normalizeRabbitArtist)

                val usedIds = mutableSetOf<String>()

                val artistPools =
                    cleanRabbitHole.map { artistName ->
                        songsForRabbitArtist(
                            artistName = artistName,
                            historySongs = mostPlayed,
                            excludedIds = usedIds
                        )
                            .also { songs ->
                                usedIds += songs.map { it.id }
                            }
                            .toMutableList()
                    }

                val roundRobin = mutableListOf<Song>()
                var addedSomething = true

                while (addedSomething && roundRobin.size < 24) {
                    addedSomething = false

                    artistPools.forEach { pool ->
                        if (pool.isNotEmpty() && roundRobin.size < 24) {
                            roundRobin += pool.removeAt(0)
                            addedSomething = true
                        }
                    }
                }

                val chainSongs =
                    spaceArtists(
                        roundRobin.distinctBy { it.id },
                        preferredGap = 3
                    )
                        .take(24)

                if (
                    chainSongs.isNotEmpty() &&
                    cleanRabbitHole.size >= 2
                ) {
                    val journey =
                        cleanRabbitHole
                            .drop(1)
                            .joinToString(", ")

                    priorityCandidates +=
                        DiscoveryCardData(
                            headline =
                                "Every time you play ${cleanRabbitHole.first()}, ${cleanRabbitHole[1]} follows",
                            source = "ghost_rabbit_hole",
                            subtext =
                                "You started with ${cleanRabbitHole.first()}. Ghost Brain followed the pattern through $journey.",
                            actionLabel = "Start Rabbit Hole",
                            seedSongs = chainSongs
                        )
                }
            }
        }

        // Repeat artist - all-time favorite, only if different from this week's pick
        val topArtists = runCatching { db.mostPlayedArtists(3).first() }.getOrNull().orEmpty()
        if (topArtists.isNotEmpty()) {
            val artist = topArtists.first()
            if (artist.name != heavyRotationArtist) {
                val artistSongs = mostPlayed.filter { it.artistsText == artist.name }.take(5)
                if (artistSongs.isNotEmpty()) {
                    candidates += DiscoveryCardData(
                        headline = "You keep coming back to ${artist.name}",
                        source = "ghost_favorite_artist",
                        subtext = "${artist.playCount} plays all-time.",
                        actionLabel = "Keep Going",
                        seedSongs = artistSongs
                    )
                }
            }
        }

        // SuperMix 2.0
        //
        // Ghost Brain chooses the taste anchors from real listening history.
        // YouTube Music then expands those anchors using relatedPage().
        //
        // This keeps SuperMix personal while allowing it to discover songs
        // beyond what already exists in the local database.
        val topArtistsForMix =
            runCatching {
                db.mostPlayedArtists(8).first()
            }.getOrNull().orEmpty()

        if (topArtistsForMix.size >= 3) {

            val localMixSongs =
                topArtistsForMix
                    .flatMap { artist ->
                        mostPlayed
                            .filter { it.artistsText == artist.name }
                            .take(3)
                    }
                    .distinctBy { it.id }

            /*
             * Pick several strong Ghost Brain songs as YouTube discovery anchors.
             *
             * Using more than one seed prevents SuperMix from becoming
             * "radio for one song".
             */
            val youtubeSeeds =
                localMixSongs
                    .shuffled()
                    .take(6)

            val youtubeExpansion =
                youtubeSeeds
                    .flatMap { seedSong ->

                        runCatching {

                            Innertube
                                .relatedPage(videoId = seedSong.id)
                                ?.getOrNull()
                                ?.songs
                                .orEmpty()
                                .take(10)
                                .map { item ->

                                    val mediaItem = item.asMediaItem

                                    Song(
                                        id = mediaItem.mediaId,
                                        title =
                                            mediaItem
                                                .mediaMetadata
                                                .title
                                                ?.toString()
                                                ?: mediaItem.mediaId,
                                        artistsText =
                                            mediaItem
                                                .mediaMetadata
                                                .artist
                                                ?.toString(),
                                        durationText =
                                            mediaItem
                                                .mediaMetadata
                                                .extras
                                                ?.getString("durationText"),
                                        thumbnailUrl =
                                            mediaItem
                                                .mediaMetadata
                                                .artworkUri
                                                ?.toString()
                                    )
                                }

                        }.getOrNull().orEmpty()
                    }
                    .distinctBy { it.id }

            /*
             * Blend known-good history with YouTube discovery.
             *
             * Local favorites remain represented, but related songs make
             * the mix expand outward from Joey's actual taste.
             */
            val mixSongs =
                (localMixSongs.shuffled().take(12) +
                    youtubeExpansion.shuffled().take(28))
                    .distinctBy { it.id }
                    .shuffled()
                    .take(40)

            if (mixSongs.isNotEmpty()) {

                candidates +=
                    DiscoveryCardData(
                        headline = "Your SuperMix",
                        source = "ghost_supermix",
                        subtext =
                            if (youtubeExpansion.isNotEmpty()) {
                                "Ghost Brain blended your listening history with ${youtubeExpansion.size} YouTube Music discoveries."
                            } else {
                                "A blend across ${topArtistsForMix.size} of your most-played artists."
                            },
                        actionLabel = "Shuffle SuperMix",
                        seedSongs = mixSongs
                    )
            }
        }

        // Late-night pattern - only eligible if it's actually late night right now
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (currentHour >= 22 || currentHour < 4) {
            val lateNightArtists = runCatching { db.lateNightArtists(3).first() }.getOrNull().orEmpty()
            if (lateNightArtists.isNotEmpty()) {
                val artist = lateNightArtists.first()
                val artistSongs = mostPlayed.filter { it.artistsText == artist.name }.take(5)
                if (artistSongs.isNotEmpty()) {
                    priorityCandidates += DiscoveryCardData(
                        headline = "This is your late-night sound",
                        source = "ghost_late_night",
                        subtext = "You reach for ${artist.name} more than anything else after dark.",
                        actionLabel = "Play",
                        seedSongs = artistSongs
                    )
                }
            }
        }

        // Skip-pattern correction - only eligible if you're actually skipping a lot right now
        val skipWindow = now - (60L * 60 * 1000)
        val recentSkips = runCatching { db.recentSkipCount(skipWindow).first() }.getOrNull() ?: 0
        if (recentSkips >= 3) {
            /*
             * Never Miss / skip-recovery rotation.
             *
             * Pull a larger proven pool, then rotate five tracks from it
             * instead of permanently showing the exact same top five.
             */
            val comfortSongs =
                runCatching {
                    db.highestCompletionSongs(30)
                        .first()
                        .shuffled()
                        .take(5)
                }.getOrNull().orEmpty()
            if (comfortSongs.isNotEmpty()) {
                priorityCandidates += DiscoveryCardData(
                    headline = "Not feeling it today?",
                    source = "ghost_skip_recovery",
                    subtext = "You've skipped $recentSkips tracks recently. Here's stuff you always finish.",
                    actionLabel = "Play Something Sure",
                    seedSongs = comfortSongs
                )
            }
        }

        // Take a Chance - a wildcard artist you've barely explored
        val wildcards = runCatching { db.wildcardArtists(3, 5).first() }.getOrNull().orEmpty()
        if (wildcards.isNotEmpty()) {
            val artist = wildcards.random()
            val localSongs = runCatching { db.songsForArtist(artist.name, 5).first() }.getOrNull().orEmpty()

            // Local history is often just 1-2 plays (that's the whole point of this card) -
            // pull the rest of the queue from Innertube so it's a real sampler, not one-and-done
            val extraNeeded = 5 - localSongs.size
            val extraSongs: List<Song> = if (extraNeeded > 0) {
                runCatching {
                    Innertube.searchPage(
                        query = artist.name,
                        params = Innertube.SearchFilter.Song.value,
                        fromMusicShelfRendererContent = Innertube.SongItem.Companion::from
                    )?.getOrNull()?.items?.take(extraNeeded)?.mapNotNull { item ->
                        val mediaItem = item.asMediaItem
                        Song(
                            id = mediaItem.mediaId,
                            title = mediaItem.mediaMetadata.title.toString(),
                            artistsText = mediaItem.mediaMetadata.artist.toString(),
                            durationText = null,
                            thumbnailUrl = mediaItem.mediaMetadata.artworkUri.toString()
                        )
                    }
                }.getOrNull().orEmpty()
            } else emptyList()

            val wildcardSongs = (localSongs + extraSongs).distinctBy { it.id }

            if (wildcardSongs.isNotEmpty()) {
                val plays = if (artist.playCount == 1) "once" else "${artist.playCount} times"
                candidates += DiscoveryCardData(
                    headline = "Take a Chance on ${artist.name}",
                    source = "ghost_take_a_chance",
                    subtext = "You've only played them $plays. Worth a real shot?",
                    actionLabel = "Take a Chance",
                    seedSongs = wildcardSongs
                )
            }
        }

        // Cold start fallback - only used if nothing else is eligible yet
        if (candidates.isEmpty() && priorityCandidates.isEmpty()) {
            val topSongs = runCatching { db.mostPlayedSongs(5).first() }.getOrNull().orEmpty()
            if (topSongs.isNotEmpty()) {
                candidates += DiscoveryCardData(
                    headline = "Your most played track",
                    source = "ghost_top_track",
                    subtext = "\"${topSongs.first().title}\" is on repeat.",
                    actionLabel = "Play",
                    seedSongs = topSongs
                )
            }
        }

        val filteredPriority = priorityCandidates.filter { it.headline != excludeHeadline }
        val filteredCandidates = candidates.filter { it.headline != excludeHeadline }

        val priorityPool = filteredPriority.ifEmpty { priorityCandidates }
        val candidatePool = filteredCandidates.ifEmpty { candidates }

        if (requestedSource != null) {
            (priorityCandidates + candidates)
                .filter { it.source == requestedSource }
                .let { matching ->
                    matching
                        .filter { it.headline != excludeHeadline }
                        .ifEmpty { matching }
                        .randomOrNull()
                }
        } else {
            priorityPool.randomOrNull() ?: candidatePool.randomOrNull()
        }
    }


    private fun cleanRabbitArtistName(
        value: String
    ): String {
        val parts =
            value
                .split("•")
                .map { it.trim() }
                .filter { part ->
                    part.isNotBlank() &&
                        part.any { it.isLetterOrDigit() }
                }

        if (parts.size <= 1) {
            return value.trim()
        }

        val usefulParts =
            parts.filterNot { part ->
                val normalized =
                    part
                        .lowercase()
                        .replace(
                            Regex("[^a-z0-9]+"),
                            " "
                        )
                        .trim()

                normalized.isBlank() ||
                    normalized == "and" ||
                    normalized == "records" ||
                    normalized.endsWith(" records") ||
                    normalized == "music" ||
                    normalized.endsWith(" music") ||
                    normalized.endsWith(" topic") ||
                    normalized.endsWith(" vevo")
            }

        return when {
            usefulParts.isEmpty() -> value.trim()
            usefulParts.size == 1 -> usefulParts.first()
            usefulParts.size < parts.size -> usefulParts.last()
            else -> value.trim()
        }
    }

    private fun normalizeRabbitArtist(
        value: String
    ): String =
        cleanRabbitArtistName(value)
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private fun rabbitArtistMatches(
        song: Song,
        artistName: String
    ): Boolean {
        val targetArtist =
            normalizeRabbitArtist(artistName)

        val songArtist =
            normalizeRabbitArtist(
                song.artistsText.orEmpty()
            )

        return targetArtist.isNotBlank() &&
            songArtist.isNotBlank() &&
            (
                songArtist == targetArtist ||
                    songArtist.contains(targetArtist) ||
                    targetArtist.contains(songArtist)
            )
    }

    private suspend fun songsForRabbitArtist(
        artistName: String,
        historySongs: List<Song>,
        excludedIds: Set<String>
    ): List<Song> {

        val targetArtist =
            normalizeRabbitArtist(artistName)

        val databaseSongs =
            runCatching {
                db.songsForArtist(
                    artistName,
                    12
                ).first()
            }.getOrDefault(emptyList())

        val localSongs =
            (historySongs + databaseSongs)
                .filter { song ->
                    song.id !in excludedIds &&
                        rabbitArtistMatches(
                            song,
                            artistName
                        )
                }
                .distinctBy { song ->
                    val title =
                        song.title
                            .lowercase()
                            .replace(
                                Regex("[^a-z0-9]+"),
                                " "
                            )
                            .trim()

                    "${normalizeRabbitArtist(song.artistsText.orEmpty())}|$title"
                }
                .shuffled()
                .take(4)
                .map { song ->
                    song.copy(
                        artistsText =
                            cleanRabbitArtistName(
                                artistName
                            )
                    )
                }

        if (localSongs.size >= 4) {
            return localSongs
        }

        val searchQueries =
            listOf(
                "$artistName songs",
                artistName,
                "$artistName official audio"
            )

        val onlineSongs =
            mutableListOf<Song>()

        for (query in searchQueries) {
            if (onlineSongs.size >= 10) {
                break
            }

            val results =
                runCatching {
                    Innertube.searchPage(
                        query = query,
                        params =
                            Innertube.SearchFilter.Song.value,
                        fromMusicShelfRendererContent =
                            Innertube.SongItem.Companion::from
                    )
                        ?.getOrNull()
                        ?.items
                        .orEmpty()
                }.getOrDefault(emptyList())

            results.forEach { item ->
                val authorNames =
                    item.authors
                        ?.mapNotNull { it.name }
                        .orEmpty()

                val authorMatches =
                    authorNames.any { authorName ->
                        val normalizedAuthor =
                            normalizeRabbitArtist(
                                authorName
                            )

                        normalizedAuthor.isNotBlank() &&
                            (
                                normalizedAuthor ==
                                    targetArtist ||
                                    normalizedAuthor.contains(
                                        targetArtist
                                    ) ||
                                    targetArtist.contains(
                                        normalizedAuthor
                                    )
                            )
                    }

                if (!authorMatches) {
                    return@forEach
                }

                val mediaItem =
                    item.asMediaItem

                if (mediaItem.mediaId in excludedIds) {
                    return@forEach
                }

                val title =
                    mediaItem
                        .mediaMetadata
                        .title
                        ?.toString()
                        ?.trim()
                        .orEmpty()

                if (title.isBlank()) {
                    return@forEach
                }

                onlineSongs +=
                    Song(
                        id = mediaItem.mediaId,
                        title = title,
                        artistsText =
                            cleanRabbitArtistName(
                                artistName
                            ),
                        durationText =
                            item.durationText,
                        thumbnailUrl =
                            mediaItem
                                .mediaMetadata
                                .artworkUri
                                ?.toString()
                    )
            }
        }

        return (localSongs + onlineSongs)
            .distinctBy { song ->
                val normalizedTitle =
                    song.title
                        .lowercase()
                        .replace(
                            Regex("[^a-z0-9]+"),
                            " "
                        )
                        .trim()

                val normalizedArtist =
                    normalizeRabbitArtist(
                        song.artistsText.orEmpty()
                    )

                "$normalizedArtist|$normalizedTitle"
            }
            .take(4)
    }

    private fun spaceArtists(
        songs: List<Song>,
        preferredGap: Int = 3
    ): List<Song> {

        val remaining =
            songs
                .distinctBy { it.id }
                .shuffled()
                .toMutableList()

        val result =
            mutableListOf<Song>()

        while (remaining.isNotEmpty()) {
            val lastArtist =
                result
                    .lastOrNull()
                    ?.artistsText
                    ?.let(
                        ::normalizeRabbitArtist
                    )

            val recentArtists =
                result
                    .takeLast(preferredGap)
                    .mapNotNull { song ->
                        song.artistsText
                            ?.let(
                                ::normalizeRabbitArtist
                            )
                            ?.takeIf {
                                it.isNotBlank()
                            }
                    }
                    .toSet()

            val preferredIndex =
                remaining.indexOfFirst { song ->
                    val artist =
                        normalizeRabbitArtist(
                            song.artistsText.orEmpty()
                        )

                    artist.isNotBlank() &&
                        artist != lastArtist &&
                        artist !in recentArtists
                }

            val fallbackIndex =
                remaining.indexOfFirst { song ->
                    val artist =
                        normalizeRabbitArtist(
                            song.artistsText.orEmpty()
                        )

                    artist.isNotBlank() &&
                        artist != lastArtist
                }

            val chosenIndex =
                when {
                    preferredIndex >= 0 ->
                        preferredIndex

                    fallbackIndex >= 0 ->
                        fallbackIndex

                    else ->
                        0
                }

            result +=
                remaining.removeAt(
                    chosenIndex
                )
        }

        return result
    }

    private suspend fun buildArtistChain(seedArtist: String, maxHops: Int): List<String> {
        val coOccurrenceWindow = 20L * 60 * 1000
        val chain = mutableListOf(seedArtist)
        var current = seedArtist
        repeat(maxHops - 1) {
            val next = runCatching { db.artistsPlayedAfter(current, coOccurrenceWindow, 5).first() }.getOrNull().orEmpty()
                .firstOrNull { it.name !in chain && it.coOccurrenceCount >= 2 }
            if (next != null) {
                chain.add(next.name)
                current = next.name
            } else {
                return chain
            }
        }
        return chain
    }
}
