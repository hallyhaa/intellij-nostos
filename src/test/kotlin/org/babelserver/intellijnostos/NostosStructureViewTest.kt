package org.babelserver.intellijnostos

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NostosStructureViewTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    fun testStructureView() {
        myFixture.configureByFile("structure/StructureTest.nos")
        myFixture.testStructureView { view ->
            val root = view.treeModel.root as NostosStructureViewElement
            val children = root.children
            val texts = children.map { (it as NostosStructureViewElement).presentation.presentableText }

            assertContainsOrdered(
                texts,
                "import Std.List",
                "use Http.Server",
                "type Colour",
                "type Person",
                "reactive Counter",
                "trait Printable",
                "impl Printable for Person",
                "add(x, y)",
                "last_destination(move)",
                "template page(title)",
                "extern greet(name)",
                "var x",
                "mvar count",
                "const max_val",
                "test \"addition\"",
            )
        }
    }

    fun testTypeVariants() {
        myFixture.configureByFile("structure/StructureTest.nos")
        myFixture.testStructureView { view ->
            val root = view.treeModel.root as NostosStructureViewElement
            val children = root.children.map { it as NostosStructureViewElement }
            val childMap = children.associateBy { it.presentation.presentableText }

            val colourChildren = childMap["type Colour"]!!.children
                .map { (it as NostosStructureViewElement).presentation.presentableText }
            assertContainsOrdered(colourChildren, "Red", "Green", "Blue")
        }
    }

    fun testTraitMembers() {
        myFixture.configureByFile("structure/StructureTest.nos")
        myFixture.testStructureView { view ->
            val root = view.treeModel.root as NostosStructureViewElement
            val children = root.children.map { it as NostosStructureViewElement }
            val childMap = children.associateBy { it.presentation.presentableText }

            val traitChildren = childMap["trait Printable"]!!.children
                .map { (it as NostosStructureViewElement).presentation.presentableText }
            assertContainsOrdered(traitChildren, "show(self)")
        }
    }

    fun testImplMembers() {
        myFixture.configureByFile("structure/StructureTest.nos")
        myFixture.testStructureView { view ->
            val root = view.treeModel.root as NostosStructureViewElement
            val children = root.children.map { it as NostosStructureViewElement }
            val childMap = children.associateBy { it.presentation.presentableText }

            val implChildren = childMap["impl Printable for Person"]!!.children
                .map { (it as NostosStructureViewElement).presentation.presentableText }
            assertContainsOrdered(implChildren, "show(self)")
        }
    }

    private fun assertContainsOrdered(actual: List<String?>, vararg expected: String) {
        var idx = 0
        for (exp in expected) {
            val found = actual.indexOf(exp)
            assertTrue("Expected '$exp' in structure view but not found. Actual: $actual", found >= 0)
            assertTrue("Expected '$exp' after index $idx but found at $found. Actual: $actual", found >= idx)
            idx = found + 1
        }
    }
}
