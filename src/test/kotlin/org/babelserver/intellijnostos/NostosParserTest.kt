package org.babelserver.intellijnostos

import com.intellij.testFramework.ParsingTestCase

class NostosParserTest : ParsingTestCase("parser", "nos", NostosParserDefinition()) {

    override fun getTestDataPath(): String = "src/test/testData"

    fun testFnDecl() = doTest(true)
    fun testTypeDecl() = doTest(true)
    fun testExpressions() = doTest(true)
    fun testPatterns() = doTest(true)
    fun testStringInterpolation() = doTest(true)
    fun testControlFlow() = doTest(true)
    fun testErrorRecovery() = doTest(true)
}
