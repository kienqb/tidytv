package com.kienqb.dontv

/** One line of a debloat list: a package plus the human note from the list file. */
data class DebloatEntry(val pkg: String, val note: String)

/** A TV model whose debloat list ships in assets/models/. */
data class TvModel(val slug: String, val name: String)

object DebloatLogic {

    /**
     * Packages that must never be disabled, whatever a list or the user says:
     * Android core, settings, the store, Google services (Wi-Fi/cast plumbing on
     * many sets), the launcher this app installs, and this app itself.
     */
    val PROTECTED: Set<String> = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.tv.settings",
        "com.android.vending",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.gathin.tvhome",
        "com.kienqb.dontv",
    )

    private val PKG_LINE = Regex("""^([a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z0-9_]+)+)\s*(?:#(.*))?$""")

    /**
     * Parse a CleanYourTV-style debloat.txt: package lines with an optional
     * trailing "# note". Blank lines and pure comment lines are skipped.
     */
    fun parseList(text: String): List<DebloatEntry> {
        val out = LinkedHashMap<String, DebloatEntry>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val m = PKG_LINE.matchEntire(line) ?: continue
            val pkg = m.groupValues[1]
            val note = m.groupValues.getOrNull(2)?.trim().orEmpty()
            if (pkg !in out) out[pkg] = DebloatEntry(pkg, note)
        }
        return out.values.toList()
    }

    /**
     * The final disable plan: entries the user left checked, that are actually
     * installed on the TV, minus protected packages and the current HOME app
     * (switching Home is a separate, deliberate step).
     */
    fun buildPlan(
        entries: List<DebloatEntry>,
        checked: Set<String>,
        installed: Set<String>,
        currentHome: String?,
        alreadyDisabled: Set<String> = emptySet(),
    ): List<String> {
        val plan = ArrayList<String>()
        val seen = HashSet<String>()
        for (e in entries) {
            val pkg = e.pkg
            if (pkg !in checked) continue
            if (pkg !in installed) continue
            if (pkg in PROTECTED) continue
            if (pkg == currentHome) continue
            // Already off before this app ran: not ours to record or restore.
            if (pkg in alreadyDisabled) continue
            if (seen.add(pkg)) plan.add(pkg)
        }
        return plan
    }

    /**
     * Step 4's "Android TV" option: the stock launcher package that may be
     * disabled so the new launcher can win the Home button — the package
     * currently holding Home, unless it is protected (system pieces, the
     * new launcher itself, this app).
     */
    fun homeToDisable(currentHome: String?): String? =
        currentHome?.takeIf { it !in PROTECTED }

    /**
     * Suggest the most likely model list for a TV from its brand/model/device
     * strings. Primary score = number of slug tokens (length >= 3) appearing
     * in the lowercased device description; ties are broken by the number of
     * short (2-char) tokens matched, then by the longer (more specific) slug,
     * so sibling variants (e.g. -gb vs -vh2) resolve to the closest list.
     * Returns null when the primary score is under 2 (nothing plausible).
     */
    fun suggestModel(models: List<TvModel>, vararg deviceInfo: String?): TvModel? {
        val hay = deviceInfo.filterNotNull().joinToString(" ").lowercase()
        if (hay.isBlank()) return null
        var best: TvModel? = null
        var bestScore = 0
        var bestShort = -1
        for (model in models) {
            val tokens = model.slug.split('-')
            val score = tokens.filter { it.length >= 3 }.count { tok -> hay.contains(tok) }
            val short = tokens.filter { it.length == 2 }.count { tok -> hay.contains(tok) }
            val better = score > bestScore ||
                (score == bestScore && score > 0 && short > bestShort) ||
                (score == bestScore && score > 0 && short == bestShort &&
                    (best == null || model.slug.length > best.slug.length))
            if (better) {
                bestScore = score
                bestShort = short
                best = model
            }
        }
        return if (bestScore >= 2) best else null
    }
}
