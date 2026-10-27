package com.kienqb.dontv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebloatLogicTest {

    @Test
    fun parseList_readsPackagesAndNotes_skipsCommentsAndBlanks() {
        val text = """
            # Header comment
            # Apply: tools/disable.sh

            tv.samba.ssm                  # Samba tracker
            com.sony.dtv.sonyselect       # Sony Select promo
            com.google.android.videos
            not a package line
            com.sony.dtv.sonyselect       # duplicate ignored
        """.trimIndent()
        val entries = DebloatLogic.parseList(text)
        assertEquals(3, entries.size)
        assertEquals("tv.samba.ssm", entries[0].pkg)
        assertEquals("Samba tracker", entries[0].note)
        assertEquals("", entries[2].note)
    }

    @Test
    fun buildPlan_filtersUncheckedNotInstalledProtectedAndHome() {
        val entries = listOf(
            DebloatEntry("com.junk.one", ""),
            DebloatEntry("com.junk.two", ""),
            DebloatEntry("com.not.installed", ""),
            DebloatEntry("com.android.vending", ""),
            DebloatEntry("com.stock.launcher", ""),
        )
        val plan = DebloatLogic.buildPlan(
            entries,
            checked = setOf("com.junk.one", "com.not.installed", "com.android.vending", "com.stock.launcher"),
            installed = setOf("com.junk.one", "com.junk.two", "com.android.vending", "com.stock.launcher"),
            currentHome = "com.stock.launcher",
        )
        assertEquals(listOf("com.junk.one"), plan)
    }

    @Test
    fun buildPlan_neverDisablesOwnLauncherOrSelf() {
        val entries = listOf(
            DebloatEntry("com.gathin.tvhome", ""),
            DebloatEntry("com.kienqb.dontv", ""),
        )
        val plan = DebloatLogic.buildPlan(
            entries,
            checked = setOf("com.gathin.tvhome", "com.kienqb.dontv"),
            installed = setOf("com.gathin.tvhome", "com.kienqb.dontv"),
            currentHome = null,
        )
        assertTrue(plan.isEmpty())
    }

    @Test
    fun buildPlan_skipsAlreadyDisabled() {
        val entries = listOf(
            DebloatEntry("com.junk.one", ""),
            DebloatEntry("com.junk.off", ""),
        )
        val plan = DebloatLogic.buildPlan(
            entries,
            checked = setOf("com.junk.one", "com.junk.off"),
            installed = setOf("com.junk.one", "com.junk.off"),
            currentHome = null,
            alreadyDisabled = setOf("com.junk.off"),
        )
        assertEquals(listOf("com.junk.one"), plan)
    }

    @Test
    fun suggestModel_matchesSonyBravia() {
        val models = listOf(
            TvModel("sony-bravia-4k-gb", "Sony Bravia 4K GB"),
            TvModel("tcl-c645", "TCL C645"),
            TvModel("xiaomi-mi-tv-4a", "Xiaomi Mi TV 4A"),
        )
        val hit = DebloatLogic.suggestModel(models, "Sony", "BRAVIA 4K GB", "BRAVIA_ATV2")
        assertEquals("sony-bravia-4k-gb", hit?.slug)
    }

    @Test
    fun suggestModel_returnsNullWhenWeak() {
        val models = listOf(TvModel("tcl-c645", "TCL C645"))
        assertNull(DebloatLogic.suggestModel(models, "UnknownBrand", "SomeModel"))
    }
}
