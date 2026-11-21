package de.meinhelfer.tag.backup

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Passwort falsch oder Datei beschaedigt bzw. abgeschnitten. */
class WrongPasswordException : IOException("Passwort falsch oder Datei beschädigt")

/**
 * Verschluesselung der Sicherungsdatei: Schluessel aus dem Passwort (PBKDF2-HMAC-SHA256),
 * Daten in 1-MB-Bloecken mit AES-256-GCM. Jeder Block ist einzeln beglaubigt, der letzte ist markiert,
 * damit auch abgeschnittene Dateien erkannt werden.
 */
internal object BackupCrypto {
    private val MAGIC = "MHBK1".toByteArray(Charsets.US_ASCII)
    private const val CHUNK = 1 shl 20
    private const val ITERATIONS = 210_000
    private const val SALT_LEN = 16
    private const val PREFIX_LEN = 8

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    private fun nonce(prefix: ByteArray, counter: Int): ByteArray {
        val n = ByteArray(12)
        System.arraycopy(prefix, 0, n, 0, PREFIX_LEN)
        n[8] = (counter ushr 24).toByte()
        n[9] = (counter ushr 16).toByte()
        n[10] = (counter ushr 8).toByte()
        n[11] = counter.toByte()
        return n
    }

    private fun cipher(mode: Int, key: SecretKeySpec, prefix: ByteArray, counter: Int, last: Boolean): Cipher {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(mode, key, GCMParameterSpec(128, nonce(prefix, counter)))
        c.updateAAD(byteArrayOf(if (last) 1 else 0))
        return c
    }

    fun encrypt(out: OutputStream, password: CharArray): OutputStream {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val prefix = ByteArray(PREFIX_LEN).also { SecureRandom().nextBytes(it) }
        out.write(MAGIC)
        out.write(salt)
        out.write(prefix)
        return EncryptingStream(out, key(password, salt), prefix)
    }

    fun decrypt(input: InputStream, password: CharArray): InputStream {
        val magic = ByteArray(MAGIC.size)
        readFully(input, magic)
        if (!magic.contentEquals(MAGIC)) throw IOException("Das ist keine Sicherungsdatei dieser App")
        val salt = ByteArray(SALT_LEN)
        val prefix = ByteArray(PREFIX_LEN)
        readFully(input, salt)
        readFully(input, prefix)
        return DecryptingStream(input, key(password, salt), prefix)
    }

    private fun readFully(input: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) throw EOFException("Datei zu kurz")
            off += n
        }
    }

    private class EncryptingStream(
        private val out: OutputStream,
        private val key: SecretKeySpec,
        private val prefix: ByteArray,
    ) : OutputStream() {
        private val buf = ByteArray(CHUNK)
        private var size = 0
        private var counter = 0
        private var closed = false

        private fun emit(last: Boolean) {
            val ct = cipher(Cipher.ENCRYPT_MODE, key, prefix, counter++, last).doFinal(buf, 0, size)
            out.write(byteArrayOf((ct.size ushr 24).toByte(), (ct.size ushr 16).toByte(), (ct.size ushr 8).toByte(), ct.size.toByte()))
            out.write(ct)
            size = 0
        }

        override fun write(b: Int) {
            write(byteArrayOf(b.toByte()), 0, 1)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            var o = off
            var left = len
            while (left > 0) {
                if (size == CHUNK) emit(false)
                val n = minOf(left, CHUNK - size)
                System.arraycopy(b, o, buf, size, n)
                size += n
                o += n
                left -= n
            }
        }

        override fun flush() = out.flush()

        override fun close() {
            if (closed) return
            closed = true
            emit(true)
            out.flush()
            out.close()
        }
    }

    private class DecryptingStream(
        private val input: InputStream,
        private val key: SecretKeySpec,
        private val prefix: ByteArray,
    ) : InputStream() {
        private var buf = ByteArray(0)
        private var pos = 0
        private var counter = 0
        private var done = false
        private var nextLen = readLen()

        private fun readLen(): Int {
            val first = input.read()
            if (first < 0) return -1
            val b = ByteArray(3)
            readFully(input, b)
            return (first shl 24) or ((b[0].toInt() and 0xFF) shl 16) or ((b[1].toInt() and 0xFF) shl 8) or (b[2].toInt() and 0xFF)
        }

        private fun fill(): Boolean {
            if (done) return false
            if (nextLen < 0) {
                // Kein Block mehr, aber der letzte wurde nie als solcher gelesen: Datei abgeschnitten
                done = true
                if (counter == 0) throw WrongPasswordException()
                return false
            }
            if (nextLen > CHUNK + 64) throw WrongPasswordException()
            val ct = ByteArray(nextLen)
            readFully(input, ct)
            nextLen = readLen()
            val last = nextLen < 0
            buf = try {
                cipher(Cipher.DECRYPT_MODE, key, prefix, counter++, last).doFinal(ct)
            } catch (_: GeneralSecurityException) {
                throw WrongPasswordException()
            }
            pos = 0
            if (last) done = true
            return true
        }

        override fun read(): Int {
            val one = ByteArray(1)
            val n = read(one, 0, 1)
            return if (n < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (pos >= buf.size) {
                if (!fill()) return -1
            }
            val n = minOf(len, buf.size - pos)
            System.arraycopy(buf, pos, b, off, n)
            pos += n
            return n
        }

        override fun close() = input.close()
    }
}
