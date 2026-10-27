package com.kienqb.dontv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardLogicTest {

    private val guards = GuardLogic.GUARD_SERVICES

    @Test
    fun merge_intoUnsetValue_givesJustTheGuards() {
        assertEquals(guards.joinToString(":"), GuardLogic.mergeEnabledServices(null, guards))
        assertEquals(guards.joinToString(":"), GuardLogic.mergeEnabledServices("", guards))
        assertEquals(guards.joinToString(":"), GuardLogic.mergeEnabledServices("null", guards))
    }

    @Test
    fun merge_keepsOtherAppsServicesFirst() {
        val other = "com.example.other/com.example.other.ReadingService"
        val merged = GuardLogic.mergeEnabledServices(other, guards)
        assertEquals(listOf(other) + guards, merged.split(':'))
    }

    @Test
    fun merge_isIdempotentWhenGuardsAlreadyOn() {
        val current = guards.joinToString(":")
        assertEquals(current, GuardLogic.mergeEnabledServices(current, guards))
    }

    @Test
    fun merge_addsOnlyTheMissingGuard() {
        val current = "com.other.app/com.other.app.Svc:${guards[0]}"
        val merged = GuardLogic.mergeEnabledServices(current, guards)
        assertEquals("com.other.app/com.other.app.Svc:${guards[0]}:${guards[1]}", merged)
    }

    @Test
    fun usageAllowed_readsAppopsOutput() {
        assertTrue(GuardLogic.usageAllowed("GET_USAGE_STATS: allow; rejectTime=+46s ago"))
        assertFalse(GuardLogic.usageAllowed("GET_USAGE_STATS: default; rejectTime=+46s ago"))
        assertFalse(GuardLogic.usageAllowed(""))
    }

    @Test
    fun servicesEnabled_needsEveryGuard() {
        assertTrue(GuardLogic.servicesEnabled(guards.joinToString(":"), guards))
        assertFalse(GuardLogic.servicesEnabled(guards[0], guards))
        assertFalse(GuardLogic.servicesEnabled(null, guards))
        assertFalse(GuardLogic.servicesEnabled("null", guards))
    }
}
