package org.babelserver.intellijnostos

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NostosFoldingTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    fun testFolding() {
        myFixture.testFolding("$testDataPath/folding/FoldingTest.nos")
    }
}
