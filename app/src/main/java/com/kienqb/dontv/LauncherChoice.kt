package com.kienqb.dontv

/**
 * Step 4's two launcher editions. Both are the same tvhome build (same
 * package, same signing key, so they install over each other); they
 * differ only in what the APK declares and what setup follows:
 *
 *  - PASSWORD: the full share build — TOTP + passcode lock, Telegram
 *    remote, and the two accessibility services the lock guards need.
 *    Play Protect blocks its install on some TVs, so this path switches
 *    the install verifier off first and enables the 3 lock guards after.
 *  - NOPASS: the same build with the two accessibility services removed
 *    from the manifest. No lock (nothing intercepts keys), but also
 *    nothing for Play Protect to block — for TVs whose owners never use
 *    a passcode and whose Play Protect cannot be switched off.
 */
enum class LauncherChoice(val assetName: String, val lockGuards: Boolean) {
    PASSWORD("tvhome.apk", true),
    NOPASS("tvhome-nopass.apk", false),
}
