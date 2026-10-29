package com.kienqb.dontv

/**
 * Safety gate for step 5 (disabling the stock launcher).
 *
 * Lesson from the Sony KD-55X80J incident (29 Oct 2026): the customer
 * ticked "Google TV" in step 5, the app disabled the stock launcher
 * (launcherx + setupwraith), the at-the-moment Home check passed, but
 * after a reboot the TV hung at the Sony logo — the new launcher never
 * came up as Home on that model. The TV had to be factory-reset with
 * the hardware buttons.
 *
 * The owner's prerequisite from that incident: step 5 may only disable
 * the stock launcher when the new launcher is *verified* on the TV —
 * installed, enabled AND already holding Home (step 4 sets it as Home
 * right after installing). A launcher that is merely installed and set
 * up, but has not actually won Home while the stock launcher is still
 * alive, is NOT verified: disabling the stock launcher in that state
 * is exactly what bricked the X80J.
 */
object HomeSafety {

    enum class LauncherState { READY, NOT_INSTALLED, DISABLED }

    /** Parse `pm list packages` (or `pm list packages -d`) output into a set of package names. */
    fun parsePackageList(output: String): Set<String> =
        output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    /** READY only when the launcher is installed and not in the disabled set. */
    fun launcherState(installed: Set<String>, disabled: Set<String>, pkg: String): LauncherState = when {
        pkg !in installed -> LauncherState.NOT_INSTALLED
        pkg in disabled -> LauncherState.DISABLED
        else -> LauncherState.READY
    }

    /**
     * Step 5's stock-launcher disable is allowed only when the launcher
     * is READY and already holds Home ([currentHome] == [pkg]).
     */
    fun mayDisableStockLauncher(
        installed: Set<String>,
        disabled: Set<String>,
        pkg: String,
        currentHome: String?
    ): Boolean =
        launcherState(installed, disabled, pkg) == LauncherState.READY && currentHome == pkg
}
