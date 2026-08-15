package com.github.soundpod.noutube

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import expo.modules.noutubeview.NouYtDlp

object NouTubeBridge {

    private const val TAG = "GhostKernel-NouTube"
    private const val PREFS_NAME = "preferences"
    private const val LAST_YTDLP_UPDATE_KEY = "noutube_last_ytdlp_update"

    private const val TWO_WEEKS_MS =
        14L * 24L * 60L * 60L * 1000L

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
            NouYtDlp(
                context.applicationContext
            ).downloadVideo(
                url = url,
                formatId =
                    "bestaudio[ext=m4a]/bestaudio/best",
                outputDir = "",
                onProgress = onProgress
            )
        }
    }
}
