package com.kienqb.dontv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The X80J incident (29 Oct 2026): step 5 disabled the Google TV stock
 * launcher pair on a TV where the new launcher had never actually been
 * verified as present and enabled, and after a reboot the TV hung at the
 * logo with no working Home. These tests pin the rule that step 5 may
 * only disable the stock launcher after the new launcher is proven to be
 * installed AND enabled on the TV.
 */
class HomeSafetyTest {

    @Test
    fun parsePackageList_readsPackageLines_ignoresJunk() {
        val out = """
            package:com.gathin.tvhome
            package:com.android.tv.settings

            garbage line
            package:com.kienqb.dontv
        """.trimIndent()
        val pkgs = HomeSafety.parsePackageList(out)
        assertEquals(
            setOf("com.gathin.tvhome", "com.android.tv.settings", "com.kienqb.dontv"),
            pkgs
        )
    }

    @Test
    fun launcherState_readyWhenInstalledAndEnabled() {
        val installed = setOf("com.gathin.tvhome", "com.android.tv.settings")
        assertEquals(
            HomeSafety.LauncherState.READY,
            HomeSafety.launcherState(installed, emptySet(), "com.gathin.tvhome")
        )
    }

    @Test
    fun launcherState_notInstalledWhenAbsent() {
        val installed = setOf("com.android.tv.settings")
        assertEquals(
            HomeSafety.LauncherState.NOT_INSTALLED,
            HomeSafety.launcherState(installed, emptySet(), "com.gathin.tvhome")
        )
    }

    @Test
    fun launcherState_disabledWhenInstalledButOff() {
        val installed = setOf("com.gathin.tvhome")
        val disabled = setOf("com.gathin.tvhome")
        assertEquals(
            HomeSafety.LauncherState.DISABLED,
            HomeSafety.launcherState(installed, disabled, "com.gathin.tvhome")
        )
    }

    @Test
    fun mayDisableStockLauncher_onlyWhenReadyAndHoldingHome() {
        val pkg = "com.gathin.tvhome"
        // Verified: installed, enabled, already holding Home -> allowed.
        assertTrue(
            HomeSafety.mayDisableStockLauncher(setOf(pkg), emptySet(), pkg, pkg)
        )
        // The X80J case: installed and enabled, but the stock launcher
        // still holds Home -> must refuse.
        assertFalse(
            HomeSafety.mayDisableStockLauncher(
                setOf(pkg), emptySet(), pkg, "com.google.android.apps.tv.launcherx"
            )
        )
        assertFalse(
            HomeSafety.mayDisableStockLauncher(setOf(pkg), emptySet(), pkg, null)
        )
        // Not installed / disabled -> refuse even if Home claims it.
        assertFalse(
            HomeSafety.mayDisableStockLauncher(emptySet(), emptySet(), pkg, pkg)
        )
        assertFalse(
            HomeSafety.mayDisableStockLauncher(setOf(pkg), setOf(pkg), pkg, pkg)
        )
    }
}
