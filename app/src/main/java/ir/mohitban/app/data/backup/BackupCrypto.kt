package ir.mohitban.app.data.backup

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.security.SecureRandom
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The backup is encrypted and no password was given. */
class BackupPasswordRequired : IOException("password required")

/** The password is wrong, or the file was damaged or changed. */
class BackupWrongPassword : IOException("wrong password or damaged file")

/**
 * Optional password protection for the backup file. Layout:
 *   "MHYBK1" | iterations (4) | salt (16) | base nonce (12) | chunks…
 * Every chunk is [length (4)] + AES-256-GCM(ciphertext + tag) of up to 64 KB, with its own nonce
 * (base nonce xor chunk number) and a flag as authenticated data saying whether it is the last chunk,
 * so a cut-off, reordered or changed file is refused. The key comes from the password with PBKDF2.
 * Chunks keep memory small even when the backup holds hundreds of pictures.
 */
object BackupCrypto {
    private val MAGIC = "MHYBK1".toByteArray(Charsets.US_ASCII)
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val CHUNK = 64 * 1024
    private const val TAG_LEN = 16
    private const val ITERATIONS = 150_000
    private val random = SecureRandom()

    /** True when [head] (the first bytes of a file) starts like an encrypted backup. */
    fun looksEncrypted(head: ByteArray): Boolean = head.size >= MAGIC.size && MAGIC.indices.all { head[it] == MAGIC[it] }

    /** How many bytes [looksEncrypted] needs. */
    val headerProbeSize: Int get() = MAGIC.size

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun nonce(base: ByteArray, counter: Long): ByteArray {
        val n = base.copyOf()
        for (i in 0 until 8) n[IV_LEN - 1 - i] = (n[IV_LEN - 1 - i].toInt() xor ((counter ushr (8 * i)).toInt() and 0xFF)).toByte()
        return n
    }

    private fun writeInt(out: OutputStream, v: Int) {
        out.write(v ushr 24); out.write(v ushr 16); out.write(v ushr 8); out.write(v)
    }

    private fun readFully(src: InputStream, n: Int): ByteArray {
        val b = ByteArray(n)
        var got = 0
        while (got < n) {
            val r = src.read(b, got, n - got)
            if (r < 0) throw IOException("truncated backup")
            got += r
        }
        return b
    }

    /** Reads a 4-byte number, or null when the stream ends right here. */
    private fun readIntOrEnd(src: InputStream): Int? {
        val first = src.read()
        if (first < 0) return null
        val rest = readFully(src, 3)
        return (first shl 24) or ((rest[0].toInt() and 0xFF) shl 16) or ((rest[1].toInt() and 0xFF) shl 8) or (rest[2].toInt() and 0xFF)
    }

    /** Wraps [out] so that everything written to it is encrypted. Close the result to finish the file. */
    fun encrypt(out: OutputStream, password: String): OutputStream {
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        out.write(MAGIC)
        writeInt(out, ITERATIONS)
        out.write(salt)
        out.write(iv)
        return EncryptingStream(out, key(password.toCharArray(), salt, ITERATIONS), iv)
    }

    /**
     * Opens [input] for reading. A plain (not encrypted) file is passed through unchanged; an encrypted one
     * needs [password] ([BackupPasswordRequired] otherwise). A wrong password shows up as [BackupWrongPassword]
     * on the first read.
     */
    fun open(input: InputStream, password: String?): InputStream {
        val pb = PushbackInputStream(input, MAGIC.size)
        val head = ByteArray(MAGIC.size)
        var got = 0
        while (got < head.size) {
            val r = pb.read(head, got, head.size - got)
            if (r < 0) break
            got += r
        }
        if (got != head.size || !looksEncrypted(head)) {
            if (got > 0) pb.unread(head, 0, got)
            return pb
        }
        if (password.isNullOrEmpty()) throw BackupPasswordRequired()
        val iterations = readIntOrEnd(pb) ?: throw IOException("truncated backup")
        if (iterations !in 10_000..2_000_000) throw IOException("bad backup header")
        val salt = readFully(pb, SALT_LEN)
        val iv = readFully(pb, IV_LEN)
        return DecryptingStream(pb, key(password.toCharArray(), salt, iterations), iv)
    }

    private class EncryptingStream(private val out: OutputStream, private val key: SecretKeySpec, private val baseIv: ByteArray) : OutputStream() {
        private val buf = ByteArray(CHUNK)
        private val one = ByteArray(1)
        private var n = 0
        private var counter = 0L
        private var closed = false

        override fun write(b: Int) {
            one[0] = b.toByte()
            write(one, 0, 1)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            var o = off
            var l = len
            while (l > 0) {
                // a full chunk is sealed only when more data follows, so the last one can be marked
                if (n == CHUNK) seal(last = false)
                val t = minOf(l, CHUNK - n)
                System.arraycopy(b, o, buf, n, t)
                n += t; o += t; l -= t
            }
        }

        private fun seal(last: Boolean) {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LEN * 8, nonce(baseIv, counter++)))
            c.updateAAD(byteArrayOf(if (last) 1 else 0))
            val ct = c.doFinal(buf, 0, n)
            writeInt(out, ct.size)
            out.write(ct)
            n = 0
        }

        override fun flush() { out.flush() }

        override fun close() {
            if (closed) return
            closed = true
            seal(last = true)
            out.flush()
            out.close()
        }
    }

    private class DecryptingStream(private val src: PushbackInputStream, private val key: SecretKeySpec, private val baseIv: ByteArray) : InputStream() {
        private var buf = ByteArray(0)
        private var pos = 0
        private var counter = 0L
        private var done = false

        override fun read(): Int {
            if (!fill()) return -1
            return buf[pos++].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (!fill()) return -1
            val t = minOf(len, buf.size - pos)
            System.arraycopy(buf, pos, b, off, t)
            pos += t
            return t
        }

        private fun fill(): Boolean {
            while (pos >= buf.size) {
                if (done) return false
                // the file must end with a chunk marked as the last one; ending sooner means it was cut off
                val len = readIntOrEnd(src) ?: throw IOException("truncated backup")
                if (len < TAG_LEN || len > CHUNK + TAG_LEN) throw IOException("bad backup chunk")
                val ct = readFully(src, len)
                val next = src.read()
                val last = next < 0
                if (!last) src.unread(next)
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LEN * 8, nonce(baseIv, counter++)))
                c.updateAAD(byteArrayOf(if (last) 1 else 0))
                buf = try { c.doFinal(ct) } catch (e: BadPaddingException) { throw BackupWrongPassword() }
                pos = 0
                if (last) done = true
            }
            return true
        }

        override fun close() { src.close() }
    }
}
