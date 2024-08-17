package com.sun.javafx.css.syntax;

public record CssParserError(int line, int column, String message) {
    public static CssParserError unexpectedEndOfFile() {
        return new CssParserError(-1, -1, "Unexpected end of file");
    }

    public static CssParserError invalidEscape(int line, int column) {
        return new CssParserError(line, column, "Invalid escape sequence");
    }

    public static CssParserError unexpectedToken(CssRawToken token) {
        return new CssParserError(token.line(), token.column(), "Unexpected token: " + token);
    }

    public static CssParserError requiredToken(CssRawToken token) {
        return new CssParserError(token.line(), token.column(), "Expected: " + token);
    }

    public static CssParserError badUrl(int line, int column) {
        return new CssParserError(line, column, "Bad URL");
    }

    @Override
    public String toString() {
        return String.format("%s [%d:%d]", message, line, column);
    }
}
