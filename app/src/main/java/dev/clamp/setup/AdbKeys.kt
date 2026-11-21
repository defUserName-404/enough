package dev.clamp.setup

import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERBitString
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x509.Time
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator

/** App-private adb key material. Lives in noBackupFilesDir; deleted on success. */
class AdbKeys(private val dir: File) {
    private val keyFile get() = File(dir, "adb.key")
    private val crtFile get() = File(dir, "adb.crt")

    fun loadOrCreate(): Pair<PrivateKey, X509Certificate> {
        if (keyFile.exists() && crtFile.exists()) {
            runCatching {
                val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                val crt = CertificateFactory.getInstance("X.509")
                    .generateCertificate(ByteArrayInputStream(crtFile.readBytes())) as X509Certificate
                return key to crt
            }
        }
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val now = System.currentTimeMillis()
        val name = X500Name("CN=clamp")
        val sigAlg = AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption, DERNull.INSTANCE)
        val tbs = V3TBSCertificateGenerator().apply {
            setSerialNumber(ASN1Integer(now))
            setIssuer(name)
            setSubject(name)
            setStartDate(Time(Date(now - DAY)))
            setEndDate(Time(Date(now + 365 * DAY)))
            setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(kp.public.encoded))
            setSignature(sigAlg)
        }.generateTBSCertificate()
        val sig = Signature.getInstance("SHA256withRSA").run {
            initSign(kp.private)
            update(tbs.encoded)
            sign()
        }
        val der = DERSequence(arrayOf(tbs, sigAlg, DERBitString(sig))).encoded
        val crt = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        dir.mkdirs()
        dir.setReadable(false, false); dir.setReadable(true, true)
        dir.setWritable(false, false); dir.setWritable(true, true)
        writeAtomic(keyFile, kp.private.encoded)
        writeAtomic(crtFile, der)
        return kp.private to crt
    }

    private fun writeAtomic(f: File, bytes: ByteArray) {
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeBytes(bytes)
        tmp.setReadable(false, false); tmp.setReadable(true, true)
        tmp.setWritable(false, false); tmp.setWritable(true, true)
        check(tmp.renameTo(f)) { "could not write ${f.name}" }
    }

    fun delete() {
        dir.deleteRecursively()
    }

    private companion object {
        const val DAY = 24L * 3600 * 1000
    }
}
