package com.github.soundpod.viewmodels.home

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.innertube.Innertube
import com.github.innertube.requests.youtubeHomePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class YouTubeHomeViewModel : ViewModel() {

    var homePageResult: Result<Innertube.YouTubeHomePage?>? by mutableStateOf(null)
        private set

    private var loadJob: Job? = null

    fun load(forceRefresh: Boolean = false) {
        if (!forceRefresh && homePageResult?.getOrNull() != null) {
            return
        }

        loadJob?.cancel()

        loadJob = viewModelScope.launch(Dispatchers.IO) {
            Log.d("GhostKernel", "GhostKernel Home: loading personalized home")

            val result = try {
                Innertube.youtubeHomePage()
                    ?: Result.failure(
                        IllegalStateException(
                            "YouTube Home request did not return a result"
                        )
                    )
            } catch (e: Throwable) {
                Result.failure(e)
            }

            val page = result.getOrNull()

            if (page != null) {
                Log.d(
                    "GhostKernel",
                    "GhostKernel Home: loaded ${page.sections.size} personalized shelves"
                )

                page.sections.forEach { section ->
                    Log.d(
                        "GhostKernel",
                        "GhostKernel Home shelf: ${section.title} (${section.items.size} items)"
                    )
                }
            } else {
                Log.e(
                    "GhostKernel",
                    "GhostKernel Home: personalized home failed",
                    result.exceptionOrNull()
                )
            }

            withContext(Dispatchers.Main) {
                homePageResult = result
            }
        }
    }

    fun refresh() {
        homePageResult = null
        load(forceRefresh = true)
    }
}
