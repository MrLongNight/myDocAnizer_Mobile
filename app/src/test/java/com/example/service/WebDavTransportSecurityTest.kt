package com.example.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavTransportSecurityTest {

    @Test
    fun `bare host defaults to HTTPS`() {
        assertEquals("https://cloud.example.test/remote.php/dav", WebDavClient.sanitizeUrl("cloud.example.test/remote.php/dav"))
    }

    @Test
    fun `explicit HTTP is rejected rather than silently downgraded`() {
        assertThrows(IllegalArgumentException::class.java) {
            WebDavClient.sanitizeUrl("http://cloud.example.test/remote.php/dav")
        }
    }

    @Test
    fun `unsupported schemes are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            WebDavClient.sanitizeUrl("ftp://cloud.example.test/dav")
        }
    }

    @Test
    fun `credentials cannot be embedded in URL`() {
        assertThrows(IllegalArgumentException::class.java) {
            WebDavClient.sanitizeUrl("https://user:secret@cloud.example.test/dav")
        }
    }

    @Test
    fun `query strings and fragments are rejected from base URL`() {
        assertThrows(IllegalArgumentException::class.java) {
            WebDavClient.sanitizeUrl("https://cloud.example.test/dav?token=secret")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebDavClient.sanitizeUrl("https://cloud.example.test/dav#fragment")
        }
    }

    @Test
    fun `child names stay within a single encoded path segment`() {
        val child = WebDavClient.buildChildUrl("https://cloud.example.test/dav/", "invoice #1.enc")
        assertTrue(child.startsWith("https://cloud.example.test/dav/"))
        assertTrue(child.endsWith("invoice%20%231.enc"))
        assertThrows(IllegalArgumentException::class.java) {
            WebDavClient.buildChildUrl("https://cloud.example.test/dav/", "../outside.enc")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebDavClient.buildChildUrl("https://cloud.example.test/dav/", "folder/file.enc")
        }
    }

    @Test
    fun `directory URLs have a trailing slash`() {
        assertTrue(
            WebDavClient.buildChildUrl("https://cloud.example.test/dav", "backups", directory = true)
                .endsWith("/backups/")
        )
    }
}
