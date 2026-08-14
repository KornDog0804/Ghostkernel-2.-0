package com.github.soundpod

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import java.io.File
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.request.crossfade
import com.github.innertube.Innertube
import com.github.soundpod.extractor.NewPipeDownloader
import com.github.soundpod.enums.CoilDiskCacheMaxSize
import com.github.soundpod.utils.coilDiskCacheMaxSizeKey
import com.github.soundpod.utils.getEnum
import com.github.soundpod.utils.preferences
import com.github.soundpod.service.YouTubeBootstrap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale


class MainApplication : Application(), SingletonImageLoader.Factory {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        instance = this

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("GhostKernel-FATAL", "Uncaught on ${thread.name}: ${throwable.message}", throwable)
            try {
                val versionInfo = try {
                    val pInfo = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
                    "GhostKernel ${pInfo.versionName} (build ${pInfo.longVersionCode})"
                } catch (_: Exception) {
                    "version unknown"
                }
                val deviceInfo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, " +
                    "Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})"

                crashLogFile(applicationContext).appendText(
                    "\n=== ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())} ===\n" +
                        "App: $versionInfo\n" +
                        "Device: $deviceInfo\n" +
                        "Thread: ${thread.name}\n" +
                        "${Log.getStackTraceString(throwable)}\n"
                )
            } catch (_: Exception) {
                // Don't let the crash handler itself crash
            }
            Runtime.getRuntime().exit(1)
        }

        Locale.setDefault(Locale.US)
        NewPipeDownloader.init(cacheDir)

        Thread {
            DatabaseInitializer.get(this)

            Innertube.visitorData = preferences.getString("visitor_data", null)
            Innertube.onVisitorDataChanged = { visitorData: String? ->
                preferences.edit { putString("visitor_data", visitorData) }
            }

            Innertube.cookies = preferences.getString("cookies", null)
            Innertube.onCookiesChanged = { cookies: String? ->
                preferences.edit { putString("cookies", cookies) }
            }
            
            // Trigger dynamic session bootstrap
            applicationScope.launch {
                YouTubeBootstrap.initialize()
            }
        }.start()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true)
            .diskCache(
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil"))
                    .maxSizeBytes(
                        preferences.getEnum(
                            coilDiskCacheMaxSizeKey,
                            CoilDiskCacheMaxSize.`128MB`
                        ).bytes
                    )
                    .build()
            )
            .build()
    }

    companion object {
        private var instance: MainApplication? = null
        val appContext: Context get() = instance!!.applicationContext

        fun crashLogFile(context: Context): File = File(context.filesDir, "crash_log.txt")
    }
}