package com.kienqb.dontv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushSupportTest {

    private val path = "/data/local/tmp/dontv-tvhome.apk"

    @Test
    fun blockCount_roundsUpToWholeBlocks() {
        assertEquals(161, PushPlan.blockCount(655_507))
        assertEquals(1, PushPlan.blockCount(4096))
        assertEquals(2, PushPlan.blockCount(4097))
        assertEquals(1, PushPlan.blockCount(1))
    }

    @Test
    fun paddedSize_isBlockCountTimesBlock() {
        assertEquals(659_456, PushPlan.paddedSize(655_507))
        assertEquals(4096, PushPlan.paddedSize(4096))
    }

    @Test
    fun headScript_readsTheExactByteCount() {
        assertEquals(
            "head -c 655507 > $path; echo M1",
            PushPlan.headScript(655_507, path, "M1"),
        )
    }

    @Test
    fun ddScript_readsWholeBlocksThenTruncatesBackToExactSize() {
        assertEquals(
            "dd bs=4096 count=161 iflag=fullblock > $path 2>/dev/null; " +
                "truncate -s 655507 $path; echo M1",
            PushPlan.ddScript(655_507, path, "M1"),
        )
    }

    @Test
    fun progress_firesEachTenPercentOnce() {
        val p = PushProgress(1000)
        assertNull(p.onWritten(50))
        assertEquals(10, p.onWritten(100))
        assertNull(p.onWritten(150))
        assertEquals(90, p.onWritten(999))
        assertEquals(100, p.onWritten(1000))
        assertNull(p.onWritten(1000))
    }

    @Test
    fun progress_usesExactIntegerThresholds() {
        val p = PushProgress(655_507)
        assertNull(p.onWritten(65_550))
        assertEquals(10, p.onWritten(65_551))
    }
}
