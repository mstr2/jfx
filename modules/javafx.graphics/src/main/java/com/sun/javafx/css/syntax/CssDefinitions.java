package com.sun.javafx.css.syntax;

import java.io.IOException;

/*
 * https://www.w3.org/TR/css-syntax-3/#tokenizer-definitions
 */
public final class CssDefinitions {

    public static final char QUOTATION_MARK = '"';
    public static final char APOSTROPHE = '\'';
    public static final char NUMBER_SIGN = '#';
    public static final char LEFT_PARENTHESIS = '(';
    public static final char RIGHT_PARENTHESIS = ')';
    public static final char LEFT_SQUARE_BRACKET = '[';
    public static final char RIGHT_SQUARE_BRACKET = ']';
    public static final char LEFT_CURLY_BRACKET = '{';
    public static final char RIGHT_CURLY_BRACKET = '}';
    public static final char COMMA = ',';
    public static final char COLON = ':';
    public static final char SEMICOLON = ';';
    public static final char REPLACEMENT_CHARACTER = '\uFFFD';
    public static final char FORM_FEED = '\u000C';
    public static final char CARRIAGE_RETURN = '\r';
    public static final char LINE_FEED = '\n';
    public static final char HYPHEN_MINUS = '-';
    public static final char PLUS_SIGN = '+';
    public static final char LOW_LINE = '_';
    public static final char SOLIDUS = '/';
    public static final char REVERSE_SOLIDUS = '\\';
    public static final char ASTERISK = '*';
    public static final char AMPERSAND = '&';
    public static final char FULL_STOP = '.';
    public static final char LATIN_CAPITAL_LETTER_E = 'E';
    public static final char LATIN_SMALL_LETTER_E = 'e';
    public static final char PERCENTAGE_SIGN = '%';
    public static final char GREATER_THAN_SIGN = '>';
    public static final char LESS_THAN_SIGN = '<';
    public static final char EXCLAMATION_MARK = '?';
    public static final char COMMERCIAL_AT = '@';

    public sealed interface Pattern permits MonoPattern, BiPattern, TriPattern {}

    public non-sealed interface MonoPattern extends Pattern {
        boolean test(int codePoint) throws IOException;
    }

    public non-sealed interface BiPattern extends Pattern {
        boolean test(int codePoint1, int codePoint2) throws IOException;
    }

    public non-sealed interface TriPattern extends Pattern {
        boolean test(int codePoint1, int codePoint2, int codePoint3) throws IOException;
    }

    public enum PatternType {
        IDENT_CODE_POINT(CssDefinitions::isIdentCodePoint),

        // https://www.w3.org/TR/css-syntax-3/#starts-with-a-valid-escape
        VALID_ESCAPE(CssDefinitions::isValidEscape),

        // https://www.w3.org/TR/css-syntax-3/#would-start-an-identifier
        IDENT_SEQUENCE_START((codePoint1, codePoint2, codePoint3) -> switch (codePoint1) {
            case HYPHEN_MINUS -> codePoint2 == HYPHEN_MINUS ||
                                 isIdentStartCodePoint(codePoint2) ||
                                 isValidEscape(codePoint2, codePoint3);
            case REVERSE_SOLIDUS -> isValidEscape(codePoint1, codePoint2);
            default -> isIdentStartCodePoint(codePoint1);
        }),

        // https://www.w3.org/TR/css-syntax-3/#starts-with-a-number
        NUMBER_START((codePoint1, codePoint2, codePoint3) -> switch (codePoint1) {
            case PLUS_SIGN, HYPHEN_MINUS -> isDigit(codePoint2) ||
                                            (codePoint2 == FULL_STOP && isDigit(codePoint3));
            case FULL_STOP -> isDigit(codePoint2);
            default -> isDigit(codePoint1);
        }),

        TWO_WHITESPACE(((codePoint1, codePoint2) ->
            Character.isWhitespace(codePoint1) &&
            Character.isWhitespace(codePoint2))),

        WHITESPACE_AND_QUOTE(((codePoint1, codePoint2) ->
            Character.isWhitespace(codePoint1) &&
            (codePoint2 == QUOTATION_MARK || codePoint2 == APOSTROPHE))),

        COMMENT_DELIMITER_CLOSE((codePoint1, codePoint2) ->
            codePoint1 == HYPHEN_MINUS && codePoint2 == GREATER_THAN_SIGN),

        FULL_STOP_AND_DIGIT((codePoint1, codePoint2) ->
            codePoint1 == FULL_STOP && isDigit(codePoint2)),

        E_NOTATION_SHORT((codePoint1, codePoint2) ->
            (codePoint1 == LATIN_CAPITAL_LETTER_E || codePoint1 == LATIN_SMALL_LETTER_E) &&
            isDigit(codePoint2)),

        E_NOTATION_LONG(((codePoint1, codePoint2, codePoint3) ->
            (codePoint1 == LATIN_CAPITAL_LETTER_E || codePoint1 == LATIN_SMALL_LETTER_E) &&
            (codePoint2 == PLUS_SIGN || codePoint2 == HYPHEN_MINUS) &&
            isDigit(codePoint3)));

        PatternType(MonoPattern peek) {
            this.pattern = peek;
        }

        PatternType(BiPattern peek) {
            this.pattern = peek;
        }

        PatternType(TriPattern peek) {
            this.pattern = peek;
        }

        private final Pattern pattern;

        public Pattern getPattern() {
            return pattern;
        }
    }

    private CssDefinitions() {}

    /*
     * https://www.w3.org/TR/css-syntax-3/#starts-with-a-valid-escape
     */
    public static boolean isValidEscape(int codePoint1, int codePoint2) {
        if (codePoint1 != REVERSE_SOLIDUS) {
            return false;
        }

        return codePoint2 != LINE_FEED;
    }

    /*
     * ident code point :=
     *     An ident-start code point, a digit, or U+002D HYPHEN-MINUS (-).
     */
    public static boolean isIdentCodePoint(int codePoint) {
        return codePoint == HYPHEN_MINUS || Character.isDigit(codePoint) || isIdentStartCodePoint(codePoint);
    }

    /*
     * non-ASCII code point :=
     *     A code point with a value equal to or greater than U+0080 <control>.
     *
     * ident-start code point :=
     *     A letter, a non-ASCII code point, or U+005F LOW LINE (_).
     */
    public static boolean isIdentStartCodePoint(int codePoint) {
        return codePoint == LOW_LINE || codePoint >= '\u0080' || Character.isLetter(codePoint);
    }

    /*
     * A code point between U+0000 NULL and U+0008 BACKSPACE inclusive, or U+000B LINE TABULATION,
     * or a code point between U+000E SHIFT OUT and U+001F INFORMATION SEPARATOR ONE inclusive,
     * or U+007F DELETE.
     */
    public static boolean isNonPrintableCodePoint(int codePoint) {
        return codePoint >= '\u0000' && codePoint <= '\u0008'
            || codePoint >= '\u000E' && codePoint <= '\u001F'
            || codePoint == '\u000B'
            || codePoint == '\u007F';
    }

    /*
     * A code point between U+0030 DIGIT ZERO (0) and U+0039 DIGIT NINE (9) inclusive.
     */
    public static boolean isDigit(int codePoint) {
        return codePoint >= '0' && codePoint <= '9';
    }

    /*
     * A digit, or a code point between U+0041 LATIN CAPITAL LETTER A (A) and U+0046 LATIN CAPITAL LETTER F (F)
     * inclusive, or a code point between U+0061 LATIN SMALL LETTER A (a) and U+0066 LATIN SMALL LETTER F (f)
     * inclusive.
     */
    public static boolean isHexDigit(int codePoint) {
        return isDigit(codePoint)
            || codePoint >= 'A' && codePoint <= 'F'
            || codePoint >= 'a' && codePoint <= 'f';
    }
}
