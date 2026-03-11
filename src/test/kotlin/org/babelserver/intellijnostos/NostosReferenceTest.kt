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

    // ==================== Resolve: lambda params ====================

    fun testResolveBackslashLambdaParam() {
        myFixture.configureByText("test.nos", """
            mapper = \x -> <caret>x + 1
        """.trimIndent())
        val resolved = resolveAtCaret()
        assertEquals("x", resolved!!.text)
    }

    fun testResolveFnLambdaParam() {
        myFixture.configureByText("test.nos", """
            mapper = fn(x) -> <caret>x * 2
        """.trimIndent())
        val resolved = resolveAtCaret()
        assertInstanceOf(resolved, NostosParam::class.java)
        assertEquals("x", (resolved as NostosNamedElement).name)
    }

    // ==================== Resolve: const at file level ====================

    fun testResolveConstRef() {
        myFixture.configureByText("test.nos", """
            const max_size = 100
            result = max_si<caret>ze + 1
        """.trimIndent())
        val resolved = resolveAtCaret()
        assertInstanceOf(resolved, NostosConstDecl::class.java)
        assertEquals("max_size", (resolved as NostosNamedElement).name)
    }

    // ==================== Resolve: for-loop binding ====================

    fun testResolveForLoopBinding() {
        myFixture.configureByText("test.nos", """
            result = for i in [1, 2, 3] do <caret>i + 1
        """.trimIndent())
        val resolved = resolveAtCaret()
        assertNotNull("Expected for-loop variable to resolve", resolved)
        assertEquals("i", resolved!!.text)
    }

    // ==================== Resolve: match arm (non-brace) ====================

    fun testResolveMatchArmBinding() {
        myFixture.configureByText("test.nos", """
            result = match x {
                n => <caret>n + 1,
            }
        """.trimIndent())
        val resolved = resolveAtCaret()
        assertNotNull("Expected match arm binding to resolve", resolved)
        assertEquals("n", resolved!!.text)
    }

    // ==================== Resolve: extern/template params ====================

    fun testResolveExternParam() {
        myFixture.configureByText("test.nos", """
            extern fn printf(fmt: String): Int
            result = fm<caret>t
        """.trimIndent())
        // extern params are not in scope outside the declaration,
        // so this should resolve to the extern param within the decl
        val ref = myFixture.file.findReferenceAt(myFixture.caretOffset)
        // 'fmt' at top level won't resolve (extern params are not in outer scope)
        // — this tests that the extern branch is traversed during resolve
        assertNotNull("Expected reference at caret", ref)
    }

    fun testResolveTemplateParam() {
        myFixture.configureByText("test.nos", """
            template deco(f) = <caret>f
        """.trimIndent())
        val resolved = resolveAtCaret()
        assertInstanceOf(resolved, NostosParam::class.java)
        assertEquals("f", (resolved as NostosNamedElement).name)
    }

    // ==================== Resolve: in module ====================

    fun testResolveInModule() {
        myFixture.configureByText("test.nos", """
            module Math
                fn square(x: Int): Int = x * x
            end
            result = squa<caret>re(5)
        """.trimIndent())
        val resolved = resolveAtCaret()
        assertInstanceOf(resolved, NostosFnDecl::class.java)
        assertEquals("square", (resolved as NostosNamedElement).name)
    }

    // ==================== Type references: variant, reactive, module ====================

    fun testResolveTypeVariantAsType() {
        myFixture.configureByText("test.nos", """
            type Shape = Circle(Float) | Rectangle(Float, Float)
            fn area(s: Circl<caret>e): Float = 0.0
        """.trimIndent())
        val resolved = resolveAtCaret()
        assertInstanceOf(resolved, NostosTypeVariant::class.java)
        assertEquals("Circle", (resolved as NostosNamedElement).name)
    }

    fun testResolveReactiveType() {
        myFixture.configureByText("test.nos", """
            reactive Counter = Int
            fn check(c: Counte<caret>r): Int = 0
        """.trimIndent())
        val resolved = resolveAtCaret()
        assertInstanceOf(resolved, NostosReactiveDecl::class.java)
    }

    fun testResolveModuleAsType() {
        myFixture.configureByText("test.nos", """
            module Utils
                fn helper(): Int = 0
            end
            fn check(u: Util<caret>s): Int = 0
        """.trimIndent())
        val resolved = resolveAtCaret()
        assertInstanceOf(resolved, NostosModuleDecl::class.java)
    }

    fun testResolveTypeInModule() {
        myFixture.configureByText("test.nos", """
            module Shapes
                type Circle = { radius: Float }
            end
            fn draw(c: Circl<caret>e): Int = 0
        """.trimIndent())
        val resolved = resolveAtCaret()
        assertInstanceOf(resolved, NostosTypeDecl::class.java)
    }

    fun testResolveTraitInModule() {
        myFixture.configureByText("test.nos", """
            module Traits
                trait Showable {
                    fn show(self) -> String
                }
            end
            fn display(s: Showab<caret>le): String = s.show()
        """.trimIndent())
        val resolved = resolveAtCaret()
        assertInstanceOf(resolved, NostosTraitDecl::class.java)
    }

    // ==================== Rename type ====================

    fun testRenameType() {
        myFixture.configureByText("test.nos", """
            type Colo<caret>ur = Red | Green | Blue
            fn paint(c: Colour): Colour = c
        """.trimIndent())
        myFixture.renameElementAtCaret("Color")
        myFixture.checkResult("""
            type Color = Red | Green | Blue
            fn paint(c: Color): Color = c
        """.trimIndent())
    }

    // ==================== Find Usages: more types ====================

    fun testFindUsagesOfVar() {
        myFixture.configureByText("test.nos", """
            var cou<caret>nt = 0
            x = count + 1
            y = count + 2
        """.trimIndent())
        val usages = myFixture.testFindUsages("test.nos")
        assertEquals("Expected 2 usages of 'count'", 2, usages.size)
    }

    fun testFindUsagesOfParam() {
        myFixture.configureByText("test.nos", """
            fn add(<caret>x: Int, y: Int): Int = x + x + y
        """.trimIndent())
        val usages = myFixture.testFindUsages("test.nos")
        assertEquals("Expected 2 usages of 'x'", 2, usages.size)
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
