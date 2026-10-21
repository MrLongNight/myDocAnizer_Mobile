package com.example.service

import android.util.Base64
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * AEAD operations for VaultHeader v2. Header metadata is authenticated as AAD;
 * callers must persist the returned header as one atomic value.
 */
object VaultHeaderCryptoV2 {
    private const val KEY_BYTES = 32
    private const val DEK_BYTES = 32
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val ALGORITHM = "AES-256-GCM-Argon2id"
    private const val MIN_ITERATIONS = 2
    private const val MAX_ITERATIONS = 10
    private const val MIN_MEMORY_KB = 19456
    private const val MAX_MEMORY_KB = 262144
    private val random = SecureRandom()
    private val kdfLock = Any()

    fun createHeader(
        vaultId: String,
        createdAt: Long,
        password: CharArray,
        dek: ByteArray,
        securityTier: String = "ONE_FACTOR",
        factorType: String = "PASSWORD",
        keyFileToken: CharArray? = null,
        autoLockMinutes: Int = 5,
        iterations: Int = MIN_ITERATIONS,
        memoryKb: Int = MIN_MEMORY_KB
    ): VaultHeaderV2 {
        require(password.isNotEmpty()) { "Password cannot be empty" }
        require(dek.size == DEK_BYTES) { "DEK must be exactly 256 bits" }
        require(vaultId.length in 8..64 && vaultId.startsWith("VAULT-") &&
            vaultId.all { it.isLetterOrDigit() || it == '-' || it == '_' }) { "Invalid vault ID" }
        require(createdAt > 0L) { "Invalid creation timestamp" }
        validateKdf(iterations, memoryKb)
        validateFactorConfiguration(securityTier, factorType, keyFileToken)
        require(autoLockMinutes in 0..1440) { "Invalid auto-lock duration" }

        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = VaultHeaderV2(
            version = 2,
            algorithmId = ALGORITHM,
            vaultId = vaultId,
            createdAt = createdAt,
            argon2Iterations = iterations,
            argon2MemoryKb = memoryKb,
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            tagLengthBits = TAG_BITS,
            encryptedDekBase64 = "",
            securityTier = securityTier,
            factorType = factorType,
            autoLockMinutes = autoLockMinutes
        )
        val kdfInput = buildKdfInput(password, keyFileToken, securityTier, factorType)
        val kek = try {
            deriveKey(kdfInput, salt, iterations, memoryKb)
        } finally {
            kdfInput.fill('\u0000')
        }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(aad(header))
            val encrypted = cipher.doFinal(dek)
            return header.copy(encryptedDekBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .also { check(VaultHeaderParserV2.parse(it.toJson()) != null) { "Generated vault header failed validation" } }
        } finally {
            kek.fill(0)
            salt.fill(0)
            iv.fill(0)
        }
    }

