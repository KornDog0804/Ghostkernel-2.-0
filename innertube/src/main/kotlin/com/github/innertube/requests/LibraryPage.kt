package com.github.innertube.requests

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import com.github.innertube.Innertube
import com.github.innertube.models.BrowseResponse
import com.github.innertube.models.MusicTwoRowItemRenderer
import com.github.innertube.models.bodies.BrowseBody
import com.github.innertube.utils.runCatchingNonCancellable
import kotlinx.serialization.json.*

data class YouTubePlaylist(
    val id: String,
    val title: String,
    val thumbnail: String?
)

data class LibraryDebugInfo(
    val lines: List<String>,
    val verdict: String
)

var lastLibraryDebugInfo: LibraryDebugInfo? = null

private val libJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

private fun MusicTwoRowItemRenderer?.toPlaylist(): YouTubePlaylist? {
    val r = this ?: return null
    val title = r.title?.runs?.firstOrNull()?.text ?: return null
    val browseId = r.navigationEndpoint?.browseEndpoint?.browseId ?: return null
    if (!browseId.startsWith("VL")) return null
    val thumb = r.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url
    return YouTubePlaylist(id = browseId, title = title, thumbnail = thumb)
}

private fun countKey(el: JsonElement, key: String): Int {
    var n = 0
    fun walk(e: JsonElement) {
        when (e) {
            is JsonObject -> { if (e.containsKey(key)) n++; e.values.forEach { walk(it) } }
            is JsonArray -> e.forEach { walk(it) }
            else -> {}
        }
    }
    walk(el)
    return n
}

private fun findBrowseIds(el: JsonElement, results: MutableList<String>) {
    when (el) {
        is JsonObject -> {
            el["browseId"]?.let { v ->
                if (v is JsonPrimitive && v.content.isNotEmpty()) results.add(v.content)
            }
            el.values.forEach { findBrowseIds(it, results) }
        }
        is JsonArray -> el.forEach { findBrowseIds(it, results) }
        else -> {}
    }
}

