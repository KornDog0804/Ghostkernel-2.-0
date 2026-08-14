package com.github.soundpod.repository

import com.github.soundpod.BuildConfig
import com.github.soundpod.db
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

@Serializable
data class KornOsSongSnapshot(
    val artist: String,
    val title: String,
    @SerialName("played_at") val playedAt: String? = null,
    @SerialName("play_count") val playCount: Int = 0,
    @SerialName("completion_percent") val completionPercent: Int = 0
)

@Serializable
data class KornOsArtistSnapshot(
    val name: String,
    @SerialName("play_count") val playCount: Int
)

@Serializable
data class KornOsTopicSnapshot(
    val artist: String,
    val title: String,
    val reason: String,
    @SerialName("best_for") val bestFor: List<String>
)

@Serializable
data class KornOsGhostBrainSnapshot(
    val headline: String,
    val subtext: String,
    @SerialName("action_label") val actionLabel: String,
    @SerialName("seed_songs") val seedSongs: List<KornOsSongSnapshot>
)

@Serializable
data class KornOsBridgeSnapshot(
    @SerialName("generated_at") val generatedAt: String,
    @SerialName("ghost_brain") val ghostBrain: KornOsGhostBrainSnapshot?,
    @SerialName("recently_haunted") val recentlyHaunted: List<KornOsSongSnapshot>,
    @SerialName("top_artists") val topArtists: List<KornOsArtistSnapshot>,
    @SerialName("suggested_topics") val suggestedTopics: List<KornOsTopicSnapshot>
)

@Serializable
private data class KornOsSyncResponse(
    val success: Boolean = false,
    val unchanged: Boolean = false,
    @SerialName("commit_sha") val commitSha: String? = null,
    val error: String? = null
)

data class KornOsSyncResult(
    val success: Boolean,
    val message: String
)

class KornOsBridgeRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
    }

    fun isConfigured(): Boolean =
        BuildConfig.KORNOS_SYNC_URL.isNotBlank() && BuildConfig.KORNOS_SYNC_KEY.isNotBlank()

    suspend fun sync(card: DiscoveryCardData?): KornOsSyncResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext KornOsSyncResult(
                success = false,
                message = "KornOS bridge is not configured in this build."
            )
        }

        val recentSongs = runCatching { db.lastPlayed(12).first() }.getOrDefault(emptyList())
        val topArtists = runCatching { db.mostPlayedArtists(10).first() }.getOrDefault(emptyList())

        val recent = recentSongs.map { song ->
            KornOsSongSnapshot(
                artist = song.artistsText.orEmpty(),
                title = song.title
            )
        }.filter { it.artist.isNotBlank() && it.title.isNotBlank() }

        val artists = topArtists.map {
            KornOsArtistSnapshot(name = it.name, playCount = it.playCount)
        }

        val seedSongs = card?.seedSongs.orEmpty().map { song ->
            KornOsSongSnapshot(
                artist = song.artistsText.orEmpty(),
                title = song.title
            )
        }.filter { it.artist.isNotBlank() && it.title.isNotBlank() }

        val topics = seedSongs.take(6).map { song ->
            KornOsTopicSnapshot(
                artist = song.artist,
                title = song.title,
                reason = card?.subtext?.takeIf { it.isNotBlank() }
                    ?: "Suggested by Ghost Brain from Joey's listening history.",
                bestFor = listOf("whats_spinning_today", "joeys_ear", "crate_digs")
            )
        }

        val snapshot = KornOsBridgeSnapshot(
            generatedAt = Instant.now().toString(),
            ghostBrain = card?.let {
                KornOsGhostBrainSnapshot(
                    headline = it.headline,
                    subtext = it.subtext,
                    actionLabel = it.actionLabel,
                    seedSongs = seedSongs
                )
            },
            recentlyHaunted = recent,
            topArtists = artists,
            suggestedTopics = topics
        )

        if (snapshot.ghostBrain == null && recent.isEmpty() && artists.isEmpty()) {
            return@withContext KornOsSyncResult(false, "GhostKernel has no listening data to send yet.")
        }

        runCatching {
            val response: HttpResponse = client.post(BuildConfig.KORNOS_SYNC_URL) {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                header("X-GhostKernel-Key", BuildConfig.KORNOS_SYNC_KEY)
                setBody(snapshot)
            }
            val parsed: KornOsSyncResponse = response.body()
            if (!response.status.isSuccess() || !parsed.success) {
                throw IllegalStateException(parsed.error ?: "KornOS sync failed with ${response.status.value}.")
            }
            KornOsSyncResult(
                success = true,
                message = if (parsed.unchanged) "KornOS already had this snapshot." else "Sent to KornOS."
            )
        }.getOrElse { error ->
            KornOsSyncResult(false, error.message ?: "Unable to reach KornOS.")
        }
    }
}
