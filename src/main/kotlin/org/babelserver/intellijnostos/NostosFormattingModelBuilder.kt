package org.babelserver.intellijnostos

import com.intellij.formatting.*
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.tree.TokenSet
import org.babelserver.intellijnostos.psi.NostosTypes

class NostosFormattingModelBuilder : FormattingModelBuilder {

    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        val spacingBuilder = createSpacingBuilder(formattingContext.codeStyleSettings)
        val block = NostosBlock(formattingContext.node, spacingBuilder)
        return FormattingModelProvider.createFormattingModelForPsiFile(
            formattingContext.containingFile,
            block,
            formattingContext.codeStyleSettings
        )
    }

    companion object {
        fun createSpacingBuilder(settings: CodeStyleSettings): SpacingBuilder {
            val keywords = TokenSet.create(
                NostosTypes.FN, NostosTypes.IF, NostosTypes.THEN, NostosTypes.ELSE,
                NostosTypes.MATCH, NostosTypes.TYPE_KW, NostosTypes.TRAIT, NostosTypes.IMPL,
                NostosTypes.MODULE_KW, NostosTypes.IMPORT, NostosTypes.USE, NostosTypes.FOR,
                NostosTypes.WHILE, NostosTypes.DO, NostosTypes.IN, NostosTypes.TO,
                NostosTypes.RETURN, NostosTypes.VAR, NostosTypes.MVAR, NostosTypes.CONST,
                NostosTypes.TRY, NostosTypes.CATCH, NostosTypes.FINALLY, NostosTypes.THROW,
                NostosTypes.PANIC, NostosTypes.SPAWN, NostosTypes.SPAWN_LINK, NostosTypes.SPAWN_MONITOR,
                NostosTypes.RECEIVE, NostosTypes.AFTER, NostosTypes.WHEN, NostosTypes.WITH,
                NostosTypes.AS, NostosTypes.FROM, NostosTypes.WHERE, NostosTypes.EXTERN,
                NostosTypes.TEST, NostosTypes.PUB, NostosTypes.PRIVATE, NostosTypes.TEMPLATE,
                NostosTypes.REACTIVE, NostosTypes.DERIVING, NostosTypes.QUOTE,
            )

            val binaryOps = TokenSet.create(
                NostosTypes.PLUS, NostosTypes.STAR, NostosTypes.SLASH, NostosTypes.PERCENT,
                NostosTypes.EQ_EQ, NostosTypes.BANG_EQ, NostosTypes.LT, NostosTypes.GT,
                NostosTypes.LE, NostosTypes.GE, NostosTypes.AMP_AMP, NostosTypes.PIPE_PIPE,
                NostosTypes.PLUS_PLUS, NostosTypes.STAR_STAR, NostosTypes.DOT_DOT,
                NostosTypes.COLON_COLON, NostosTypes.PIPE_GT, NostosTypes.SEND_OP,
            )

            val assignOps = TokenSet.create(
                NostosTypes.EQ, NostosTypes.PLUS_EQ, NostosTypes.MINUS_EQ,
                NostosTypes.STAR_EQ, NostosTypes.SLASH_EQ,
            )

            return SpacingBuilder(settings, NostosLanguage)
                // Dot: no spaces (x.y not x . y)
                .around(NostosTypes.DOT).none()
                // @ for annotations: no space after (@name not @ name)
                .after(NostosTypes.AT).none()
                // ^ for pin patterns: no space after (^x not ^ x)
                .after(NostosTypes.CARET).none()
                // Backslash lambda: no space after (\x not \ x)
                .after(NostosTypes.BACKSLASH).none()
                // Comma: no space before, one space after (allow line break)
                .before(NostosTypes.COMMA).none()
                .after(NostosTypes.COMMA).spacing(1, 1, 0, true, 0)
                // Semicolon: no space before
                .before(NostosTypes.SEMICOLON).none()
                // Colon: no space before, one space after (allow line break)
                .before(NostosTypes.COLON).none()
                .after(NostosTypes.COLON).spacing(1, 1, 0, true, 0)
                // fn in lambda context: no space before paren — fn(x) not fn (x)
                .afterInside(NostosTypes.FN, NostosTypes.FN_LAMBDA_EXPR).none()
                // Unary prefix operators: no space after
                .afterInside(NostosTypes.MINUS, NostosTypes.PREFIX_EXPR).none()
                .afterInside(NostosTypes.BANG, NostosTypes.PREFIX_EXPR).none()
                .afterInside(NostosTypes.TILDE, NostosTypes.PREFIX_EXPR).none()
                // Negative literal in patterns: no space (-42 not - 42)
                .afterInside(NostosTypes.MINUS, NostosTypes.LITERAL_PATTERN).none()
                // Binary minus: spaces (x - y)
                .aroundInside(NostosTypes.MINUS, NostosTypes.ADD_GROUP_EXPR)
                    .spacing(1, 1, 0, true, 0)
                // Arrow operators
                .around(NostosTypes.ARROW).spacing(1, 1, 0, true, 0)
                .around(NostosTypes.FAT_ARROW).spacing(1, 1, 0, true, 0)
                // Binary operators
                .around(binaryOps).spacing(1, 1, 0, true, 0)
                // Assignment operators
                .around(assignOps).spacing(1, 1, 0, true, 0)
                // Pipe (type variants, or-patterns, match arms)
                .around(NostosTypes.PIPE).spacing(1, 1, 0, true, 0)
                // Keywords: one space after (allow line break)
                .after(keywords).spacing(1, 1, 0, true, 0)
        }
    }
}
