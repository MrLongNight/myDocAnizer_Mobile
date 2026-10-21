package com.example.service

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters

/**
 * HKDF-SHA-256 helpers for deriving purpose-separated keys from an already-unlocked vault DEK.
 *
 * This class does not persist keys, unlock a vault, or replace the current document format.
 * Callers own and must clear their input DEK copy and the returned key bytes when finished.
 */
internal object CryptoKeyDerivation {
    private const val HASH_LENGTH_BYTES = 32
    private const val DOMAIN_PREFIX = "myDocAnizer.crypto.v1"
    private const val VAULT_DEK_BYTES = 32

    enum class Purpose(val wireName: String) {
        DATABASE("sqlcipher-database"),
        DOCUMENT_STREAM("document-stream"),
        SETTINGS("settings"),
        BACKUP_CONTAINER("backup-container")
    }

    /** RFC 5869 HKDF extract-and-expand with SHA-256. */
    fun derive(ikm: ByteArray, salt: ByteArray, info: ByteArray, outputLengthBytes: Int = HASH_LENGTH_BYTES): ByteArray {
        require(ikm.isNotEmpty()) { "Input key material must not be empty" }
        require(salt.isNotEmpty()) { "HKDF salt must not be empty" }
        require(info.isNotEmpty()) { "HKDF context must not be empty" }
        require(outputLengthBytes in 1..(255 * HASH_LENGTH_BYTES)) { "Invalid HKDF output length" }

        val generator = HKDFBytesGenerator(SHA256Digest())
        generator.init(HKDFParameters(ikm, salt, info))
        return ByteArray(outputLengthBytes).also { output ->
            generator.generateBytes(output, 0, output.size)
        }
    }

    /**
     * Derives a deterministic subkey scoped to both a vault identity and one explicit purpose.
     * This is not the final database-key wrapping or portable-recovery implementation.
     */
    fun deriveVaultSubkey(vaultDek: ByteArray, vaultId: String, purpose: Purpose): ByteArray {
        require(vaultDek.size == VAULT_DEK_BYTES) { "Vault DEK must be exactly 256 bits" }
        require(vaultId.isNotBlank() && vaultId.length <= 256) { "Invalid vault ID" }

        val salt = "\${DOMAIN_PREFIX}:salt:\${vaultId}".toByteArray(Charsets.UTF_8)
        val info = "\${DOMAIN_PREFIX}:subkey:\${purpose.wireName}".toByteArray(Charsets.UTF_8)
        return try {
            derive(vaultDek, salt, info, HASH_LENGTH_BYTES)
        } finally {
            salt.fill(0)
            info.fill(0)
        }
    }
}
