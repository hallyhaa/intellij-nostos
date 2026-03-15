package org.babelserver.intellijnostos

import com.intellij.openapi.util.Version
import junit.framework.TestCase
import org.babelserver.intellijnostos.lsp.NostosLspServerManager

class NostosVersionCheckTest : TestCase() {

    fun testParseFullVersionString() {
        val v = NostosLspServerManager.parseNostosVersion("nostos 0.2.18")
        assertEquals(Version(0, 2, 18), v)
    }

    fun testParseVersionNumberOnly() {
        val v = NostosLspServerManager.parseNostosVersion("0.2.18")
        assertEquals(Version(0, 2, 18), v)
    }

    fun testParseWithWhitespace() {
        val v = NostosLspServerManager.parseNostosVersion("  nostos  1.0.0  ")
        assertEquals(Version(1, 0, 0), v)
    }

    fun testParseInvalidReturnsNull() {
        assertNull(NostosLspServerManager.parseNostosVersion("not a version"))
    }

    fun testParseEmptyReturnsNull() {
        assertNull(NostosLspServerManager.parseNostosVersion(""))
    }

    fun testMinVersionAccepted() {
        val v = NostosLspServerManager.parseNostosVersion("nostos 0.2.18")!!
        assertFalse(v < NostosLspServerManager.MIN_VERSION)
    }

    fun testAboveMinVersionAccepted() {
        val v = NostosLspServerManager.parseNostosVersion("nostos 0.3.0")!!
        assertFalse(v < NostosLspServerManager.MIN_VERSION)
    }

    fun testBelowMinVersionRejected() {
        val v = NostosLspServerManager.parseNostosVersion("nostos 0.2.17")!!
        assertTrue(v < NostosLspServerManager.MIN_VERSION)
    }

    fun testMajorVersionAboveAccepted() {
        val v = NostosLspServerManager.parseNostosVersion("nostos 1.0.0")!!
        assertFalse(v < NostosLspServerManager.MIN_VERSION)
    }

    fun testOldMajorVersionRejected() {
        val v = NostosLspServerManager.parseNostosVersion("nostos 0.1.99")!!
        assertTrue(v < NostosLspServerManager.MIN_VERSION)
    }
}
