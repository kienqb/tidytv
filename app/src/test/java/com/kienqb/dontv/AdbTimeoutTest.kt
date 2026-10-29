package com.kienqb.dontv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbTimeoutTest {

    @Test
    fun ordinaryCommandsGetTheShortBudget() {
        // The agreed 10-15 s window for a normal command on flaky Wi-Fi.
        assertEquals(15_000L, AdbTimeout.EXEC_MS)
        assertTrue(AdbTimeout.INSTALL_MS > AdbTimeout.EXEC_MS)
    }

    @Test
    fun timeoutMessageCarriesTheScreenAwakeHint() {
        val msg = AdbTimeout.message(15)
        assertTrue(msg.contains("15 giây"))
        assertTrue(msg.contains("Đảm bảo màn hình TV đang sáng và không ở chế độ chờ"))
    }
}
