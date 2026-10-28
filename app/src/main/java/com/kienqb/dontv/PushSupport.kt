package com.kienqb.dontv

/**
 * Pure pieces of the file push (step 4's launcher, step 2's backup file),
 * kept free of Android/IO so they are unit-testable.
 */
object PushPlan {

    const val BLOCK = 4096

    fun blockCount(size: Int): Int =
        if (size <= 0) 0 else (size + BLOCK - 1) / BLOCK

    /** The fallback sender pads the data with zeroes up to a whole block. */
    fun paddedSize(size: Int): Int = blockCount(size) * BLOCK

    /** Primary reader (toybox with head -c): consume exactly [size] bytes. */
    fun headScript(size: Int, remotePath: String, marker: String): String =
        "head -c $size > $remotePath; echo $marker"

    /**
     * Fallback for toybox builds without head -c (Android 8): dd in whole
     * blocks — iflag=fullblock keeps filling a block across short pipe reads
     * instead of counting each short read as a whole block — then truncate
     * the zero padding back off. The sender must send [paddedSize] bytes.
     * A dd too old for iflag fails at once instead of crawling byte by byte
     * the way the old bs=1 fallback did.
     */
    fun ddScript(size: Int, remotePath: String, marker: String): String =
        "dd bs=$BLOCK count=${blockCount(size)} iflag=fullblock > $remotePath 2>/dev/null; " +
            "truncate -s $size $remotePath; echo $marker"
}

/** Fires each 10% milestone exactly once as bytes are written. */
class PushProgress(private val total: Int) {

    private var firedUpTo = 0

    /** Returns the milestone just reached (10..100), or null. */
    fun onWritten(sent: Int): Int? {
        if (total <= 0) return null
        var hit: Int? = null
        var m = firedUpTo + 10
        while (m <= 100 && sent.toLong() * 100 >= total.toLong() * m) {
            hit = m
            firedUpTo = m
            m += 10
        }
        return hit
    }
}