suspend fun Innertube.libraryPage(): Result<List<YouTubePlaylist>>? = runCatchingNonCancellable {
    val items = mutableListOf<YouTubePlaylist>()
    val debug = mutableListOf<String>()
    var verdict = ""

    // Auth state
    debug.add("=== AUTH STATE ===")
    debug.add("isLoggedIn=$isLoggedIn")
    debug.add("cookieLen=${cookies?.length ?: 0}")
    debug.add("hasSAPISID=${cookies?.contains("SAPISID") == true || cookies?.contains("__Secure-3PAPISID") == true}")
    debug.add("clientName=$clientName")
    debug.add("clientVersion=$clientVersion")
    debug.add("visitorData=${visitorData?.take(20)}")
    debug.add("apiKey=${apiKey?.take(10)}")

    for (browseId in listOf("FEmusic_liked_playlists", "FEmusic_library_landing")) {
        runCatching {
            debug.add("=== REQUEST: $browseId ===")

            val statement = client.post(BROWSE) {
                attributes.put(Innertube.Attributes.UseCookies, true)
                // Manually inject auth headers because defaultRequest runs before
                // individual request attributes are set, so UseCookies check fails
                cookies?.let { cookieString ->
                    header("Cookie", cookieString)
                    header("X-Goog-AuthUser", "0")
                    visitorData?.let { header("X-Goog-Visitor-Id", it) }
                    header("Origin", "https://music.youtube.com")
                    header("Referer", "https://music.youtube.com/")
                    header("X-Origin", "https://music.youtube.com")
                    val sapisid = cookieString.split("; ")
                        .find { it.startsWith("SAPISID=") }
                        ?.substringAfter("SAPISID=")
                    if (sapisid != null) {
                        val ts = System.currentTimeMillis() / 1000
                        val payload = "$ts $sapisid https://music.youtube.com"
                        val digest = java.security.MessageDigest.getInstance("SHA-1")
                            .digest(payload.toByteArray())
                        val hash = digest.joinToString("") { "%02x".format(it) }
                        header("Authorization", "SAPISIDHASH ${ts}_${hash}")
                    }
                }
                setBody(BrowseBody(localized = false, browseId = browseId))
            }

            // Log request headers
            debug.add("--- REQUEST HEADERS ---")
            statement.request.headers.entries().forEach { (k, v) ->
                val safe = if (k.lowercase() == "cookie") "len=${v.joinToString().length}" else v.joinToString().take(80)
                debug.add("$k: $safe")
            }

            val text = statement.bodyAsText()
            debug.add("--- RESPONSE len=${text.length} ---")

            // Top level keys
            val jsonEl = libJson.parseToJsonElement(text)
            if (jsonEl is JsonObject) {
                debug.add("TOP KEYS: ${jsonEl.keys.joinToString()}")

                // Check for error object
                jsonEl["error"]?.let { err ->
                    debug.add("ERROR IN RESPONSE: $err")
                }

                // Check alerts
                jsonEl["alerts"]?.let { alerts ->
                    debug.add("ALERTS: ${alerts.toString().take(200)}")
                }

                // Check onResponseReceivedActions
                jsonEl["onResponseReceivedActions"]?.let {
                    debug.add("HAS onResponseReceivedActions: yes")
                }

                // Contents structure
                val contents = jsonEl["contents"]
                if (contents == null) {
                    debug.add("CONTENTS: MISSING - full response first 500 chars:")
                    debug.add(text.take(500))
                } else {
                    if (contents is JsonObject) {
                        debug.add("CONTENTS KEYS: ${contents.keys.joinToString()}")
                        contents["singleColumnBrowseResultsRenderer"]?.let { scbr ->
                            if (scbr is JsonObject) {
                                val tabs = scbr["tabs"]
                                if (tabs is JsonArray) {
                                    debug.add("TABS COUNT: ${tabs.size}")
                                    tabs.forEachIndexed { i, tab ->
                                        if (tab is JsonObject) {
                                            val tr = tab["tabRenderer"]
                                            if (tr is JsonObject) {
                                                debug.add("tab[$i] title=${(tr["title"] as? JsonPrimitive)?.content}")
                                                val content = tr["content"]
                                                if (content is JsonObject) {
                                                    debug.add("tab[$i] content keys: ${content.keys.joinToString()}")
                                                    val slr = content["sectionListRenderer"]
                                                    if (slr is JsonObject) {
                                                        val slrContents = slr["contents"]
                                                        if (slrContents is JsonArray) {
                                                            debug.add("tab[$i] sectionList.contents count=${slrContents.size}")
                                                            slrContents.forEachIndexed { ci, c ->
                                                                if (c is JsonObject) {
                                                                    debug.add("  content[$ci] keys: ${c.keys.joinToString()}")
                                                                    c["itemSectionRenderer"]?.let { isr ->
                                                                        if (isr is JsonObject) {
                                                                            val isrC = isr["contents"]
                                                                            if (isrC is JsonArray) {
                                                                                debug.add("    isr.contents count=${isrC.size}")
                                                                                isrC.forEachIndexed { ii, ic ->
                                                                                    if (ic is JsonObject) {
                                                                                        debug.add("    isr[$ii] keys: ${ic.keys.joinToString()}")
                                                                                                ic["messageRenderer"]?.let { mr ->
                                                                                                    if (mr is JsonObject) {
                                                                                                        val runs = (mr["text"] as? JsonObject)?.get("runs")
                                                                                                        if (runs is JsonArray) {
                                                                                                            val msg = runs.joinToString("") { r -> if (r is JsonObject) (r["text"] as? JsonPrimitive)?.content ?: "" else "" }
                                                                                                            debug.add("    MSG: $msg")
                                                                                                        }
                                                                                                    }
                                                                                                }
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Deep scan
            val allBrowseIds = mutableListOf<String>()
            findBrowseIds(jsonEl, allBrowseIds)
            val vlIds = allBrowseIds.filter { it.startsWith("VL") }
            val plIds = allBrowseIds.filter { it.startsWith("PL") }
            debug.add("browseIds total=${allBrowseIds.size} VL=${vlIds.size} PL=${plIds.size}")
            vlIds.take(10).forEach { debug.add("VL: $it") }

            val gridCount = countKey(jsonEl, "gridRenderer")
            val twoRowCount = countKey(jsonEl, "musicTwoRowItemRenderer")
            debug.add("grid=$gridCount twoRow=$twoRowCount")

            // Typed parse
            val response = libJson.decodeFromString<BrowseResponse>(text)
            val tabs = response.contents?.singleColumnBrowseResultsRenderer?.tabs
                ?: response.contents?.twoColumnBrowseResultsRenderer?.tabs

            tabs?.forEachIndexed { ti, tab ->
                tab.tabRenderer?.content?.sectionListRenderer?.contents?.forEachIndexed { ci, content ->
                    content.itemSectionRenderer?.contents?.forEachIndexed { ii, item ->
                        item.gridRenderer?.items?.forEach { gi ->
                            gi.musicTwoRowItemRenderer.toPlaylist()?.let {
                                if (items.none { p -> p.id == it.id }) items.add(it)
                            }
                        }
                    }
                    content.gridRenderer?.items?.forEach { gi ->
                        gi.musicTwoRowItemRenderer.toPlaylist()?.let {
                            if (items.none { p -> p.id == it.id }) items.add(it)
                        }
                    }
                }
            }

            verdict = when {
                items.isNotEmpty() -> "Found ${items.size} playlists"
                vlIds.isNotEmpty() -> "Typed parser missed playlists. Model path incomplete."
                else -> "No VL browseIds. Auth issue or wrong scope."
            }
            debug.add("VERDICT: $verdict")

        }.onFailure {
            debug.add("EXCEPTION $browseId: ${it.message}")
            verdict = "Exception: ${it.message}"
        }
    }

    lastLibraryDebugInfo = LibraryDebugInfo(lines = debug, verdict = verdict)
    items
}
