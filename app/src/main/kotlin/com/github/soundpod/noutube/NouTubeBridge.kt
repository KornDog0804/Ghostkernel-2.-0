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
}
