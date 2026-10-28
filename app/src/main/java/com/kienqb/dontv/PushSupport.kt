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

    /** Raw bytes per chunk for the exec-command fallback (base64 of this is 8192 chars). */
    const val EXEC_CHUNK = 6144

    /** Split [data] into consecutive chunks of at most [chunkSize] bytes. */
    fun chunks(data: ByteArray, chunkSize: Int): List<ByteArray> {
        if (data.isEmpty()) return emptyList()
        val out = ArrayList<ByteArray>((data.size + chunkSize - 1) / chunkSize)
        var off = 0
        while (off < data.size) {
            val len = minOf(chunkSize, data.size - off)
            out.add(data.copyOfRange(off, off + len))
            off += len
        }
        return out
    }
}

/** Whole-file integrity: the push only counts as done when the TV's MD5 matches. */
object PushIntegrity {

    /** Lowercase hex MD5 of [data]. */
    fun md5Hex(data: ByteArray): String =
        java.security.MessageDigest.getInstance("MD5").digest(data)
            .joinToString("") { "%02x".format(it) }

    /**
     * `md5sum <file>` prints "<md5>  <path>". Returns the 32-char hash, or
     * null when the output holds none (tool missing, error text...).
     */
    fun parseMd5Sum(output: String): String? {
        val token = output.trim().split(Regex("\\s+")).firstOrNull()?.lowercase() ?: return null
        return if (token.length == 32 && token.all { it in '0'..'9' || it in 'a'..'f' }) token else null
    }
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
