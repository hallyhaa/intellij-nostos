package org.babelserver.intellijnostos

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The new-issue URL builder. We only test the pure assembly/encoding here; the
 * surrounding submit() flow opens a browser and reads platform services, so it
 * is exercised manually rather than in a unit test.
 */
class NostosErrorReportSubmitterTest {

    @Test
    fun pointsAtTheProjectIssueTracker() {
        val url = NostosErrorReportSubmitter.buildIssueUrl("title", "body")
        assertTrue(
            url.startsWith("https://github.com/hallyhaa/intellij-nostos/issues/new?"),
            "unexpected base: $url",
        )
        assertTrue(url.contains("labels=bug"), "missing bug label: $url")
    }

    @Test
    fun encodesSpacesAndSpecialCharacters() {
        val url = NostosErrorReportSubmitter.buildIssueUrl(
            "NPE at foo & bar",
            "line one\nline two",
        )
        // No raw spaces or newlines may survive into the query string.
        assertFalse(url.contains(" "), "raw space leaked: $url")
        assertFalse(url.contains("\n"), "raw newline leaked: $url")
        // '&' inside the title must be percent-encoded, not left as a separator.
        assertTrue(url.contains("title=NPE+at+foo+%26+bar"), "title not encoded: $url")
        assertTrue(url.contains("body=line+one%0Aline+two"), "body not encoded: $url")
    }

    @Test
    fun titleAndBodyAreSeparateParameters() {
        val url = NostosErrorReportSubmitter.buildIssueUrl("T", "B")
        assertEquals(
            "https://github.com/hallyhaa/intellij-nostos/issues/new?labels=bug&title=T&body=B",
            url,
        )
    }
}
