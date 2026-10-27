package com.kienqb.dontv

/**
 * The launcher's hard lock leans on three grants made outside the launcher
 * app: usage access plus its two accessibility services (input keys and
 * home keys). The launcher's own "Bảo vệ khoá" row can only send the user
 * to system screens, and on some firmware those screens do not exist —
 * verified on a Sony Android TV 9: the usage-access screen resolves to no
 * activity at all and the accessibility screen lands on a Google stub.
 * TidyTV already holds an ADB channel while it installs the launcher, so
 * it grants all three itself and reads the state back. This object is the
 * pure half: the service list merging and the grant checks.
 */
object GuardLogic {

    /** Accessibility services of the launcher, as flattened components. */
    val GUARD_SERVICES: List<String> = listOf(
        "com.gathin.tvhome/com.gathin.tvhome.InputKeyService",
        "com.gathin.tvhome/com.gathin.tvhome.HomeKeyService",
    )

    /**
     * Merges [needed] into the current value of
     * enabled_accessibility_services (colon-separated flattened components;
     * "null"/blank when nothing is enabled yet). Services already on stay
     * on, in their original order — other apps' services are not ours to
     * switch off.
     */
    fun mergeEnabledServices(current: String?, needed: List<String>): String {
        val out = mutableListOf<String>()
        if (!current.isNullOrBlank() && current.trim() != "null") {
            for (part in current.split(':')) {
                val p = part.trim()
                if (p.isNotEmpty() && p !in out) out.add(p)
            }
        }
        for (service in needed) if (service !in out) out.add(service)
        return out.joinToString(":")
    }

    /** True when `cmd appops get` output shows usage access allowed. */
    fun usageAllowed(appopsOut: String): Boolean =
        appopsOut.contains("GET_USAGE_STATS: allow")

    /** True when every guard service is in the enabled-services value. */
    fun servicesEnabled(servicesValue: String?, needed: List<String>): Boolean {
        if (servicesValue.isNullOrBlank() || servicesValue.trim() == "null") return false
        val parts = servicesValue.split(':').map { it.trim() }.toSet()
        return needed.all { it in parts }
    }
}
