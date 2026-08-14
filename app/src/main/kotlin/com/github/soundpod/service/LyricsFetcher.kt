package com.github.soundpod.service

import android.database.sqlite.SQLiteConstraintException
import com.github.soundpod.models.Song
import com.github.innertube.Innertube
import com.github.innertube.requests.lyrics
import com.github.soundpod.db
import com.github.soundpod.models.Lyrics
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

object LyricsFetcher {

    @Serializable
    private data class LrcLibResult(
        val plainLyrics: String? = null,
        val syncedLyrics: String? = null,
        val instrumental: Boolean = false
    )

    private val lrcLibJson = Json { ignoreUnknownKeys = true }

    private val lrcLibClient by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 8000
            }
        }
    }

    suspend fun fetchLyrics(
        mediaId: String
    ): Boolean = withContext(Dispatchers.IO) {

        // Try YouTube's own lyrics first
        var fixedLyrics: String? = null
        Innertube.lyrics(videoId = mediaId)?.onSuccess { fixedLyrics = it }

        if (!fixedLyrics.isNullOrBlank()) {
            println("GhostKernel-Lyrics: found via Innertube for $mediaId")
            return@withContext saveLyrics(mediaId, fixedLyrics, null)
        }

        // Fallback: LRCLIB, keyed on title/artist since YouTube had nothing
        println("GhostKernel-Lyrics: Innertube empty for $mediaId, trying LRCLIB fallback")
        val song = runCatching { db.song(mediaId).first() }.getOrNull()
        val title = song?.title
        val artist = song?.artistsText

        if (title.isNullOrBlank()) {
            println("GhostKernel-Lyrics: no song metadata for $mediaId, cannot try LRCLIB")
            return@withContext false
        }

        val lrcLibResult = runCatching {
            val responseText = lrcLibClient.get("https://lrclib.net/api/search") {
                parameter("track_name", title)
                if (!artist.isNullOrBlank()) parameter("artist_name", artist)
            }.bodyAsText()
            lrcLibJson.decodeFromString<List<LrcLibResult>>(responseText)
        }.getOrElse { e ->
            println("GhostKernel-Lyrics: LRCLIB request failed for $mediaId: ${e.message}")
            emptyList()
        }

        val match = lrcLibResult.firstOrNull {
            !it.instrumental && (!it.plainLyrics.isNullOrBlank() || !it.syncedLyrics.isNullOrBlank())
        }

        if (match == null) {
            println("GhostKernel-Lyrics: LRCLIB had no usable match for $mediaId ($title)")
            return@withContext false
        }

        println("GhostKernel-Lyrics: found via LRCLIB for $mediaId (synced=${!match.syncedLyrics.isNullOrBlank()})")
        return@withContext saveLyrics(mediaId, match.plainLyrics, match.syncedLyrics)
    }

    private suspend fun saveLyrics(mediaId: String, fixed: String?, synced: String?): Boolean {
        db.insert(
            Song(
                id = mediaId,
                title = mediaId,
                artistsText = null,
                durationText = null,
                thumbnailUrl = null
            )
        )
        val maxAttempts = 5
        repeat(maxAttempts) { attempt ->
            try {
                db.upsert(
                    Lyrics(
                        songId = mediaId,
                        fixed = fixed,
                        synced = synced
                    )
                )
                if (attempt > 0) {
                    println("GhostKernel-Lyrics: SAVE SUCCEEDED for $mediaId on retry attempt ${attempt + 1}")
                }
                return true
            } catch (e: SQLiteConstraintException) {
                println("GhostKernel-Lyrics: SAVE FAILED for $mediaId (attempt ${attempt + 1}/$maxAttempts) - ${e.message}")
                if (attempt < maxAttempts - 1) {
                    delay(300)
                }
            }
        }
        println("GhostKernel-Lyrics: SAVE PERMANENTLY FAILED for $mediaId after $maxAttempts attempts")
        return false
    }
}
