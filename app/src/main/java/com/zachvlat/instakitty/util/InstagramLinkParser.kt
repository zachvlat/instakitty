package com.zachvlat.instakitty.util

/**
 * Turns Instagram URLs (from a VIEW intent or shared text) into an in-app navigation route.
 * Pure Kotlin so it can be unit tested without Android framework classes.
 */
object InstagramLinkParser {

    private val URL_REGEX =
        Regex("(?:https?://)?(?:www\\.)?instagram\\.com/[^\\s]+", RegexOption.IGNORE_CASE)

    private val HOST_REGEX =
        Regex("^(?:https?://)?(?:www\\.)?instagram\\.com(?::\\d+)?(/.*)?$", RegexOption.IGNORE_CASE)

    /** Routes an Instagram URL to an in-app route, or null if it is not a supported Instagram link. */
    fun routeFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val normalized = url.trim()
        val match = HOST_REGEX.find(normalized) ?: return null
        val path = (match.groupValues.getOrNull(1) ?: "")
            .substringBefore('#')
            .substringBefore('?')
        val segments = path.split('/').filter { it.isNotBlank() }

        return when {
            segments.size == 1 && segments[0] != "p" -> "user/${segments[0]}"
            segments.size == 2 && segments[0] == "p" -> "post/${segments[1]}"
            segments.size == 2 && segments[0] == "reel" -> "post/${segments[1]}"
            segments.size == 3 && segments[1] == "p" -> "post/${segments[2]}"
            segments.size == 3 && segments[1] == "reel" -> "post/${segments[2]}"
            segments.size >= 2 && segments[0] == "stories" -> "user/${segments[1]}"
            else -> null
        }
    }

    /** Pulls the first Instagram URL out of arbitrary shared text and routes it. */
    fun routeFromSharedText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = URL_REGEX.find(text) ?: return null
        val raw = match.value.trimEnd('.', ',', ')', ']', '\'', '"', '!')
        val url = if (raw.startsWith("http", ignoreCase = true)) raw else "https://$raw"
        return routeFromUrl(url)
    }
}
