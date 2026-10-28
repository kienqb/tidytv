package com.kienqb.dontv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherChoiceTest {

    @Test
    fun editionsUseDistinctBundledAssets() {
        assertEquals("tvhome.apk", LauncherChoice.PASSWORD.assetName)
        assertEquals("tvhome-nopass.apk", LauncherChoice.NOPASS.assetName)
        assertNotEquals(LauncherChoice.PASSWORD.assetName, LauncherChoice.NOPASS.assetName)
    }

    @Test
    fun onlyThePasswordEditionEnablesLockGuards() {
        assertTrue(LauncherChoice.PASSWORD.lockGuards)
        assertFalse(LauncherChoice.NOPASS.lockGuards)
    }
}
