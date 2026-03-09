package org.babelserver.intellijnostos;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static org.babelserver.intellijnostos.NostosTokenTypes.*;

%%

%public
%class NostosFlexLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType

%state S_BLOCK_COMMENT
%state S_STRING
%state S_CHAR_LIT

%{
    public NostosFlexLexer() {
        this((java.io.Reader)null);
    }

    /** Tracks brace depth for string interpolation (${...}). */
    public int interpolationDepth = 0;

    private IElementType identifierOrKeyword(boolean beforeParen) {
        String text = yytext().toString();
        switch (text) {
            case "if": case "then": case "else": case "match": case "with":
            case "type": case "trait": case "end": case "use": case "pub":
            case "private": case "module": case "import": case "var": case "mvar":
            case "const": case "for": case "to": case "while": case "do":
            case "break": case "continue": case "return": case "spawn":
            case "spawn_link": case "spawn_monitor": case "receive": case "after":
            case "try": case "catch": case "finally": case "throw": case "panic":
            case "when": case "true": case "false": case "self": case "Self":
            case "reactive": case "deriving": case "where": case "forall":
            case "extern": case "test": case "quote": case "from":
                return KEYWORD;
            default:
                if (Character.isUpperCase(text.charAt(0))) {
                    return TYPE_NAME;
                }
                return beforeParen ? FUNCTION_NAME : IDENTIFIER;
        }
    }
%}

// Character classes
LineTerminator  = \r|\n|\r\n
Spaces          = [ \t]
WhiteSpace      = {Spaces} | {LineTerminator}
Digit           = [0-9]
HexDigit        = [0-9a-fA-F]
BinDigit        = [01]
Letter          = [a-zA-Z]
IdentStart      = {Letter} | "_"
IdentPart       = {Letter} | {Digit} | "_"
Identifier      = {IdentStart} {IdentPart}*

// Numbers
DecInteger      = {Digit} ({Digit} | "_")*
HexInteger      = 0 [xX] ({HexDigit} | "_")+
BinInteger      = 0 [bB] ({BinDigit} | "_")+
FracPart        = "." {Digit} ({Digit} | "_")*
Exponent        = [eE] [+-]? {Digit}+
DecimalSuffix   = "d"
FloatNumber     = {DecInteger} {FracPart} {Exponent}? {DecimalSuffix}?
                | {DecInteger} {Exponent} {DecimalSuffix}?
                | {DecInteger} {FracPart}? {DecimalSuffix}

// Two-char operators
TwoCharOp       = "++" | "::" | "->" | "<-" | "<=" | ">="
                | "==" | "!=" | "&&" | "||" | "**" | "+="
                | "=>" | "|>" | "-=" | "*=" | "/="

%%

/* ===== S_BLOCK_COMMENT state ===== */

<S_BLOCK_COMMENT> {
    "*#"            { yybegin(YYINITIAL); return BLOCK_COMMENT; }
    [^*]+           { return BLOCK_COMMENT; }
    "*"             { return BLOCK_COMMENT; }
    <<EOF>>         { yybegin(YYINITIAL); return BLOCK_COMMENT; }
}

/* ===== S_STRING state ===== */

<S_STRING> {
    "${"            { interpolationDepth++; yybegin(YYINITIAL); return INTERPOLATION_START; }
    \"              { yybegin(YYINITIAL); return STRING; }
    \\[^\r\n]       { return STRING; }
    [^\"\\$\r\n]+   { return STRING; }
    "$"             { return STRING; }
    \\              { return STRING; }
    {LineTerminator} { return STRING; }
    <<EOF>>         { yybegin(YYINITIAL); return STRING; }
}

/* ===== S_CHAR_LIT state ===== */

<S_CHAR_LIT> {
    \'              { yybegin(YYINITIAL); return CHAR; }
    \\[^\r\n]       { return CHAR; }
    [^\'\\\r\n]     { return CHAR; }
    <<EOF>>         { yybegin(YYINITIAL); return CHAR; }
}

/* ===== YYINITIAL state ===== */

<YYINITIAL> {
    // Whitespace
    {WhiteSpace}+                   { return WHITE_SPACE; }

    // Block comment start
    "#*"                            { yybegin(S_BLOCK_COMMENT); return BLOCK_COMMENT; }

    // Line comments (# followed by anything except *)
    "#" [^*\r\n] [^\r\n]*          { return COMMENT; }
    "#"                             { return COMMENT; }

    // String start
    \"                              { yybegin(S_STRING); return STRING; }

    // Char literal start
    \'                              { yybegin(S_CHAR_LIT); return CHAR; }

    // Numbers (specific before general)
    {HexInteger}                    { return NUMBER; }
    {BinInteger}                    { return NUMBER; }
    {FloatNumber}                   { return NUMBER; }
    {DecInteger}                    { return NUMBER; }

    // Function names: identifier followed by ( with optional spaces
    {Identifier} / {Spaces}* "("   { return identifierOrKeyword(true); }

    // Identifiers and keywords
    {Identifier}                    { return identifierOrKeyword(false); }

    // Two-char operators (before single-char)
    {TwoCharOp}                     { return OPERATOR; }

    // Single-char operators and punctuation
    [+\-*/%=<>!.|,;:@~\^&\?]       { return OPERATOR; }
    "("                             { return LPAREN; }
    ")"                             { return RPAREN; }
    "["                             { return LBRACKET; }
    "]"                             { return RBRACKET; }
    "{"                             { if (interpolationDepth > 0) interpolationDepth++; return LBRACE; }
    "}"                             { if (interpolationDepth > 1) { interpolationDepth--; return RBRACE; }
                                      if (interpolationDepth == 1) { interpolationDepth = 0; yybegin(S_STRING); return INTERPOLATION_END; }
                                      return RBRACE; }

    // Catch-all
    [^]                             { return BAD_CHARACTER; }
}
