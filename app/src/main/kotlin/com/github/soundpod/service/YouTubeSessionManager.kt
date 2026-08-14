package com.github.soundpod.service

import android.util.Log
import com.github.innertube.Innertube
import com.github.soundpod.MainApplication
import com.github.soundpod.utils.preferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object YouTubeSessionManager {
    private val _isSessionReady = MutableStateFlow(false)
    val isSessionReady = _isSessionReady.asStateFlow()

    private val _isBootstrapped = MutableStateFlow(false)
    val isBootstrapped = _isBootstrapped.asStateFlow()

    private val _needsConsent = MutableStateFlow(false)
    val needsConsent = _needsConsent.asStateFlow()

    fun setNeedsConsent(value: Boolean) {
        _needsConsent.value = value
    }

    fun updateSession(
        visitorData: String? = null,
        poToken: String? = null,
        apiKey: String? = null,
        clientName: String? = null,
        clientVersion: String? = null,
        context: com.github.innertube.models.Context? = null,
        jsUrl: String? = null,
        cookies: String? = null,
        decipher: (suspend (String) -> String)? = null,
        signatureDecipher: (suspend (String) -> String)? = null,
        isFromBootstrap: Boolean = false
    ) {
        val prefs = MainApplication.appContext.preferences
        
        val hasAuthenticatedSession = Innertube.cookies?.let {
            it.contains("SAPISID") || it.contains("__Secure-3PAPISID")
        } == true

        visitorData?.let {
            // Don't let an anonymous bootstrap fetch clobber the visitorData
            // that belongs to an already-authenticated session - mismatched
            // cookies + visitorData causes YouTube to reject the DYNAMIC
            // client as UNPLAYABLE and silently fall back to lower-quality
            // unauthenticated clients (ANDROID_VR).
            if (isFromBootstrap && hasAuthenticatedSession) {
                Log.d("GhostKernel", "Skipping bootstrap visitorData overwrite - authenticated session already present")
            } else {
                Innertube.visitorData = it
            }
        }
        poToken?.let { Innertube.poToken = it }
        apiKey?.let { Innertube.apiKey = it }
        clientName?.let { Innertube.clientName = it }
        clientVersion?.let { Innertube.clientVersion = it }
        context?.let { Innertube.context = it }
        
        jsUrl?.let {
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
            scope.launch { YouTubeDecipherer.initialize(it) }
        }
        
        cookies?.let { 
            Innertube.cookies = it
            // Also store in preferences for persistence
            MainApplication.appContext.preferences.edit { putString("cookies", it) }
        }
        
        decipher?.let { Innertube.decipher = it }
        signatureDecipher?.let { Innertube.signatureDecipher = it }

        // Build Innertube.context so DYNAMIC client is used in player requests
        val currentClientName = Innertube.clientName ?: clientName ?: "WEB_REMIX"
        val currentClientVersion = Innertube.clientVersion ?: clientVersion ?: "1.20240214.01.00"
        val currentVisitorData = Innertube.visitorData ?: visitorData
        Innertube.context = com.github.innertube.models.Context(
            client = com.github.innertube.models.Context.Client(
                clientName = currentClientName,
                clientVersion = currentClientVersion,
                clientId = "67",
                platform = "DESKTOP",
                userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
                gl = "US",
                hl = "en",
                visitorData = currentVisitorData
            )
        )
        
        if (Innertube.visitorData != null) {
            _isSessionReady.value = true
        }

        if (isFromBootstrap && Innertube.visitorData != null && Innertube.apiKey != null) {
            _isBootstrapped.value = true
        }
    }
}
