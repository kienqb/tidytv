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

    @Test
    fun chunks_splitAtSizeAndReassembleExactly() {
        val data = ByteArray(10_000) { (it % 251).toByte() }
        val parts = PushPlan.chunks(data, 6144)
        assertEquals(listOf(6144, 3856), parts.map { it.size })
        val back = ByteArray(data.size)
        var off = 0
        for (part in parts) {
            part.copyInto(back, off)
            off += part.size
        }
        assertEquals(data.toList(), back.toList())
    }

    @Test
    fun chunks_handleEmptyAndExactMultiple() {
        assertEquals(0, PushPlan.chunks(ByteArray(0), 6144).size)
        assertEquals(1, PushPlan.chunks(ByteArray(6144), 6144).size)
        assertEquals(2, PushPlan.chunks(ByteArray(12_288), 6144).size)
    }

    @Test
    fun md5Hex_matchesKnownVectors() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", PushIntegrity.md5Hex(ByteArray(0)))
        assertEquals(
            "900150983cd24fb0d6963f7d28e17f72",
            PushIntegrity.md5Hex("abc".toByteArray(Charsets.US_ASCII)),
        )
    }

    @Test
    fun parseMd5Sum_readsHashAndRejectsGarbage() {
        assertEquals(
            "900150983cd24fb0d6963f7d28e17f72",
            PushIntegrity.parseMd5Sum("900150983cd24fb0d6963f7d28e17f72  /sdcard/x.apk"),
        )
        assertNull(PushIntegrity.parseMd5Sum(""))
        assertNull(PushIntegrity.parseMd5Sum("md5sum: /sdcard/x.apk: No such file or directory"))
        assertNull(PushIntegrity.parseMd5Sum("/system/bin/sh: md5sum: not found"))
    }
}
