package com.example.service

import org.junit.Assert.assertEquals
import org.junit.Test

class P2pWebSessionHeaderPolicyTest {
    @Test
    fun `session token is accepted only from explicit header map`() {
        assertEquals("session-value", sessionTokenFromHeaders(mapOf("x-session-token" to "session-value")))
        assertEquals("", sessionTokenFromHeaders(emptyMap()))
        assertEquals("", sessionTokenFromHeaders(mapOf("authorization" to "Bearer session-value")))
    }
}
