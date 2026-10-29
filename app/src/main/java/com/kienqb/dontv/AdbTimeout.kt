package com.kienqb.dontv

/**
 * Time budgets for talking to the TV, and the words shown when one runs
 * out. On home Wi-Fi a TV that dozed off (Wi-Fi sleeps with the screen)
 * or sits on a weak signal does not refuse commands — it just never
 * answers them, so every exchange needs a hard bound and the failure
 * needs to tell the user the likely physical cause instead of a bare
 * "timeout".
 */
object AdbTimeout {

    /** Ordinary shell commands: a healthy TV answers in well under a
     * second; 15 s of silence means the link, not the command, is dead. */
    const val EXEC_MS = 15_000L

    /** pm install is the one command allowed to take its time: a slow TV
     * can spend most of a minute staging and verifying an APK. */
    const val INSTALL_MS = 60_000L

    /** What the log says when the TV stays silent past [seconds]. */
    fun message(seconds: Long): String =
        "TV không trả lời sau $seconds giây. " +
            "Đảm bảo màn hình TV đang sáng và không ở chế độ chờ, " +
            "Wi-Fi vẫn bắt tốt, rồi thử lại."
}
