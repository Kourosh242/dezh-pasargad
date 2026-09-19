package com.pasargad.dezh.security

import java.io.File

/**
 * App-private file storage for the security envelope:
 *  - `keywrap.v1` — password-wrapped DEK (versioned [com.pasargad.dezh.cryptography.KeyWrapContainer])
 *  - `meta.v1`    — Keystore-encrypted security metadata
 * Writes are atomic (tmp + rename); nothing is ever written to shared storage.
 */
class VaultSecurityStorage(private val baseDir: File) {

    private val keyWrapFile = File(baseDir, "keywrap.v1")
    private val metaFile = File(baseDir, "meta.v1")

    init {
        baseDir.mkdirs()
    }

    fun hasKeyWrap(): Boolean = keyWrapFile.isFile

    fun readKeyWrap(): ByteArray = keyWrapFile.readBytes()

    fun writeKeyWrap(bytes: ByteArray) = atomicWrite(keyWrapFile, bytes)

    fun readMeta(): ByteArray? = if (metaFile.isFile) metaFile.readBytes() else null

    fun writeMeta(bytes: ByteArray) = atomicWrite(metaFile, bytes)

    /** Deletes every security file (used on failed setup to avoid orphaned partial state). */
    fun wipeAll() {
        keyWrapFile.delete()
        metaFile.delete()
        keyWrapFile.deleteRecursively()
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val tmp = File(baseDir, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "Cannot persist security file" }
        }
    }
}
