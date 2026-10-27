package com.kienqb.dontv

import android.content.Context
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * ADB-over-Wi-Fi client (libadb-android). The RSA key + self-signed cert are
 * generated once and persisted in the app's private storage, so the TV only
 * asks "Allow USB debugging?" the first time.
 */
class TvAdb(context: Context) : AbsAdbConnectionManager() {

    private val keyFile = File(context.filesDir, "adb_key.pk8")
    private val certFile = File(context.filesDir, "adb_cert.der")
    private val privateKey: PrivateKey
    private val certificate: Certificate

    init {
        if (keyFile.exists() && certFile.exists()) {
            privateKey = KeyFactory.getInstance("RSA")
                .generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
            certificate = certFile.inputStream().use { input ->
                CertificateFactory.getInstance("X.509").generateCertificate(input)
            }
        } else {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048)
            val kp = kpg.generateKeyPair()
            val name = X500Name("CN=TidyTV")
            val now = System.currentTimeMillis()
            val certBuilder = JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(now / 1000),
                Date(now - 24L * 3600 * 1000),
                Date(now + 20L * 365 * 24 * 3600 * 1000),
                name, kp.public,
            )
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
            val cert = JcaX509CertificateConverter().getCertificate(certBuilder.build(signer))
            privateKey = kp.private
            certificate = cert
            keyFile.writeBytes(kp.private.encoded)
            certFile.writeBytes(cert.encoded)
        }
        // Deliberately NO setApi() call: CleanTV (the working reference app)
        // leaves the manager default (API BASE), so the CNXN handshake uses
        // the legacy protocol version + maxdata that old adbd builds (Sony
        // Android 8/9) are most compatible with. setApi(28) switched the
        // handshake to the skip-checksum protocol version for no benefit.
        setTimeout(60, TimeUnit.SECONDS)
    }

    override fun getPrivateKey(): PrivateKey = privateKey
    override fun getCertificate(): Certificate = certificate
    override fun getDeviceName(): String = "TidyTV"

    /**
     * Run one shell command on the TV and return its combined output.
     *
     * Reads until a self-appended end marker instead of reading to EOF —
     * the pattern CleanTV uses against the same library. libadb's
     * AdbStream.read() throws IOException("Stream closed") when the peer
     * closes the stream while the reader is waiting with an empty queue
     * (instead of returning -1); the old readBytes()-to-EOF version turned
     * that race into a failed connect on the user's Sony (v1.0.3/1.0.4:
     * "Kết nối thất bại: Stream closed" right after a successful Allow in
     * the TV's dialog). Here the reader thread swallows stream exceptions
     * and keeps whatever output already arrived; the marker (or the
     * deadline) ends the read, never the peer's close timing.
     */
    fun exec(command: String): String {
        val marker = "__TDV_" + System.nanoTime().toString(16) + "__"
        val stream = openStream("shell:$command; echo $marker")
        try {
            return readUntil(stream, marker, 60_000, throwOnTimeout = true)
                .replace("\r", "")
                .substringBefore(marker)
        } finally {
            try { stream.close() } catch (_: Exception) {}
        }
    }

    /**
     * Reads [stream] on a helper thread until [marker] appears in the
     * accumulated output, the stream ends, or the deadline passes; returns
     * what arrived. Stream exceptions are swallowed on purpose (see exec).
     */
    private fun readUntil(
        stream: io.github.muntashirakon.adb.AdbStream,
        marker: String?,
        deadlineMs: Long,
        throwOnTimeout: Boolean,
    ): String {
        val out = java.io.ByteArrayOutputStream()
        val reader = Thread {
            try {
                val input = stream.openInputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    synchronized(out) { out.write(buf, 0, n) }
                    if (marker != null &&
                        synchronized(out) { out.toString("UTF-8") }.contains(marker)
                    ) {
                        break
                    }
                }
            } catch (_: Exception) {
                // Peer closed the stream mid-read: keep what we have.
            }
        }.apply { isDaemon = true; start() }
        val deadline = System.currentTimeMillis() + deadlineMs
        while (reader.isAlive && System.currentTimeMillis() < deadline) {
            reader.join(200)
        }
        if (reader.isAlive && throwOnTimeout) {
            reader.interrupt()
            throw java.io.IOException("TV không trả lời sau ${deadlineMs / 1000} giây")
        }
        return synchronized(out) { out.toString("UTF-8") }
    }

    /**
     * Push raw bytes to a file on the TV, then verify the on-device size.
     * Returns true only when the size matches exactly.
     *
     * CleanTV's proven pattern: an `exec:` stream whose command reads an
     * EXACT byte count from stdin, and the stream is held open until the TV
     * echoes a marker — which only happens after the reader consumed every
     * byte and the file is closed on disk. The previous "shell:cat > path"
     * version closed the stream the moment the last byte was written; adbd
     * tore the shell down before cat drained the pipe and the tail was
     * lost (user's Sony, v1.0.5: the 636 KB launcher APK arrived
     * incomplete and this size check caught it).
     */
    fun pushBytes(data: ByteArray, remotePath: String): Boolean {
        if (pushExact(data, remotePath, "head -c ${data.size}")) {
            return verifySize(data, remotePath)
        }
        // Android 8 toybox has no head -c; dd reads the same exact count.
        if (pushExact(data, remotePath, "dd bs=1 count=${data.size} 2>/dev/null")) {
            return verifySize(data, remotePath)
        }
        return false
    }

    private fun pushExact(data: ByteArray, remotePath: String, readerCmd: String): Boolean {
        val marker = "__TDV_" + System.nanoTime().toString(16) + "__"
        return try {
            val stream = openStream("exec:sh -c '$readerCmd > $remotePath; echo $marker'")
            try {
                val out = stream.openOutputStream()
                var off = 0
                while (off < data.size) {
                    val len = minOf(4000, data.size - off)
                    out.write(data, off, len)
                    off += len
                }
                out.flush()
                readUntil(stream, marker, 120_000, throwOnTimeout = false).contains(marker)
            } finally {
                try { stream.close() } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun verifySize(data: ByteArray, remotePath: String): Boolean =
        exec("stat -c %s $remotePath").trim().toLongOrNull() == data.size.toLong()
}
