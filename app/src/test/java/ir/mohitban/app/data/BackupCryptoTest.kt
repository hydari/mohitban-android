package ir.mohitban.app.data

import ir.mohitban.app.data.backup.BackupCrypto
import ir.mohitban.app.data.backup.BackupPasswordRequired
import ir.mohitban.app.data.backup.BackupWrongPassword
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class BackupCryptoTest {
    private fun encrypt(data: ByteArray, password: String): ByteArray {
        val bos = ByteArrayOutputStream()
        BackupCrypto.encrypt(bos, password).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun decrypt(file: ByteArray, password: String?): ByteArray =
        BackupCrypto.open(ByteArrayInputStream(file), password).use { it.readBytes() }

    @Test fun roundTripSmall() {
        val data = "اطلاعات محیط‌بان".toByteArray()
        assertArrayEquals(data, decrypt(encrypt(data, "pass-1234"), "pass-1234"))
    }

    @Test fun roundTripSeveralChunks() {
        val data = ByteArray(200_000) { (it * 31 + 7).toByte() }
        val file = encrypt(data, "long password for a long file")
        assertTrue(BackupCrypto.looksEncrypted(file))
        assertArrayEquals(data, decrypt(file, "long password for a long file"))
    }

    @Test fun roundTripEmpty() {
        assertArrayEquals(ByteArray(0), decrypt(encrypt(ByteArray(0), "pass-1234"), "pass-1234"))
    }

    @Test fun exactChunkMultiple() {
        val data = ByteArray(64 * 1024 * 2) { it.toByte() }
        assertArrayEquals(data, decrypt(encrypt(data, "pass-1234"), "pass-1234"))
    }

    @Test fun wrongPasswordIsRefused() {
        val file = encrypt("secret".toByteArray(), "right-password")
        assertThrows(BackupWrongPassword::class.java) { decrypt(file, "wrong-password") }
    }

    @Test fun missingPasswordIsReported() {
        val file = encrypt("secret".toByteArray(), "right-password")
        assertThrows(BackupPasswordRequired::class.java) { BackupCrypto.open(ByteArrayInputStream(file), null) }
        assertThrows(BackupPasswordRequired::class.java) { BackupCrypto.open(ByteArrayInputStream(file), "") }
    }

    @Test fun plainFilePassesThroughUnchanged() {
        val zipLike = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        assertFalse(BackupCrypto.looksEncrypted(zipLike))
        assertArrayEquals(zipLike, decrypt(zipLike, null))
        assertArrayEquals(ByteArray(0), decrypt(ByteArray(0), null))
    }

    @Test fun changedByteIsDetected() {
        val file = encrypt(ByteArray(10_000) { it.toByte() }, "pass-1234")
        file[file.size / 2] = (file[file.size / 2].toInt() xor 0x55).toByte()
        assertThrows(IOException::class.java) { decrypt(file, "pass-1234") }
    }

    @Test fun cutOffFileIsDetected() {
        val file = encrypt(ByteArray(150_000) { it.toByte() }, "pass-1234")
        assertThrows(IOException::class.java) { decrypt(file.copyOf(file.size - 100), "pass-1234") }
        assertThrows(IOException::class.java) { decrypt(file.copyOf(40), "pass-1234") }
    }

    @Test fun everyEncryptionIsDifferent() {
        val data = "same".toByteArray()
        assertFalse(encrypt(data, "pass-1234").contentEquals(encrypt(data, "pass-1234")))
    }
}
