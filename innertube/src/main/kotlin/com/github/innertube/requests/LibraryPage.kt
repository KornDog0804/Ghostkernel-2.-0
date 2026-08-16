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
import com.github.innertube.models.bodies.ContinuationBody
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


private fun libraryText(element: JsonElement?): String? {
    val obj = element as? JsonObject ?: return null

    val simpleText =
        (obj["simpleText"] as? JsonPrimitive)?.content

    if (!simpleText.isNullOrBlank()) {
        return simpleText
    }

    val runs = obj["runs"] as? JsonArray

    val runText =
        runs
            ?.mapNotNull { run ->
                (run as? JsonObject)
                    ?.get("text")
                    ?.let { it as? JsonPrimitive }
                    ?.content
            }
            ?.joinToString("")
            ?.trim()

    return runText?.takeIf { it.isNotBlank() }
}

private fun libraryThumbnail(
    element: JsonElement?
): String? {
    return when (element) {

        is JsonObject -> {

            val directUrl =
                (element["url"] as? JsonPrimitive)
                    ?.content

            if (!directUrl.isNullOrBlank()) {
                directUrl
            } else {
                element.values
                    .firstNotNullOfOrNull {
                        libraryThumbnail(it)
                    }
            }
        }

        is JsonArray ->
            element.firstNotNullOfOrNull {
                libraryThumbnail(it)
            }

        else -> null
    }
}

private fun playlistBrowseId(
    obj: JsonObject
): String? {

    val directEndpoint =
        obj["navigationEndpoint"]
            as? JsonObject

    val directBrowse =
        directEndpoint
            ?.get("browseEndpoint")
            as? JsonObject

    val directId =
        (directBrowse?.get("browseId")
            as? JsonPrimitive)
            ?.content

    if (
        directId != null &&
        directId.startsWith("VL")
    ) {
        return directId
    }

    val title =
        obj["title"]
            as? JsonObject

    val runs =
        title?.get("runs")
            as? JsonArray

    runs?.forEach { run ->

        val runObject =
            run as? JsonObject
                ?: return@forEach

        val endpoint =
            runObject["navigationEndpoint"]
                as? JsonObject

        val browseEndpoint =
            endpoint?.get("browseEndpoint")
                as? JsonObject

        val browseId =
            (browseEndpoint?.get("browseId")
                as? JsonPrimitive)
                ?.content

        if (
            browseId != null &&
            browseId.startsWith("VL")
        ) {
            return browseId
        }
    }

    return null
}

private fun rawPlaylist(
    obj: JsonObject
): YouTubePlaylist? {

    val browseId =
        playlistBrowseId(obj)
            ?: return null

    val title =
        libraryText(obj["title"])
            ?: libraryText(obj["headline"])
            ?: libraryText(obj["primaryText"])
            ?: return null

    val thumbnail =
        libraryThumbnail(
            obj["thumbnailRenderer"]
        )
            ?: libraryThumbnail(
                obj["thumbnail"]
            )
            ?: libraryThumbnail(
                obj["thumbnails"]
            )

    return YouTubePlaylist(
        id = browseId,
        title = title,
        thumbnail = thumbnail
    )
}

private fun collectRawPlaylists(
    element: JsonElement,
    results: MutableList<YouTubePlaylist>
) {

    when (element) {

        is JsonObject -> {

            rawPlaylist(element)
                ?.let { playlist ->

                    if (
                        results.none {
                            it.id == playlist.id
                        }
                    ) {
                        results.add(playlist)
                    }
                }

            element.values.forEach {
                collectRawPlaylists(
                    it,
                    results
                )
            }
        }

        is JsonArray ->
            element.forEach {
                collectRawPlaylists(
                    it,
                    results
                )
            }

        else -> Unit
    }
}

