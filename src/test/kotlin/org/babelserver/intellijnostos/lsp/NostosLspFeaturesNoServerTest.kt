package org.babelserver.intellijnostos.lsp

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies the new LSP-backed features degrade safely when no language server
 * is running — the state every headless test fixture is in. None of these
 * should throw or block; they must return empty/no-op. This does NOT exercise
 * a live server roundtrip (which requires a sandbox IDE), only the guard paths.
 */
class NostosLspFeaturesNoServerTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // The light fixture shares one project across test classes, and other
        // tests (e.g. doAnnotate-based ones) call startIfNeeded, which on a
        // machine with nostos installed actually launches the server. Guarantee
        // the precondition so "without a server" really holds here.
        NostosLspServerManager.getInstance(project).stop()
    }

    fun testWorkspaceSymbolReturnsEmptyWithoutServer() {
        val contributor = NostosWorkspaceSymbolContributor()
        val names = contributor.getNames(project, false)
        assertEquals(0, names.size)
        val items = contributor.getItemsByName("foo", "foo", project, false)
        assertEquals(0, items.size)
    }

    fun testHighlightUsagesFactoryNullWithoutServer() {
        val file = myFixture.configureByText("a.nos", "fn main() = 1")
        val factory = NostosHighlightUsagesHandlerFactory()
        // No active server in the fixture, so the factory must decline.
        assertNull(factory.createHighlightUsagesHandler(myFixture.editor, file))
    }

    fun testHighlightUsagesFactoryNullForNonNostos() {
        val file = myFixture.configureByText("a.txt", "hello")
        val factory = NostosHighlightUsagesHandlerFactory()
        assertNull(factory.createHighlightUsagesHandler(myFixture.editor, file))
    }

    fun testCodeVisionEmptyWithoutServer() {
        val file = myFixture.configureByText("a.nos", "fn main() = 1")
        val provider = NostosReferencesCodeVisionProvider()
        val entries = provider.computeForEditor(myFixture.editor, file)
        assertTrue(entries.isEmpty())
    }

    fun testCallHierarchyTargetNullWithoutServer() {
        myFixture.configureByText("a.nos", "fn main() = 1")
        val provider = NostosCallHierarchyProvider()
        // getTarget reads from a DataContext; with no server it must not produce a target.
        val ctx = com.intellij.openapi.actionSystem.DataContext { null }
        assertNull(provider.getTarget(ctx))
    }

    fun testCommitToLiveActionDisabledWithoutServer() {
        myFixture.configureByText("a.nos", "fn main() = 1")
        val presentation = myFixture.testAction(NostosCommitToLiveAction())
        assertFalse(presentation.isEnabled)
    }

    fun testCommitToLiveActionDisabledForNonNostosFile() {
        myFixture.configureByText("a.txt", "hello")
        val presentation = myFixture.testAction(NostosCommitToLiveAction())
        assertFalse(presentation.isEnabled)
    }

    fun testCommitAllToLiveActionDisabledWithoutServer() {
        val presentation = myFixture.testAction(NostosCommitAllToLiveAction())
        assertFalse(presentation.isEnabled)
    }

    fun testCacheActionsDisabledWithoutServer() {
        assertFalse(myFixture.testAction(NostosBuildCacheAction()).isEnabled)
        assertFalse(myFixture.testAction(NostosClearCacheAction()).isEnabled)
    }

    fun testGotoDeclarationHandlerNullWithoutServer() {
        val file = myFixture.configureByText("a.nos", "fn main() = unknown_symbol")
        val offset = file.text.indexOf("unknown_symbol") + 2
        val handler = NostosLspGotoDeclarationHandler()
        assertNull(handler.getGotoDeclarationTargets(file.findElementAt(offset), offset, myFixture.editor))
    }

    fun testGotoDeclarationHandlerNullForNonNostosFile() {
        val file = myFixture.configureByText("a.txt", "hello")
        val handler = NostosLspGotoDeclarationHandler()
        assertNull(handler.getGotoDeclarationTargets(file.findElementAt(1), 1, myFixture.editor))
    }
}
