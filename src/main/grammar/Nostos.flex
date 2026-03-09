package org.babelserver.intellijnostos;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;

%%

%public
%class NostosFlexLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType

%%

<YYINITIAL> {
    [^]    { return BAD_CHARACTER; }
}