private fun collectLibraryContinuationTokens(
    element: JsonElement,
    results: MutableSet<String>
) {

    when (element) {

        is JsonObject -> {

            val nextContinuationData =
                element["nextContinuationData"]
                    as? JsonObject

            val continuation =
                (nextContinuationData
                    ?.get("continuation")
                    as? JsonPrimitive)
                    ?.content

            if (!continuation.isNullOrBlank()) {
                results.add(continuation)
            }

            val continuationCommand =
                element["continuationCommand"]
                    as? JsonObject

            val commandToken =
                (continuationCommand
                    ?.get("token")
                    as? JsonPrimitive)
                    ?.content

            if (!commandToken.isNullOrBlank()) {
                results.add(commandToken)
            }

            element.values.forEach {
                collectLibraryContinuationTokens(
                    it,
                    results
                )
            }
        }

        is JsonArray ->
            element.forEach {
                collectLibraryContinuationTokens(
                    it,
                    results
                )
            }

        else -> Unit
    }
}

private suspend fun Innertube.libraryContinuationJson(
    continuation: String
): JsonElement? {

    return runCatching {

        val statement =
            client.post(BROWSE) {

                attributes.put(
                    Innertube.Attributes.UseCookies,
                    true
                )

                cookies?.let { cookieString ->

                    header(
                        "Cookie",
                        cookieString
                    )

                    header(
                        "X-Goog-AuthUser",
                        "0"
                    )

                    visitorData?.let {
                        header(
                            "X-Goog-Visitor-Id",
                            it
                        )
                    }

                    header(
                        "Origin",
                        "https://music.youtube.com"
                    )

                    header(
                        "Referer",
                        "https://music.youtube.com/"
                    )

                    header(
                        "X-Origin",
                        "https://music.youtube.com"
                    )

                    val sapisid =
                        cookieString
                            .split("; ")
                            .find {
                                it.startsWith(
                                    "SAPISID="
                                )
                            }
                            ?.substringAfter(
                                "SAPISID="
                            )

                    if (sapisid != null) {

                        val ts =
                            System.currentTimeMillis() /
                                1000

                        val payload =
                            "$ts $sapisid https://music.youtube.com"

                        val digest =
                            java.security
                                .MessageDigest
                                .getInstance(
                                    "SHA-1"
                                )
                                .digest(
                                    payload
                                        .toByteArray()
                                )

                        val hash =
                            digest.joinToString(
                                ""
                            ) {
                                "%02x".format(it)
                            }

                        header(
                            "Authorization",
                            "SAPISIDHASH ${ts}_${hash}"
                        )
                    }
                }

                setBody(
                    ContinuationBody(
                        continuation =
                            continuation
                    )
                )
            }

        libJson.parseToJsonElement(
            statement.bodyAsText()
        )

    }.getOrNull()
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
            collectRawPlaylists(
                jsonEl,
                items
            )

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

            /*
             * YouTube Music can split a user's library across
             * multiple shelves and continuation chains.
             *
             * Follow every unique continuation token we can find,
             * instead of assuming the first page is the whole library.
             */
            val pendingTokens =
                mutableListOf<String>()

            val discoveredTokens =
                linkedSetOf<String>()

            collectLibraryContinuationTokens(
                jsonEl,
                discoveredTokens
            )

            pendingTokens.addAll(
                discoveredTokens
            )

            val visitedTokens =
                mutableSetOf<String>()

            var continuationPages = 0

            while (
                pendingTokens.isNotEmpty() &&
                continuationPages < 100
            ) {

                val token =
                    pendingTokens.removeAt(0)

                if (
                    !visitedTokens.add(token)
                ) {
                    continue
                }

                val continuationJson =
                    libraryContinuationJson(
                        token
                    )
                        ?: continue

                continuationPages++

                collectRawPlaylists(
                    continuationJson,
                    items
                )

                val nextTokens =
                    linkedSetOf<String>()

                collectLibraryContinuationTokens(
                    continuationJson,
                    nextTokens
                )

                nextTokens.forEach {
                    nextToken ->

                    if (
                        nextToken !in
                            visitedTokens &&
                        nextToken !in
                            pendingTokens
                    ) {
                        pendingTokens.add(
                            nextToken
                        )
                    }
                }
            }

            debug.add(
                "LIB PAGINATION root=$browseId pages=$continuationPages playlists=${items.size}"
            )

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

    val uniqueItems =
        items.distinctBy {
            it.id
        }

    lastLibraryDebugInfo =
        LibraryDebugInfo(
            lines = debug,
            verdict =
                if (uniqueItems.isNotEmpty()) {
                    "Found ${uniqueItems.size} playlists"
                } else {
                    verdict
                }
        )

    uniqueItems
}
