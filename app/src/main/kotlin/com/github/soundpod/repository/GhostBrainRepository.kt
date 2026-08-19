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

        if (learnedArtists.isNotEmpty()) {
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
        val recentArtists = runCatching { db.mostPlayedArtistsSince(weekCutoff, 3).first() }.getOrNull().orEmpty()
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

            // Rabbit hole - real multi-hop chain off this week's top artist
            val chain = buildArtistChain(artist.name, maxHops = 4)
            if (chain.size >= 2) {
                val chainSongs = chain.flatMap { artistName ->
                    mostPlayed.filter { it.artistsText == artistName }.take(3)
                }
                if (chainSongs.isNotEmpty()) {
                    val journey = chain.drop(1).joinToString(", ")
                    priorityCandidates += DiscoveryCardData(
                        headline = "Every time you play ${chain.first()}, ${chain[1]} follows",
                        source = "ghost_rabbit_hole",
                        subtext = "You started with ${chain.first()}. Ghost Brain followed the pattern through $journey.",
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
            val comfortSongs = runCatching { db.highestCompletionSongs(5).first() }.getOrNull().orEmpty()
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
