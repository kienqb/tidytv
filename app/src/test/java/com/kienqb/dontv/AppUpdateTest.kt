package com.kienqb.dontv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {

    @Test
    fun parseVersion_handlesVPrefixAndPlain() {
        assertEquals(listOf(1, 0, 3), AppUpdate.parseVersion("v1.0.3"))
        assertEquals(listOf(1, 0, 3), AppUpdate.parseVersion("1.0.3"))
        assertEquals(listOf(2, 0), AppUpdate.parseVersion(" 2.0 "))
        assertNull(AppUpdate.parseVersion("v1.x"))
        assertNull(AppUpdate.parseVersion(""))
    }

    @Test
    fun isNewer_comparesNumericallyNotLexically() {
        assertTrue(AppUpdate.isNewer("1.0.10", "1.0.9"))
        assertTrue(AppUpdate.isNewer("v1.1", "1.0.3"))
        assertFalse(AppUpdate.isNewer("1.0.3", "1.0.3"))
        assertFalse(AppUpdate.isNewer("1.0.2", "1.0.3"))
        assertFalse(AppUpdate.isNewer("khong-phai-ban", "1.0.3"))
    }

    @Test
    fun parseLatestRelease_picksTheApkAsset() {
        val json = """
            {"tag_name":"v1.0.4","assets":[
              {"name":"notes.txt","browser_download_url":"https://example.com/notes.txt"},
              {"name":"TidyTV-1.0.4.apk","browser_download_url":"https://example.com/TidyTV-1.0.4.apk"}
            ]}
        """.trimIndent()
        val info = AppUpdate.parseLatestRelease(json)
        assertEquals("1.0.4", info?.version)
        assertEquals("https://example.com/TidyTV-1.0.4.apk", info?.apkUrl)
    }

    @Test
    fun parseLatestRelease_nullWhenNoApkOrBadTag() {
        assertNull(AppUpdate.parseLatestRelease("""{"tag_name":"v1.0.4","assets":[]}"""))
        assertNull(AppUpdate.parseLatestRelease("""{"tag_name":"","assets":[{"name":"a.apk","browser_download_url":"https://e/a.apk"}]}"""))
    }
}
