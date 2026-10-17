package com.example

import com.example.service.P2pSyncSecurityService
import com.example.service.P2pSyncServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class P2pSyncSecurityTest {

    @Test
    fun testPairingPinGeneration() {
        val pin1 = P2pSyncSecurityService.generatePairingPin()
        val pin2 = P2pSyncSecurityService.generatePairingPin()

        assertNotNull(pin1)
        assertNotNull(pin2)
        assertEquals(6, pin1.length)
        assertEquals(6, pin2.length)
        assertTrue(pin1.all { it.isDigit() })
    }

    @Test
    fun testSharedSecretAndTokenEquality() {
        val secret1 = P2pSyncSecurityService.generateSharedSecret()
        val secret2 = P2pSyncSecurityService.generateSharedSecret()

        assertNotNull(secret1)
        assertNotNull(secret2)
        assertNotEquals(secret1, secret2)

        assertTrue(P2pSyncSecurityService.isTokenEqual(secret1, secret1))
        assertFalse(P2pSyncSecurityService.isTokenEqual(secret1, secret2))
        assertFalse(P2pSyncSecurityService.isTokenEqual(secret1, ""))
    }

    @Test
    fun testLocalIpValidationRules() {
        // Private RFC 1918 networks and loopback should be allowed
        assertTrue(P2pSyncServer.isLocalIp("127.0.0.1"))
        assertTrue(P2pSyncServer.isLocalIp("::1"))
        assertTrue(P2pSyncServer.isLocalIp("192.168.1.50"))
        assertTrue(P2pSyncServer.isLocalIp("10.0.0.15"))
        assertTrue(P2pSyncServer.isLocalIp("172.16.5.10"))
        assertTrue(P2pSyncServer.isLocalIp("172.31.255.254"))

        // Public or outside RFC 1918 networks should be blocked
        assertFalse(P2pSyncServer.isLocalIp("8.8.8.8"))
        assertFalse(P2pSyncServer.isLocalIp("1.1.1.1"))
        assertFalse(P2pSyncServer.isLocalIp("172.15.0.1")) // Outside 172.16 - 172.31 range
        assertFalse(P2pSyncServer.isLocalIp("172.32.0.1")) // Outside 172.16 - 172.31 range
        assertFalse(P2pSyncServer.isLocalIp("192.169.1.1"))
    }
}
