package org.babelserver.intellijnostos

import com.intellij.openapi.util.Version
import org.babelserver.intellijnostos.lsp.NostosLspServerManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NostosVersionCheckTest {

    @Test
    fun testParseFullVersionString() {
        val v = NostosLspServerManager.parseNostosVersion("nostos 0.2.18")
        assertEquals(Version(0, 2, 18), v)
    }

    @Test
    fun testParseVersionNumberOnly() {
        val v = NostosLspServerManager.parseNostosVersion("0.2.18")
        assertEquals(Version(0, 2, 18), v)
    }

    @Test
    fun testParseWithWhitespace() {
        val v = NostosLspServerManager.parseNostosVersion("  nostos  1.0.0  ")
        assertEquals(Version(1, 0, 0), v)
    }

    @Test
    fun testParseInvalidReturnsNull() {
        assertNull(NostosLspServerManager.parseNostosVersion("not a version"))
    }

    @Test
    fun testParseEmptyReturnsNull() {
        assertNull(NostosLspServerManager.parseNostosVersion(""))
    }

    @Test
    fun testMinVersionAccepted() {
        val v = NostosLspServerManager.parseNostosVersion("nostos 0.2.18")!!
        assertFalse(v < NostosLspServerManager.MIN_VERSION)
    }

    @Test
    fun testAboveMinVersionAccepted() {
        val v = NostosLspServerManager.parseNostosVersion("nostos 0.3.0")!!
        assertFalse(v < NostosLspServerManager.MIN_VERSION)
    }

    @Test
    fun testBelowMinVersionRejected() {
        val v = NostosLspServerManager.parseNostosVersion("nostos 0.2.17")!!
        assertTrue(v < NostosLspServerManager.MIN_VERSION)
    }

    @Test
    fun testMajorVersionAboveAccepted() {
        val v = NostosLspServerManager.parseNostosVersion("nostos 1.0.0")!!
        assertFalse(v < NostosLspServerManager.MIN_VERSION)
    }

    @Test
    fun testOldMajorVersionRejected() {
        val v = NostosLspServerManager.parseNostosVersion("nostos 0.1.99")!!
        assertTrue(v < NostosLspServerManager.MIN_VERSION)
    }
}