    fun unwrapDek(
        header: VaultHeaderV2,
        password: CharArray,
        keyFileToken: CharArray? = null
    ): ByteArray {
        require(password.isNotEmpty()) { "Password cannot be empty" }
        require(VaultHeaderParserV2.parse(header.toJson()) != null) { "Invalid or unsupported vault header" }
        validateFactorConfiguration(header.securityTier, header.factorType, keyFileToken)
        val salt = decode(header.saltBase64, SALT_BYTES, "salt")
        val iv = decode(header.ivBase64, IV_BYTES, "IV")
        val encrypted = decode(header.encryptedDekBase64, DEK_BYTES + TAG_BITS / 8, "encrypted DEK")
        val kdfInput = buildKdfInput(password, keyFileToken, header.securityTier, header.factorType)
        val kek = try {
            deriveKey(kdfInput, salt, header.argon2Iterations, header.argon2MemoryKb)
        } finally {
            kdfInput.fill('\u0000')
        }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(aad(header))
            val dek = cipher.doFinal(encrypted)
            if (dek.size != DEK_BYTES) {
                dek.fill(0)
                throw SecurityException("Invalid recovered DEK length")
            }
            return dek
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            throw SecurityException("Vault header authentication failed", e)
        } finally {
            kek.fill(0)
            salt.fill(0)
            iv.fill(0)
            encrypted.fill(0)
        }
    }

    /**
     * v2 supports only factor combinations whose cryptographic behavior is implemented here.
     * Unsupported declarations fail closed rather than being treated as password-only protection.
     */
    private fun validateFactorConfiguration(
        securityTier: String,
        factorType: String,
        keyFileToken: CharArray?
    ) {
        when (securityTier) {
            "ONE_FACTOR" -> {
                require(factorType == "PASSWORD") { "v2 one-factor mode requires PASSWORD" }
                require(keyFileToken == null) { "A key file cannot be silently ignored in one-factor mode" }
            }
            "TWO_FACTOR" -> {
                require(factorType == "PASSWORD_AND_KEYFILE") {
                    "v2 two-factor mode supports only PASSWORD_AND_KEYFILE"
                }
                require(isValidKeyFileToken(keyFileToken)) {
                    "v2 two-factor mode requires a valid 256-bit key-file token"
                }
            }
            else -> throw IllegalArgumentException("Unsupported v2 security tier")
        }
    }

    private fun isValidKeyFileToken(token: CharArray?): Boolean =
        token != null && token.size == 64 && token.all {
            it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F'
        }

    private fun buildKdfInput(
        password: CharArray,
        keyFileToken: CharArray?,
        securityTier: String,
        factorType: String
    ): CharArray {
        validateFactorConfiguration(securityTier, factorType, keyFileToken)
        if (securityTier == "ONE_FACTOR") return password.copyOf()

        val token = requireNotNull(keyFileToken)
        return CharArray(password.size + 1 + token.size).also { combined ->
            password.copyInto(combined, destinationOffset = 0)
            combined[password.size] = ':'
            token.copyInto(combined, destinationOffset = password.size + 1)
        }
    }

    private fun validateKdf(iterations: Int, memoryKb: Int) {
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "Unsupported Argon2id iteration count" }
        require(memoryKb in MIN_MEMORY_KB..MAX_MEMORY_KB) { "Unsupported Argon2id memory setting" }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int, memoryKb: Int): ByteArray {
        validateKdf(iterations, memoryKb)
        require(salt.size == SALT_BYTES) { "Invalid Argon2id salt length" }
        val encoded = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(password))
        val passBytes = ByteArray(encoded.remaining()).also { encoded.get(it) }
        if (encoded.hasArray()) encoded.array().fill(0)
        val result = ByteArray(KEY_BYTES)
        try {
            synchronized(kdfLock) {
                val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .withIterations(iterations)
                    .withMemoryAsKB(memoryKb)
                    .withParallelism(1)
                    .withSalt(salt)
                    .build()
                Argon2BytesGenerator().apply { init(params) }.generateBytes(passBytes, result, 0, result.size)
            }
            return result
        } catch (e: Exception) {
            result.fill(0)
            throw SecurityException("Vault key derivation failed", e)
        } finally {
            passBytes.fill(0)
        }
    }

    private fun aad(header: VaultHeaderV2): ByteArray = listOf(
        "myDocAnizer-VaultHeader-v2",
        "version=${header.version}",
        "algorithmId=${header.algorithmId}",
        "vaultId=${header.vaultId}",
        "createdAt=${header.createdAt}",
        "argon2Iterations=${header.argon2Iterations}",
        "argon2MemoryKb=${header.argon2MemoryKb}",
        "saltBase64=${header.saltBase64}",
        "tagLengthBits=${header.tagLengthBits}",
        "securityTier=${header.securityTier}",
        "factorType=${header.factorType}",
        "autoLockMinutes=${header.autoLockMinutes}"
    ).joinToString("|").toByteArray(StandardCharsets.UTF_8)

    private fun decode(value: String, expectedSize: Int, label: String): ByteArray {
        val bytes = try { Base64.decode(value, Base64.NO_WRAP) }
        catch (e: Exception) { throw IllegalArgumentException("Invalid $label encoding", e) }
        require(bytes.size == expectedSize) { "Invalid $label length" }
        return bytes
    }
}
