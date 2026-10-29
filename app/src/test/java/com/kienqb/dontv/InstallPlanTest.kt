package com.kienqb.dontv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The /sdcard install denial (customer TV, 29 Oct 2026): the launcher
 * file transferred fully and MD5-matched, but `pm install` from
 * /sdcard failed with an SELinux avc denial — system_server may not
 * read sdcardfs on that firmware. These tests pin the escalation:
 * /sdcard first (works on most TVs), then /data/local/tmp, then a
 * stdin-streamed install that never reads a file from /sdcard.
 */
class InstallPlanTest {

    @Test
    fun attempts_sdcardFirst_thenTmpCopy_thenStdinStream() {
        val cmds = InstallPlan.attempts("/sdcard/dontv-tvhome.apk", 649903)
        assertEquals(3, cmds.size)
        assertEquals("pm install -r /sdcard/dontv-tvhome.apk", cmds[0])
        assertEquals(
            "cp /sdcard/dontv-tvhome.apk /data/local/tmp/dontv-tvhome.apk" +
                " && pm install -r /data/local/tmp/dontv-tvhome.apk",
            cmds[1]
        )
        assertEquals("cat /sdcard/dontv-tvhome.apk | pm install -r -S 649903", cmds[2])
    }

    @Test
    fun succeeded_onlyOnSuccessWord() {
        assertTrue(InstallPlan.succeeded("Success"))
        assertTrue(InstallPlan.succeeded("Performing Streamed Install\nSuccess"))
        assertFalse(InstallPlan.succeeded("Failure [INSTALL_FAILED_VERIFICATION_FAILURE]"))
        assertFalse(
            InstallPlan.succeeded(
                "avc: denied { read } for scontext=u:r:system_server:s0 " +
                    "tcontext=u:object_r:sdcardfs:s0 permissive=0"
            )
        )
        assertFalse(InstallPlan.succeeded(""))
    }
}
