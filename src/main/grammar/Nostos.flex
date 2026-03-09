package org.babelserver.intellijnostos;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static org.babelserver.intellijnostos.psi.NostosTypes.*;

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
            case "fn": return FN;
            case "if": return IF;
            case "then": return THEN;
            case "else": return ELSE;
            case "match": return MATCH;
            case "with": return WITH;
            case "type": return TYPE_KW;
            case "trait": return TRAIT;
            case "end": return END;
            case "use": return USE;
            case "pub": return PUB;
            case "private": return PRIVATE;
            case "module": return MODULE_KW;
            case "import": return IMPORT;
            case "var": return VAR;
            case "mvar": return MVAR;
            case "const": return CONST;
            case "for": return FOR;
            case "to": return TO;
            case "in": return IN;
            case "while": return WHILE;
            case "do": return DO;
            case "break": return BREAK;
            case "continue": return CONTINUE;
            case "return": return RETURN;
            case "spawn": return SPAWN;
            case "spawn_link": return SPAWN_LINK;
            case "spawn_monitor": return SPAWN_MONITOR;
            case "receive": return RECEIVE;
            case "after": return AFTER;
            case "try": return TRY;
            case "catch": return CATCH;
            case "finally": return FINALLY;
            case "throw": return THROW;
            case "panic": return PANIC;
            case "when": return WHEN;
            case "true": return TRUE;
            case "false": return FALSE;
            case "self": return SELF;
            case "Self": return SELF_TYPE;
            case "reactive": return REACTIVE;
            case "deriving": return DERIVING;
            case "where": return WHERE;
            case "forall": return FORALL;
            case "extern": return EXTERN;
            case "test": return TEST;
            case "quote": return QUOTE;
            case "from": return FROM;
            case "as": return AS;
            case "_": return UNDERSCORE;
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

    // Set literal start (must come before comment rules)
    "#{"                            { return HASH_LBRACE; }

    // Block comment start
    "#*"                            { yybegin(S_BLOCK_COMMENT); return BLOCK_COMMENT; }

    // Line comments (# followed by anything except * or {)
    "#" [^*{\r\n] [^\r\n]*         { return COMMENT; }
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

    // Two-char operators (must come before single-char)
    "++"                            { return PLUS_PLUS; }
    "::"                            { return COLON_COLON; }
    "->"                            { return ARROW; }
    "<-"                            { return SEND_OP; }
    "<="                            { return LE; }
    ">="                            { return GE; }
    "=="                            { return EQ_EQ; }
    "!="                            { return BANG_EQ; }
    "&&"                            { return AMP_AMP; }
    "||"                            { return PIPE_PIPE; }
    "**"                            { return STAR_STAR; }
    "+="                            { return PLUS_EQ; }
    "=>"                            { return FAT_ARROW; }
    "|>"                            { return PIPE_GT; }
    "-="                            { return MINUS_EQ; }
    "*="                            { return STAR_EQ; }
    "/="                            { return SLASH_EQ; }
    ".."                            { return DOT_DOT; }
    "%{"                            { return PERCENT_LBRACE; }

    // Single-char operators
    "+"                             { return PLUS; }
    "-"                             { return MINUS; }
    "*"                             { return STAR; }
    "/"                             { return SLASH; }
    "%"                             { return PERCENT; }
    "="                             { return EQ; }
    "<"                             { return LT; }
    ">"                             { return GT; }
    "!"                             { return BANG; }
    "."                             { return DOT; }
    "|"                             { return PIPE; }
    ","                             { return COMMA; }
    ";"                             { return SEMICOLON; }
    ":"                             { return COLON; }
    "@"                             { return AT; }
    "~"                             { return TILDE; }
    "^"                             { return CARET; }
    "&"                             { return AMP; }
    "?"                             { return QUESTION; }
    "\\"                            { return BACKSLASH; }

    // Brackets
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
