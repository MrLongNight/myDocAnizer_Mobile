package com.example.service

/**
 * Minimal boundary for issuing a short-lived lease on the unlocked vault DEK.
 * Implementations must provide a temporary DEK copy and wipe it after the callback.
 */
internal interface VaultDekLeaseSource {
    fun currentVaultId(): String?

    fun <T> withUnlockedKey(operation: (ByteArray) -> T): T?
}

/**
 * Derives purpose-separated keys only inside the active vault-key lease.
 *
 * The derived key is valid only for the callback duration and is zeroed in a finally block.
 * This provider intentionally does not persist keys or migrate any existing data format.
 */
internal class VaultKeyProvider(
    private val leaseSource: VaultDekLeaseSource
) {
    fun <T> withKey(
        purpose: CryptoKeyDerivation.Purpose,
        operation: (ByteArray) -> T
    ): T? {
        val vaultId = leaseSource.currentVaultId()
            ?.takeIf { it.isNotBlank() && it.length <= 256 }
            ?: return null

        return leaseSource.withUnlockedKey { vaultDek ->
            val purposeKey = CryptoKeyDerivation.deriveVaultSubkey(vaultDek, vaultId, purpose)
            try {
                operation(purposeKey)
            } finally {
                purposeKey.fill(0)
            }
        }
    }

    companion object {
        fun fromVaultManager(manager: VaultSecurityManager): VaultKeyProvider =
            VaultKeyProvider(
                object : VaultDekLeaseSource {
                    override fun currentVaultId(): String? =
                        manager.vaultHeader.value?.vaultId

                    override fun <T> withUnlockedKey(operation: (ByteArray) -> T): T? =
                        manager.withUnlockedKey(operation)
                }
            )
    }
}
