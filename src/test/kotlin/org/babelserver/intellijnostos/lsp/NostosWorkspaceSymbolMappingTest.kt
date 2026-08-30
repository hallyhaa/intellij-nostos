package org.babelserver.intellijnostos.lsp

import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.WorkspaceSymbolLocation
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NostosWorkspaceSymbolMappingTest {

    @Test
    fun fullLocationIsUsedAsIs() {
        val location = Location("file:///tmp/a.nos", Range(Position(3, 7), Position(3, 11)))
        val symbol = WorkspaceSymbol("main", SymbolKind.Function, Either.forLeft(location))

        assertEquals(location, symbol.resolvedLocation())
    }

    @Test
    fun uriOnlyLocationNavigatesToTopOfFile() {
        val symbol = WorkspaceSymbol(
            "main",
            SymbolKind.Function,
            Either.forRight(WorkspaceSymbolLocation("file:///tmp/a.nos")),
        )

        val resolved = symbol.resolvedLocation()
        assertEquals("file:///tmp/a.nos", resolved?.uri)
        assertNull(resolved?.range?.start)
    }
}
