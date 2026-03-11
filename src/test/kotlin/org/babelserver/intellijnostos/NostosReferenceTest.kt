package org.babelserver.intellijnostos

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.babelserver.intellijnostos.psi.*

class NostosReferenceTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    // ==================== Go to Definition ====================

    fun testResolveFnCall() {
        myFixture.configureByText("test.nos", """
            fn add(x: Int, y: Int): Int = x + y
            result = a<caret>dd(1, 2)
        """.trimIndent())
        val resolved = resolveAtCaret()
        assertInstanceOf(resolved, NostosFnDecl::class.java)
        assertEquals("add", (resolved as NostosNamedElement).name)
    }

    fun testResolveBareFnCall() {
        myFixture.configureByText("test.nos", """
            double(n) = n * 2
            result = doub<caret>le(5)
        """.trimIndent())
        val resolved = resolveAtCaret()
        assertInstanceOf(resolved, NostosBareFnDecl::class.java)
        assertEquals("double", (resolved as NostosNamedElement).name)
    }

    fun testResolveVarRef() {
        myFixture.configureByText("test.nos", """
            var count = 0
            result = cou<caret>nt + 1
        """.trimIndent())
        val resolved = resolveAtCaret()
        assertInstanceOf(resolved, NostosVarDecl::class.java)
    }

    fun testResolveFnParam() {
        myFixture.configureByText("test.nos", """
            fn add(x: Int, y: Int): Int = <caret>x + y
        """.trimIndent())
        val resolved = resolveAtCaret()
        assertInstanceOf(resolved, NostosParam::class.java)
        assertEquals("x", (resolved as NostosNamedElement).name)
    }

    fun testResolveBareParam() {
        myFixture.configureByText("test.nos", """
            double(n) = <caret>n * 2
        """.trimIndent())
        val resolved = resolveAtCaret()
        assertInstanceOf(resolved, NostosBareParam::class.java)
    }

    fun testResolveTypeRef() {
        myFixture.configureByText("test.nos", """
            type Colour = Red | Green | Blue
            fn paint(c: Colo<caret>ur): Int = 0
        """.trimIndent())
        val resolved = resolveAtCaret()
        assertInstanceOf(resolved, NostosTypeDecl::class.java)
        assertEquals("Colour", (resolved as NostosNamedElement).name)
    }

    fun testResolveTraitRef() {
        myFixture.configureByText("test.nos", """
            trait Printable {
                fn show(self) -> String
            }
            fn display(p: Printa<caret>ble): String = p.show()
        """.trimIndent())
        val resolved = resolveAtCaret()
        assertInstanceOf(resolved, NostosTraitDecl::class.java)
    }

    // ==================== Cross-file resolution ====================

    fun testCrossFileResolve() {
        myFixture.configureByText("helpers.nos", """
            fn helper(x: Int): Int = x + 1
        """.trimIndent())
        myFixture.configureByText("main.nos", """
            result = hel<caret>per(42)
        """.trimIndent())
        val resolved = resolveAtCaret()
        assertInstanceOf(resolved, NostosFnDecl::class.java)
        assertEquals("helper", (resolved as NostosNamedElement).name)
        assertEquals("helpers.nos", resolved.containingFile.name)
    }

    fun testCrossFileTypeResolve() {
        myFixture.configureByText("types.nos", """
            type Colour = Red | Green | Blue
        """.trimIndent())
        myFixture.configureByText("main.nos", """
            fn paint(c: Colo<caret>ur): Int = 0
        """.trimIndent())
        val resolved = resolveAtCaret()
        assertInstanceOf(resolved, NostosTypeDecl::class.java)
        assertEquals("types.nos", (resolved as PsiElement).containingFile.name)
    }

    // ==================== Find Usages ====================

    fun testFindUsagesOfFn() {
        myFixture.configureByText("test.nos", """
            fn a<caret>dd(x: Int, y: Int): Int = x + y
            a = add(1, 2)
            b = add(3, 4)
        """.trimIndent())
        val usages = myFixture.testFindUsages("test.nos")
        assertEquals("Expected 2 usages of 'add'", 2, usages.size)
    }

    fun testFindUsagesOfType() {
        myFixture.configureByText("test.nos", """
            type Colo<caret>ur = Red | Green | Blue
            fn paint(c: Colour): Colour = c
        """.trimIndent())
        val usages = myFixture.testFindUsages("test.nos")
        assertEquals("Expected 2 usages of 'Colour'", 2, usages.size)
    }

    // ==================== Rename ====================

    fun testRenameFn() {
        myFixture.configureByText("test.nos", """
            fn a<caret>dd(x: Int, y: Int): Int = x + y
            result = add(1, 2)
        """.trimIndent())
        myFixture.renameElementAtCaret("sum")
        myFixture.checkResult("""
            fn sum(x: Int, y: Int): Int = x + y
            result = sum(1, 2)
        """.trimIndent())
    }

    // ==================== Helpers ====================

    private fun resolveAtCaret(): PsiElement? {
        val ref = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNotNull("Expected reference at caret", ref)
        val resolved = ref!!.resolve()
        assertNotNull("Expected reference to resolve", resolved)
        return resolved
    }

    private fun <T> assertInstanceOf(actual: Any?, expectedClass: Class<T>) {
        assertNotNull("Expected non-null instance of ${expectedClass.simpleName}", actual)
        assertTrue(
            "Expected ${expectedClass.simpleName} but got ${actual!!::class.java.simpleName}",
            expectedClass.isInstance(actual)
        )
    }
}
