package com.github.soundpod.noutube

import android.content.Context
import expo.modules.noutubeview.NouYtDlp

object NouTubeBridge {

    fun listFormats(
        context: Context,
        url: String
    ): Result<Map<String, Any>> {
        return runCatching {
            NouYtDlp(context.applicationContext).listFormats(url)
        }
    }

    fun downloadAudio(
        context: Context,
        url: String,
        onProgress: (progress: Float, etaInSeconds: Long, line: String?) -> Unit
    ): Result<NouYtDlp.DownloadResult> {
        return runCatching {
            NouYtDlp(context.applicationContext).downloadVideo(
                url = url,
                formatId = "bestaudio[ext=m4a]/bestaudio/best",
                outputDir = "",
                onProgress = onProgress
            )
        }
    }
}
