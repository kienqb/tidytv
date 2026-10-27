package com.kienqb.dontv

import org.json.JSONObject

/** One published GitHub release worth offering to the user. */
data class ReleaseInfo(val version: String, val apkUrl: String)

/**
 * The pure half of the in-app updater: GitHub Releases JSON in, decision
 * out. The releases of github.com/kienqb/tidytv are the update source; tags
 * look like "v1.0.3" and each release carries one TidyTV-<version>.apk asset.
 */
object AppUpdate {

    /** "v1.0.3" / "1.0.3" -> [1, 0, 3]; null when unparseable. */
    fun parseVersion(raw: String): List<Int>? {
        val cleaned = raw.trim().removePrefix("v").removePrefix("V")
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split('.')
        if (parts.any { it.isEmpty() || it.any { c -> !c.isDigit() } }) return null
        return parts.map { it.toInt() }
    }

    /** True when [candidate] is strictly newer than [current]. */
    fun isNewer(candidate: String, current: String): Boolean {
        val c = parseVersion(candidate) ?: return false
        val u = parseVersion(current) ?: return true
        val n = maxOf(c.size, u.size)
        for (i in 0 until n) {
            val a = c.getOrElse(i) { 0 }
            val b = u.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** Reads a GitHub "latest release" payload; null when it has no .apk asset. */
    fun parseLatestRelease(json: String): ReleaseInfo? {
        val root = JSONObject(json)
        val tag = root.optString("tag_name")
        if (tag.isBlank() || parseVersion(tag) == null) return null
        val assets = root.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                val url = asset.optString("browser_download_url")
                if (url.isNotBlank()) return ReleaseInfo(tag.removePrefix("v"), url)
            }
        }
        return null
    }
}
