package com.github.soundpod.noutube

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.github.soundpod.MainApplication
import expo.modules.noutubeview.NouYtDlp

object NouTubeBridge {

    private const val TAG = "GhostKernel-NouTube"
    private const val PREFS_NAME = "preferences"
    private const val LAST_YTDLP_UPDATE_KEY = "noutube_last_ytdlp_update"

    private const val TWO_WEEKS_MS =
        14L * 24L * 60L * 60L * 1000L

    private fun recordDownloadFailure(
        context: Context,
        operation: String,
        url: String,
        error: Throwable
    ) {
        Log.e(
            TAG,
            "$operation failed: ${error.message}",
            error
        )

        runCatching {
            val appContext = context.applicationContext
            val causeChain = generateSequence(error as Throwable?) { it.cause }
                .joinToString(" -> ") { throwable ->
                    "${throwable.javaClass.name}: ${throwable.message}"
                }

            MainApplication
                .crashLogFile(appContext)
                .appendText(
                    buildString {
                        append("\n=== NouTube Download Failure ===\n")
                        append("Operation: $operation\n")
                        append("Package: ${appContext.packageName}\n")
                        append("Cache: ${appContext.cacheDir.absolutePath}\n")
                        append("URL: $url\n")
                        append("Cause chain: $causeChain\n")
                        append("Stack trace:\n")
                        append(error.stackTraceToString())
                        append("\n=== End NouTube Failure ===\n")
                    }
                )
        }
    }

    fun initialize(
        context: Context
    ): Result<Unit> {
        return runCatching {
            NouYtDlp(context.applicationContext).ensureInitialized()

            Log.i(
                TAG,
                "NouTube yt-dlp and FFmpeg initialized"
            )
        }
    }

    fun updateYtDlp(
        context: Context
    ): Result<Unit> {
        return runCatching {
            val appContext = context.applicationContext
            val engine = NouYtDlp(appContext)

            engine.ensureInitialized()

            Log.i(TAG, "Updating yt-dlp")

            engine.update()

            Log.i(TAG, "yt-dlp update completed")
        }
    }

    fun initializeAndUpdateIfNeeded(
        context: Context
    ): Result<Boolean> {
        return runCatching {
            val appContext = context.applicationContext
            val engine = NouYtDlp(appContext)

            /*
             * NouTube initializes its native download engine when
             * the native module starts.
             */
            engine.ensureInitialized()

            val prefs =
                appContext.getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )

            val now = System.currentTimeMillis()
            val lastUpdate =
                prefs.getLong(
                    LAST_YTDLP_UPDATE_KEY,
                    0L
                )

            val updateNeeded =
                now - lastUpdate > TWO_WEEKS_MS

            if (!updateNeeded) {
                Log.i(
                    TAG,
                    "yt-dlp update not needed"
                )

                return@runCatching false
            }

            Log.i(
                TAG,
                "NouTube yt-dlp update due"
            )

            /*
             * Match the working NouTube behavior:
             * timestamp is written ONLY after update succeeds.
             */
            engine.update()

            prefs.edit {
                putLong(
                    LAST_YTDLP_UPDATE_KEY,
                    now
                )
            }

            Log.i(
                TAG,
                "NouTube yt-dlp updated successfully"
            )

            true
        }
    }

    fun listFormats(
        context: Context,
        url: String
    ): Result<Map<String, Any>> {
        return runCatching {
            NouYtDlp(
                context.applicationContext
            ).listFormats(url)
        }
    }

    fun downloadAlbum(
        context: Context,
        url: String,
        onProgress: (
            progress: Float,
            etaInSeconds: Long,
            line: String?
        ) -> Unit
    ): Result<NouYtDlp.DownloadResult> {
        return runCatching {
            val appContext = context.applicationContext

            /*
             * Production safety:
             * A fresh GhostKernel install has its own yt-dlp runtime/data.
             * Make sure that runtime is initialized AND updated before the
             * first album download is allowed to start.
             */
            initializeAndUpdateIfNeeded(appContext).getOrThrow()

            NouYtDlp(
                appContext
            ).downloadVideo(
                url = url,
                formatId = "playlist",
                outputDir = "",
                onProgress = onProgress
            )
        }.onFailure { error ->
            recordDownloadFailure(
                context = context,
                operation = "Album download",
                url = url,
                error = error
            )
        }
    }

    fun downloadAudio(
        context: Context,
        url: String,
        onProgress: (
            progress: Float,
            etaInSeconds: Long,
            line: String?
        ) -> Unit
    ): Result<NouYtDlp.DownloadResult> {
        return runCatching {
            val appContext = context.applicationContext

            /*
             * Production safety:
             * Do not assume the bundled yt-dlp runtime is current.
             * On a fresh official install, update it before downloading.
             */
            initializeAndUpdateIfNeeded(appContext).getOrThrow()

            NouYtDlp(
                appContext
            ).downloadVideo(
                url = url,
                formatId =
                    "bestaudio[ext=m4a]/bestaudio/best",
                outputDir = "",
                onProgress = onProgress
            )
        }.onFailure { error ->
            recordDownloadFailure(
                context = context,
                operation = "Song download",
                url = url,
                error = error
            )
        }
    }
}
