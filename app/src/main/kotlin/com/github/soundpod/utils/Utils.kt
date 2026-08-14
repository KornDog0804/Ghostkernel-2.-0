package com.github.soundpod.utils

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.github.innertube.Innertube
import com.github.innertube.requests.playlistPageContinuation
import com.github.innertube.utils.plus
import com.github.soundpod.models.Song

val Innertube.SongItem.asMediaItem: MediaItem
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    get() = MediaItem.Builder()
        .setMediaId(key)
        .setUri(key)
        .setCustomCacheKey(key)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(info?.name)
                .setArtist(authors?.mapNotNull { it.name }?.joinToString(" • ")?.trim()?.removeSuffix("-")?.removeSuffix(" -")?.trim())
                .setAlbumTitle(album?.name)
                .setArtworkUri(thumbnail?.url?.toUri())
                .setExtras(
                    Bundle().apply {
                        album?.endpoint?.browseId?.let { putString("albumId", it) }
                        durationText?.let { putString("durationText", it) }

                        val names = authors?.filter { it.endpoint != null }?.mapNotNull { it.name }
                        if (!names.isNullOrEmpty()) putStringArrayList("artistNames", ArrayList(names))

                        val ids = authors?.mapNotNull { it.endpoint?.browseId }
                        if (!ids.isNullOrEmpty()) putStringArrayList("artistIds", ArrayList(ids))
                    }
                )
                .build()
        )
        .build()

val Innertube.VideoItem.asMediaItem: MediaItem
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    get() = MediaItem.Builder()
        .setMediaId(key)
        .setUri(key)
        .setCustomCacheKey(key)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(info?.name)
                .setArtist(authors?.mapNotNull { it.name }?.joinToString(" • ")?.trim()?.removeSuffix("-")?.removeSuffix(" -")?.trim())
                .setArtworkUri(thumbnail?.url?.toUri())
                .setExtras(
                    Bundle().apply {
                        durationText?.let { putString("durationText", it) }

                        if (isOfficialMusicVideo) {
                            val names = authors?.filter { it.endpoint != null }?.mapNotNull { it.name }
                            if (!names.isNullOrEmpty()) putStringArrayList("artistNames", ArrayList(names))

                            val ids = authors?.mapNotNull { it.endpoint?.browseId }
                            if (!ids.isNullOrEmpty()) putStringArrayList("artistIds", ArrayList(ids))
                        }
                    }
                )
                .build()
        )
        .build()

val Song.asMediaItem: MediaItem
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    get() = MediaItem.Builder()
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artistsText)
                .setArtworkUri(thumbnailUrl?.toUri())
                .setExtras(
                    Bundle().apply {
                        durationText?.let { putString("durationText", it) }
                    }
                )
                .build()
        )
        .setMediaId(id)
        .setUri(id)
        .setCustomCacheKey(id)
        .build()

fun String?.thumbnail(size: Int): String? {
    if (this == null) return null

    if (this.contains("i.ytimg.com")) {
        val quality = when {
            size <= 120 -> "default.jpg"
            size <= 320 -> "mqdefault.jpg"
            size <= 480 -> "hqdefault.jpg"
            size <= 640 -> "sddefault.jpg"
            else -> "maxresdefault.jpg"
        }
        return this.replace(Regex("(default|mqdefault|hqdefault|sddefault|maxresdefault|hq720)\\.jpg"), quality)
    }

    if (this.contains("googleusercontent.com") || this.contains("ggpht.com")) {
        val cleanUrl = this.substringBefore("=")
        return "$cleanUrl=w$size-h$size-p-l100-rj"
    }

    return this
}

fun Uri?.thumbnail(size: Int): Uri? {
    return this?.toString()?.thumbnail(size)?.toUri()
}

fun formatAsDuration(millis: Long) = DateUtils.formatElapsedTime(millis / 1000).removePrefix("0")

val paginationDebugLog = mutableListOf<String>()

private fun logPagination(message: String) {
    android.util.Log.d("GhostKernel-Pagination", message)
    paginationDebugLog.add(message)
    try {
        com.github.soundpod.MainApplication.crashLogFile(com.github.soundpod.appContext).appendText(
            "[PAGINATION] $message\n"
        )
    } catch (_: Exception) {
        // Don't let logging itself crash anything
    }
}

suspend fun Result<Innertube.PlaylistOrAlbumPage>.completed(): Result<Innertube.PlaylistOrAlbumPage>? {
    paginationDebugLog.clear()
    var playlistPage = getOrNull() ?: return null
    var round = 0

    logPagination("=== ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())} - Initial page: ${playlistPage.songsPage?.items?.size} items, continuation=${playlistPage.songsPage?.continuation?.take(30)}")

    while (playlistPage.songsPage?.continuation != null) {
        round++
        val continuation = playlistPage.songsPage?.continuation!!
        val otherPlaylistPageResult =
            Innertube.playlistPageContinuation(continuation = continuation)

        if (otherPlaylistPageResult == null) {
            logPagination("Round $round: continuation call returned null - stopping")
            break
        }

        if (otherPlaylistPageResult.isFailure) {
            logPagination("Round $round: FAILED - ${otherPlaylistPageResult.exceptionOrNull()?.message}")
            break
        }

        otherPlaylistPageResult.getOrNull()?.let { otherSongsPage ->
            logPagination("Round $round: got ${otherSongsPage.items?.size} more items, new continuation=${otherSongsPage.continuation?.take(30)}")
            playlistPage = playlistPage.copy(songsPage = playlistPage.songsPage + otherSongsPage)
        }
    }

    logPagination("Final total: ${playlistPage.songsPage?.items?.size} items after $round rounds")

    return Result.success(playlistPage)
}

// Removed `isAtLeastAndroid6` entirely since your minSdkVersion is >= 23

inline val isAtLeastAndroid8
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

inline val isAtLeastAndroid12
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

inline val isAtLeastAndroid13
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU