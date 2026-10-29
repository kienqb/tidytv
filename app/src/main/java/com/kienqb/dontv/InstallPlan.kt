package com.kienqb.dontv

/**
 * The install attempts for step 4, in order, stopping at the first
 * success. The APK is always staged on /sdcard first (proven writable
 * on every TV seen, transfer verified by MD5) — but *installing* from
 * /sdcard does not work everywhere:
 *
 * Lesson from a customer's TV (29 Oct 2026, a different customer from
 * the W800C case): the file arrived 100% and MD5-matched, yet
 * `pm install` failed with
 *   avc: denied { read } for scontext=u:r:system_server:s0
 *   tcontext=u:object_r:sdcardfs:s0 permissive=0
 * — that firmware's SELinux policy forbids the install process
 * (system_server) from reading files on /sdcard at all.
 *
 * So after the plain /sdcard attempt, escalate:
 *  2. copy the staged file into /data/local/tmp (the classic adb
 *     install location, labelled for the installer to read) and
 *     install from there. The copy runs in the shell, which may read
 *     /sdcard even when system_server may not.
 *  3. stream the APK through a pipe: the shell reads the file and
 *     `pm install -S <bytes>` consumes stdin, so the installer never
 *     opens a file on /sdcard itself.
 */
object InstallPlan {

    /** Where attempt 2 stages the APK for the installer to read. */
    const val TMP_APK = "/data/local/tmp/dontv-tvhome.apk"

    /** Ordered shell commands; run until one reports success. */
    fun attempts(remoteApk: String, apkSize: Int): List<String> = listOf(
        "pm install -r $remoteApk",
        "cp $remoteApk $TMP_APK && pm install -r $TMP_APK",
        "cat $remoteApk | pm install -r -S $apkSize"
    )

    /** `pm install` prints "Success" on stdout when the install lands. */
    fun succeeded(output: String): Boolean = output.contains("Success")
}
