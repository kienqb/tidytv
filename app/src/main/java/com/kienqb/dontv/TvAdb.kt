package com.kienqb.dontv

import android.content.Context
import android.os.Build
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
        setApi(Build.VERSION.SDK_INT)
        setTimeout(60, TimeUnit.SECONDS)
    }

    override fun getPrivateKey(): PrivateKey = privateKey
    override fun getCertificate(): Certificate = certificate
    override fun getDeviceName(): String = "TidyTV"

    /** Run one shell command on the TV and return its combined output. */
    fun exec(command: String): String {
        val stream = openStream("shell:$command")
        try {
            val bytes = stream.openInputStream().readBytes()
            return String(bytes, Charsets.UTF_8)
        } finally {
            try { stream.close() } catch (_: Exception) {}
        }
    }

    /**
     * Push raw bytes to a file on the TV through a shell stream, then verify
     * the on-device size. Returns true only when the size matches exactly.
     */
    fun pushBytes(data: ByteArray, remotePath: String): Boolean {
        val stream = openStream("shell:cat > $remotePath")
        try {
            val out = stream.openOutputStream()
            var off = 0
            while (off < data.size) {
                val len = minOf(64 * 1024, data.size - off)
                out.write(data, off, len)
                off += len
            }
            out.flush()
        } finally {
            try { stream.close() } catch (_: Exception) {}
        }
        val sizeOut = exec("stat -c %s $remotePath").trim()
        return sizeOut.toLongOrNull() == data.size.toLong()
    }
}
