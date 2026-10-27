package com.kienqb.dontv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeToDisableTest {

    @Test
    fun returnsStockLauncherHoldingHome() {
        assertEquals(
            "com.google.android.tvlauncher",
            DebloatLogic.homeToDisable("com.google.android.tvlauncher")
        )
        assertEquals(
            "com.mitv.tvhome",
            DebloatLogic.homeToDisable("com.mitv.tvhome")
        )
    }

    @Test
    fun neverDisablesProtectedPackages() {
        // The new launcher, this app and the TV settings/fallback home must
        // never be disabled, even if one of them currently resolves as Home.
        assertNull(DebloatLogic.homeToDisable("com.gathin.tvhome"))
        assertNull(DebloatLogic.homeToDisable("com.kienqb.dontv"))
        assertNull(DebloatLogic.homeToDisable("com.android.tv.settings"))
    }

    @Test
    fun nullHomeDisablesNothing() {
        assertNull(DebloatLogic.homeToDisable(null))
    }
}
