package org.babelserver.intellijnostos

import com.intellij.psi.impl.DebugUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class NostosParserTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private fun doParserTest() {
        val testName = getTestName(false)
        myFixture.configureByFile("parser/$testName.nos")
        val actual = DebugUtil.psiToString(myFixture.file, true, false)
            .trimEnd()
            .lines()
            .dropLastWhile { it.trim().startsWith("PsiWhiteSpace") }
            .joinToString("\n")

        val expectedFile = File("$testDataPath/parser/$testName.txt")
        if (!expectedFile.exists()) {
            expectedFile.writeText(actual)
            fail("Expected output file $expectedFile did not exist; created with actual content. Re-run test.")
            return
        }

        val expected = expectedFile.readText().trimEnd()
        assertEquals(expected, actual)
    }

    fun testFnDecl() = doParserTest()
    fun testTypeDecl() = doParserTest()
    fun testExpressions() = doParserTest()
    fun testPatterns() = doParserTest()
    fun testStringInterpolation() = doParserTest()
    fun testControlFlow() = doParserTest()
    fun testErrorRecovery() = doParserTest()
    fun testBareFnDecl() = doParserTest()
    fun testBraceMatch() = doParserTest()
    fun testArrowLambda() = doParserTest()
    fun testDestructuringImport() = doParserTest()
    fun testNewlineBlock() = doParserTest()
    fun testListConsPattern() = doParserTest()
    fun testExampleRobot() = doParserTest()
    fun testBlockBody() = doParserTest()
    fun testIfBlock() = doParserTest()
    fun testForToLoop() = doParserTest()
    fun testTemplate() = doParserTest()
    fun testReactive() = doParserTest()
    fun testTryCatch() = doParserTest()
    fun testSingleQuotedString() = doParserTest()
    fun testColonImpl() = doParserTest()
    fun testTraitSignature() = doParserTest()
    fun testLeadingPipeType() = doParserTest()
    fun testTypedBareParams() = doParserTest()
    fun testUnitType() = doParserTest()
    fun testListConsExpr() = doParserTest()
    fun testSessionDebug() = doParserTest()
    fun testModule() = doParserTest()
}
