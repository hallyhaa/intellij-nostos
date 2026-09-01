package org.babelserver.intellijnostos.lsp

import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NostosLspDefinitionMappingTest {

    private val range = Range(Position(3, 7), Position(3, 11))
    private val wider = Range(Position(1, 0), Position(9, 0))

    @Test
    fun plainLocationsPassThrough() {
        val location = Location("file:///tmp/a.nos", range)
        val response = Either.forLeft<List<Location>, List<LocationLink>>(listOf(location))
        assertEquals(listOf(location), definitionLocations(response))
    }

    @Test
    fun locationLinksPreferTheSelectionRange() {
        val link = LocationLink().apply {
            targetUri = "file:///tmp/a.nos"
            targetRange = wider
            targetSelectionRange = range
        }
        val response = Either.forRight<List<Location>, List<LocationLink>>(listOf(link))
        assertEquals(listOf(Location("file:///tmp/a.nos", range)), definitionLocations(response))
    }

    @Test
    fun locationLinksFallBackToTheTargetRange() {
        val link = LocationLink().apply {
            targetUri = "file:///tmp/a.nos"
            targetRange = wider
        }
        val response = Either.forRight<List<Location>, List<LocationLink>>(listOf(link))
        assertEquals(listOf(Location("file:///tmp/a.nos", wider)), definitionLocations(response))
    }

    @Test
    fun missingResponsesAndMalformedLinksGiveNothing() {
        assertEquals(emptyList<Location>(), definitionLocations(null))
        assertEquals(
            emptyList<Location>(),
            definitionLocations(Either.forLeft(emptyList())),
        )
        // A link without a target URI cannot be navigated to.
        assertEquals(
            emptyList<Location>(),
            definitionLocations(Either.forRight(listOf(LocationLink().apply { targetRange = wider }))),
        )
    }
}
