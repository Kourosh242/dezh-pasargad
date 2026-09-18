package com.pasargad.dezh.backup

import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val codec = BackupCodec(SecureRandom(), kdfIterations = TEST_ITERATIONS)
    private val passphrase = "پارک شب ۱۴۰۴!x".toCharArray()

    private fun samplePayload(entryCount: Int = 3) = BackupPayload(
        exportedAtEpochMs = 1_760_000_000_000L,
        entries = (0 until entryCount).map { index ->
            BackupEntry(
                id = "id-$index",
                title = "بانک $index",
                username = "user$index",
                email = "user$index@example.com",
                password = "S3cr3t!$index${'$'}{index}#", // deliberately distinctive
                notes = "note $index",
                category = "Banking",
                favorite = index == 0,
                createdAtEpochMs = 1_760_000_000_000L + index,
                updatedAtEpochMs = 1_760_000_000_000L + index,
            )
        },
    )

    @Test
    fun `seal then open round-trips the payload`() {
        val payload = samplePayload()
        val bytes = codec.seal(payload, passphrase)
        val restored = codec.open(bytes, passphrase)
        assertEquals(payload, restored)
    }

    @Test
    fun `header is readable without the passphrase`() {
        val payload = samplePayload(7)
        val bytes = codec.seal(payload, passphrase)
        val header = codec.readHeader(bytes)
        assertEquals(BACKUP_VERSION_CURRENT, header.version)
        assertEquals(7, header.entryCount)
        assertEquals(payload.exportedAtEpochMs, header.createdAtEpochMs)
    }

    @Test
    fun `wrong passphrase is rejected as WRONG_PASSWORD`() {
        val bytes = codec.seal(samplePayload(), passphrase)
        val failure = runCatching { codec.open(bytes, "دیگری".toCharArray()) }.exceptionOrNull()
        assertTrue(failure is BackupParseException)
        assertEquals(ParseFailure.WRONG_PASSWORD, (failure as BackupParseException).reason)
    }

    @Test
    fun `tampered ciphertext fails the integrity check`() {
        val bytes = codec.seal(samplePayload(), passphrase).decodeToString()
        val envelope = decode(bytes)
        val flipped = mutateLastChar(envelope.ciphertext)
        val tampered = bytes.replace(envelope.ciphertext, flipped)
        val failure = runCatching { codec.open(tampered.encodeToByteArray(), passphrase) }.exceptionOrNull()
        assertEquals(ParseFailure.INTEGRITY_FAILURE, (failure as BackupParseException).reason)
    }

    @Test
    fun `tampered version is rejected before decryption`() {
        val bytes = codec.seal(samplePayload(), passphrase).decodeToString()
        val tampered = bytes.replace("\"version\":$BACKUP_VERSION_CURRENT", "\"version\":99")
        val failure = runCatching { codec.open(tampered.encodeToByteArray(), passphrase) }.exceptionOrNull()
        assertEquals(ParseFailure.UNSUPPORTED_VERSION, (failure as BackupParseException).reason)
    }

    @Test
    fun `tampered entryCount fails via AAD binding even with a matching checksum`() {
        val payload = samplePayload(2)
        val bytes = codec.seal(payload, passphrase).decodeToString()
        // The unkeyed header checksum cannot cover header fields, so a relabeled
        // count passes readHeader — but the AAD binds the count to the ciphertext,
        // so decryption fails authentication (indistinguishable from wrong password).
        val tampered = bytes.replace("\"entryCount\":2", "\"entryCount\":5")
        codec.readHeader(tampered.encodeToByteArray()) // does not throw: by design
        val failure = runCatching { codec.open(tampered.encodeToByteArray(), passphrase) }.exceptionOrNull()
        assertEquals(ParseFailure.WRONG_PASSWORD, (failure as BackupParseException).reason)
    }

    @Test
    fun `non-JSON input is INVALID_FORMAT`() {
        val failure = runCatching { codec.open("not a backup".encodeToByteArray(), passphrase) }.exceptionOrNull()
        assertEquals(ParseFailure.INVALID_FORMAT, (failure as BackupParseException).reason)
    }

    @Test
    fun `empty input is INVALID_FORMAT`() {
        val failure = runCatching { codec.open(ByteArray(0), passphrase) }.exceptionOrNull()
        assertEquals(ParseFailure.INVALID_FORMAT, (failure as BackupParseException).reason)
    }

    @Test
    fun `foreign format id is INVALID_FORMAT`() {
        val bytes = codec.seal(samplePayload(), passphrase).decodeToString()
        val tampered = bytes.replace(BACKUP_FORMAT_ID, "someone-else")
        val failure = runCatching { codec.open(tampered.encodeToByteArray(), passphrase) }.exceptionOrNull()
        assertEquals(ParseFailure.INVALID_FORMAT, (failure as BackupParseException).reason)
    }

    @Test
    fun `count mismatch inside payload is INCOMPLETE`() {
        // A file declaring 2 entries whose decrypted payload only holds 1 —
        // e.g. a torn write or a migration that dropped rows.
        val payload = samplePayload(1)
        val bytes = codec.sealWithDeclaredCount(payload, declaredEntryCount = 2, passphrase = passphrase)
        val failure = runCatching { codec.open(bytes, passphrase) }.exceptionOrNull()
        assertEquals(ParseFailure.INCOMPLETE, (failure as BackupParseException).reason)
    }

    @Test
    fun `credentials never appear as plaintext in the sealed bytes`() {
        val payload = samplePayload()
        val bytes = codec.seal(payload, passphrase)
        val text = bytes.decodeToString()
        payload.entries.forEach { entry ->
            assertFalse(text.contains(entry.password))
            assertFalse(text.contains(entry.title))
            assertFalse(text.contains(entry.email))
            assertFalse(text.contains(entry.username))
        }
        assertFalse(text.contains("password\":"))
    }

    @Test
    fun `iteration count travels with the file - migration friendliness`() {
        val fast = BackupCodec(SecureRandom(), kdfIterations = MIN_TEST_ITERATIONS)
        val bytes = fast.seal(samplePayload(), passphrase)
        val envelope = decode(bytes.decodeToString())
        assertEquals(MIN_TEST_ITERATIONS, envelope.kdf.iterations)
        assertEquals(fast.open(bytes, passphrase), fast.open(bytes, passphrase))
        // A codec with different default iterations still opens it (params read from file).
        assertEquals(fast.open(bytes, passphrase), codec.open(bytes, passphrase))
    }

    @Test
    fun `different random salt produces different ciphertexts`() {
        val first = codec.seal(samplePayload(), passphrase)
        val second = codec.seal(samplePayload(), passphrase)
        assertFalse(first.contentEquals(second))
    }

    // --- helpers -----------------------------------------------------------------

    private fun decode(text: String) = strictJson().decodeFromString(
        BackupEnvelope.serializer(),
        text,
    )

    private fun strictJson() = kotlinx.serialization.json.Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    private fun mutateLastChar(value: String): String {
        val last = value.last()
        val replacement = if (last == 'A') 'B' else 'A'
        return value.dropLast(1) + replacement
    }

    private companion object {
        const val TEST_ITERATIONS = 20_000
        const val MIN_TEST_ITERATIONS = 10_000
    }
}
