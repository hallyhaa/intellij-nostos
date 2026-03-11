package org.babelserver.intellijnostos

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.babelserver.intellijnostos.psi.*

class NostosFindUsagesProviderTest : BasePlatformTestCase() {

    private val provider = NostosFindUsagesProvider()

    override fun getTestDataPath(): String = "src/test/testData"

    private fun findNamedElement(name: String): NostosNamedElement? {
        val file = myFixture.file
        val elements = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, NostosNamedElement::class.java)
        return elements.firstOrNull { it.name == name }
    }

    // ==================== getType ====================

    fun testGetTypeFnDecl() {
        myFixture.configureByText("test.nos", "fn add(x: Int): Int = x")
        assertEquals("function", provider.getType(findNamedElement("add")!!))
    }

    fun testGetTypeBareFnDecl() {
        myFixture.configureByText("test.nos", "double(n) = n * 2")
        assertEquals("function", provider.getType(findNamedElement("double")!!))
    }

    fun testGetTypeExternDecl() {
        myFixture.configureByText("test.nos", "extern fn printf(fmt: String): Int")
        assertEquals("extern function", provider.getType(findNamedElement("printf")!!))
    }

    fun testGetTypeTemplateDecl() {
        myFixture.configureByText("test.nos", "template deco(f) = f")
        assertEquals("template", provider.getType(findNamedElement("deco")!!))
    }

    fun testGetTypeTypeDecl() {
        myFixture.configureByText("test.nos", "type Colour = Red | Green | Blue")
        assertEquals("type", provider.getType(findNamedElement("Colour")!!))
    }

    fun testGetTypeTraitDecl() {
        myFixture.configureByText("test.nos", """
            trait Show {
                fn show(self) -> String
            }
        """.trimIndent())
        assertEquals("trait", provider.getType(findNamedElement("Show")!!))
    }

    fun testGetTypeReactiveDecl() {
        myFixture.configureByText("test.nos", "reactive Counter = 0")
        assertEquals("reactive type", provider.getType(findNamedElement("Counter")!!))
    }

    fun testGetTypeModuleDecl() {
        myFixture.configureByText("test.nos", """
            module Math
            end
        """.trimIndent())
        assertEquals("module", provider.getType(findNamedElement("Math")!!))
    }

    fun testGetTypeVarDecl() {
        myFixture.configureByText("test.nos", "var count = 0")
        assertEquals("variable", provider.getType(findNamedElement("count")!!))
    }

    fun testGetTypeMvarDecl() {
        myFixture.configureByText("test.nos", "mvar state: Int = 0")
        assertEquals("mutable variable", provider.getType(findNamedElement("state")!!))
    }

    fun testGetTypeConstDecl() {
        myFixture.configureByText("test.nos", "const MAX = 100")
        assertEquals("constant", provider.getType(findNamedElement("MAX")!!))
    }

    fun testGetTypeParam() {
        myFixture.configureByText("test.nos", "fn add(x: Int): Int = x")
        assertEquals("parameter", provider.getType(findNamedElement("x")!!))
    }

    fun testGetTypeBareParam() {
        myFixture.configureByText("test.nos", "double(n) = n * 2")
        assertEquals("parameter", provider.getType(findNamedElement("n")!!))
    }

    fun testGetTypeTypeVariant() {
        myFixture.configureByText("test.nos", "type Colour = Red | Green | Blue")
        assertEquals("type variant", provider.getType(findNamedElement("Red")!!))
    }

    // ==================== canFindUsagesFor ====================

    fun testCanFindUsagesForNamedElement() {
        myFixture.configureByText("test.nos", "fn add(x: Int): Int = x")
        assertTrue(provider.canFindUsagesFor(findNamedElement("add")!!))
    }

    // ==================== getDescriptiveName / getNodeText ====================

    fun testGetDescriptiveName() {
        myFixture.configureByText("test.nos", "fn add(x: Int): Int = x")
        assertEquals("add", provider.getDescriptiveName(findNamedElement("add")!!))
    }

    fun testGetNodeText() {
        myFixture.configureByText("test.nos", "fn add(x: Int): Int = x")
        assertEquals("add", provider.getNodeText(findNamedElement("add")!!, false))
    }

    // ==================== getWordsScanner ====================

    fun testGetWordsScanner() {
        assertNotNull(provider.wordsScanner)
    }
}
