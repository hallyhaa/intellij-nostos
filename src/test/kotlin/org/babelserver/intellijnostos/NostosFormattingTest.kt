package org.babelserver.intellijnostos

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NostosFormattingTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private fun doFormattingTest(before: String, after: String) {
        myFixture.configureByText("test.nos", before)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformat(myFixture.file)
        }
        myFixture.checkResult(after)
    }

    // ===== Spacing around operators =====

    fun testSpacingAroundBinaryOperators() {
        doFormattingTest(
            "x=1+2*3",
            "x = 1 + 2 * 3"
        )
    }

    fun testSpacingAroundComparisonOperators() {
        doFormattingTest(
            "result=x>=10&&y!=0",
            "result = x >= 10 && y != 0"
        )
    }

    fun testSpacingAroundArrows() {
        doFormattingTest(
            "fn add(x: Int, y: Int)->Int = x + y",
            "fn add(x: Int, y: Int) -> Int = x + y"
        )
    }

    fun testSpacingAroundFatArrow() {
        doFormattingTest(
            "mapper = x=>x * 2",
            "mapper = x => x * 2"
        )
    }

    fun testSpacingAroundPipeOperator() {
        doFormattingTest(
            "result = x|>double|>toString",
            "result = x |> double |> toString"
        )
    }

    // ===== No space around dot =====

    fun testNoSpaceAroundDot() {
        doFormattingTest(
            "x . y . z",
            "x.y.z"
        )
    }

    // ===== Comma spacing =====

    fun testCommaSpacing() {
        doFormattingTest(
            "fn add(x:Int,y:Int) = x + y",
            "fn add(x: Int, y: Int) = x + y"
        )
    }

    // ===== Colon spacing =====

    fun testColonSpacing() {
        doFormattingTest(
            "var x  :  Int = 42",
            "var x: Int = 42"
        )
    }

    // ===== Unary operators =====

    fun testUnaryMinusNoSpace() {
        doFormattingTest(
            "x = -42",
            "x = -42"
        )
    }

    fun testUnaryBangNoSpace() {
        doFormattingTest(
            "x = !true",
            "x = !true"
        )
    }

    fun testBinaryMinusHasSpaces() {
        doFormattingTest(
            "x = a-b",
            "x = a - b"
        )
    }

    // ===== Keyword spacing =====

    fun testKeywordSpacingAfterKeyword() {
        // Tests that keyword is followed by exactly one space
        doFormattingTest(
            "var  x: Int = 42",
            "var x: Int = 42"
        )
    }

    fun testMatchKeywordSpacing() {
        doFormattingTest(
            "match  x {\n0 => y,\n}",
            "match x {\n    0 => y,\n}"
        )
    }

    fun testFnLambdaNoSpace() {
        doFormattingTest(
            "mapper = fn (x) -> x * 2",
            "mapper = fn(x) -> x * 2"
        )
    }

    // ===== Indentation =====

    fun testBlockIndentation() {
        doFormattingTest(
            """{
            |x = 1
            |y = 2
            |}""".trimMargin(),
            """{
            |    x = 1
            |    y = 2
            |}""".trimMargin()
        )
    }

    fun testMatchArmIndentation() {
        doFormattingTest(
            """match x {
            |0 => "zero",
            |n => "other",
            |}""".trimMargin(),
            """match x {
            |    0 => "zero",
            |    n => "other",
            |}""".trimMargin()
        )
    }

    fun testDeclBodyIndentation() {
        doFormattingTest(
            """add(x, y) =
            |x + y""".trimMargin(),
            """add(x, y) =
            |    x + y""".trimMargin()
        )
    }

    fun testFnDeclBodyIndentation() {
        doFormattingTest(
            """fn add(x: Int, y: Int): Int =
            |x + y""".trimMargin(),
            """fn add(x: Int, y: Int): Int =
            |    x + y""".trimMargin()
        )
    }

    fun testLambdaBodyIndentation() {
        doFormattingTest(
            """\x ->
            |x + 1""".trimMargin(),
            """\x ->
            |    x + 1""".trimMargin()
        )
    }

    fun testMultilineFnWithCallChain() {
        doFormattingTest(
            """process(items) =
            |items.filter(x =>
            |x > 0
            |)""".trimMargin(),
            """process(items) =
            |    items.filter(x =>
            |        x > 0
            |    )""".trimMargin()
        )
    }
}
