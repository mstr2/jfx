package com.sun.javafx.css.syntax;

import javafx.css.syntax.AtKeywordToken;
import javafx.css.syntax.BadStringToken;
import javafx.css.syntax.BadUrlToken;
import javafx.css.syntax.ColonToken;
import javafx.css.syntax.CommaToken;
import javafx.css.syntax.CDCToken;
import javafx.css.syntax.CDOToken;
import javafx.css.syntax.DelimToken;
import javafx.css.syntax.DimensionToken;
import javafx.css.syntax.HashToken;
import javafx.css.syntax.IdentToken;
import javafx.css.syntax.NumberToken;
import javafx.css.syntax.PercentageToken;
import javafx.css.syntax.RightCurlyToken;
import javafx.css.syntax.RightParenToken;
import javafx.css.syntax.RightBracketToken;
import javafx.css.syntax.SemicolonToken;
import javafx.css.syntax.StringToken;
import javafx.css.syntax.UrlToken;
import javafx.css.syntax.WhitespaceToken;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static com.sun.javafx.css.syntax.CssDefinitions.*;
import static com.sun.javafx.css.syntax.CssDefinitions.PatternType.*;

/**
 * W3C-compliant CSS tokenizer, implementing CSS Syntax Module Level 3.
 *
 * @see <a href="https://www.w3.org/TR/css-syntax-3/#tokenization">Tokenization</a>
 */
public final class CssTokenizer implements AutoCloseable {

    private final CssStreamReader input;
    private final List<CssParserError> errors = new ArrayList<>();

    public CssTokenizer(InputStream input, Charset charset) {
        this.input = new CssStreamReader(input, charset);
    }

    public List<CssRawToken> tokenize() throws IOException {
        List<CssRawToken> tokens = new ArrayList<>();

        while (true) {
            CssRawToken token = consumeToken();
            if (token != null) {
                tokens.add(token);
            } else {
                break;
            }
        }

        return tokens;
    }

    @Override
    public void close() throws Exception {
        input.close();
    }

    private void error(CssParserError error) {
        errors.add(error);
    }

    /**
     * This algorithm consumes a single {@link CssRawToken} from the token stream.
     *
     * @see <a href="https://www.w3.org/TR/css-syntax-3/#consume-token">Consume a token</a>
     * @return a single token of any type
     */
    private CssRawToken consumeToken() throws IOException {
        consumeComment();

        return switch (input.read()) {
            case APOSTROPHE, QUOTATION_MARK -> consumeStringToken();
            case NUMBER_SIGN -> handleNumberSign();
            case LEFT_PARENTHESIS -> new CssLeftParenToken(input.currentLine(), input.currentColumn());
            case RIGHT_PARENTHESIS -> new RightParenToken(input.currentLine(), input.currentColumn());
            case LEFT_SQUARE_BRACKET -> new CssLeftBracketToken(input.currentLine(), input.currentColumn());
            case RIGHT_SQUARE_BRACKET -> new RightBracketToken(input.currentLine(), input.currentColumn());
            case LEFT_CURLY_BRACKET -> new CssLeftCurlyToken(input.currentLine(), input.currentColumn());
            case RIGHT_CURLY_BRACKET -> new RightCurlyToken(input.currentLine(), input.currentColumn());
            case COMMA -> new CommaToken(input.currentLine(), input.currentColumn());
            case COLON -> new ColonToken(input.currentLine(), input.currentColumn());
            case SEMICOLON -> new SemicolonToken(input.currentLine(), input.currentColumn());
            case PLUS_SIGN -> handlePlusSign();
            case HYPHEN_MINUS -> handleMinusSign();
            case FULL_STOP -> handleFullStop();
            case LESS_THAN_SIGN -> handleLessThanSign();
            case COMMERCIAL_AT -> handleCommercialAt();
            case REVERSE_SOLIDUS -> handleReverseSolidus();
            default -> handleOtherCodePoints();
        };
    }

    /*
     * If the next input code point is an ident code point or the next two input code points are a valid escape, then:
     *   1. Create a <hash-token>.
     *   2. If the next 3 input code points would start an ident sequence, set the <hash-token>'s type flag to "id".
     *   3. Consume an ident sequence, and set the <hash-token>'s value to the returned string.
     *   4. Return the <hash-token>.
     * Otherwise, return a <delim-token> with its value set to the current input code point.
     */
    private CssRawToken handleNumberSign() throws IOException {
        if (input.peek(IDENT_CODE_POINT) || input.peek(VALID_ESCAPE)) {
            if (input.peek(IDENT_SEQUENCE_START)) {
                int line = input.currentLine(), column = input.currentColumn();
                String value = consumeIdentSequence();
                return new HashToken(value, HashToken.Type.ID, line, column);
            }
        }

        return new DelimToken(input.currentCodePoint(), input.currentLine(), input.currentColumn());
    }

    /*
     * If the input stream starts with a number, reconsume the current input code point,
     * consume a numeric token, and return it.
     *
     * Otherwise, return a <delim-token> with its value set to the current input code point.
     */
    private CssRawToken handlePlusSign() throws IOException {
        if (input.peek(NUMBER_START)) {
            input.unread(input.currentCodePoint());
            return consumeNumericToken();
        }

        return new DelimToken(input.currentCodePoint(), input.currentLine(), input.currentColumn());
    }

    /*
     * If the input stream starts with a number, reconsume the current input code point, consume
     * a numeric token, and return it.
     *
     * Otherwise, return a <delim-token> with its value set to the current input code point.
     */
    private CssRawToken handleFullStop() throws IOException {
        if (input.peek(NUMBER_START)) {
            input.unread(input.currentCodePoint());
            return consumeNumericToken();
        }

        return new DelimToken(input.currentCodePoint(), input.currentLine(), input.currentColumn());
    }

    /*
     * If the next 3 input code points are U+0021 EXCLAMATION MARK U+002D HYPHEN-MINUS U+002D HYPHEN-MINUS (!--),
     * consume them and return a <CDO-token>.
     *
     * Otherwise, return a <delim-token> with its value set to the current input code point.
     */
    private CssRawToken handleLessThanSign() throws IOException {
        int line = input.currentLine(), column = input.currentColumn();

        if (input.consume(EXCLAMATION_MARK, HYPHEN_MINUS, HYPHEN_MINUS)) {
            return new CDOToken(line, column);
        }

        return new DelimToken(input.currentCodePoint(), line, column);
    }

    /*
     * If the next 3 input code points would start an ident sequence, consume an ident sequence, create
     * an <at-keyword-token> with its value set to the returned value, and return it.
     *
     * Otherwise, return a <delim-token> with its value set to the current input code point.
     */
    private CssRawToken handleCommercialAt() throws IOException {
        int line = input.currentLine(), column = input.currentColumn();

        if (input.peek(IDENT_SEQUENCE_START)) {
            String value = consumeIdentSequence();
            return new AtKeywordToken(value, line, column);
        }

        return new DelimToken(input.currentCodePoint(), line, column);
    }

    /*
     * If the input stream starts with a valid escape, reconsume the current input code point,
     * consume an ident-like token, and return it.
     *
     * Otherwise, this is a parse error.
     * Return a <delim-token> with its value set to the current input code point.
     */
    private CssRawToken handleReverseSolidus() throws IOException {
        if (isValidEscape(REVERSE_SOLIDUS, input.peek())) {
            input.unread(REVERSE_SOLIDUS);
            return consumeIdentLikeToken();
        }

        error(CssParserError.invalidEscape(input.currentLine(), input.currentColumn()));
        return new DelimToken(input.currentCodePoint(), input.currentLine(), input.currentColumn());
    }

    /*
     * If the input stream starts with a number, reconsume the current input code point,
     * consume a numeric token, and return it.
     *
     * Otherwise, if the next 2 input code points are U+002D HYPHEN-MINUS U+003E GREATER-THAN SIGN (->),
     * consume them and return a <CDC-token>.
     *
     * Otherwise, if the input stream starts with an ident sequence, reconsume the current input code point,
     * consume an ident-like token, and return it.
     *
     * Otherwise, return a <delim-token> with its value set to the current input code point.
     */
    private CssRawToken handleMinusSign() throws IOException {
        if (input.peek(NUMBER_START)) {
            input.unread(input.currentCodePoint());
            return consumeNumericToken();
        }

        if (input.peek(COMMENT_DELIMITER_CLOSE)) {
            return new CDCToken(input.currentLine(), input.currentColumn());
        }

        if (input.peek(IDENT_SEQUENCE_START)) {
            input.unread(input.currentCodePoint());
            return consumeIdentLikeToken();
        }

        return new DelimToken(input.currentCodePoint(), input.currentLine(), input.currentColumn());
    }

    private CssRawToken handleOtherCodePoints() throws IOException {
        if (input.currentCodePoint() < 0) {
            return null;
        }

        if (Character.isWhitespace(input.currentCodePoint())) {
            int line = input.currentLine(), column = input.currentColumn();
            int next = input.peek();

            while (Character.isWhitespace(next)) {
                input.skip();
                next = input.peek();
            }

            return new WhitespaceToken(line, column);
        }

        if (isDigit(input.currentCodePoint())) {
            input.unread(input.currentCodePoint());
            return consumeNumericToken();
        }

        if (isIdentStartCodePoint(input.currentCodePoint())) {
            input.unread(input.currentCodePoint());
            return consumeIdentLikeToken();
        }

        return new DelimToken(input.currentCodePoint(), input.currentLine(), input.currentColumn());
    }

    /**
     * This algorithm returns an escaped code point.
     * <p>
     * Note: This algorithm assumes that the U+005C REVERSE SOLIDUS (\) has already been consumed and
     *       that the next input code point has already been verified to be part of a valid escape.
     *
     * @see <a href="https://www.w3.org/TR/css-syntax-3/#consume-escaped-code-point">Consume an escaped code point</a>
     */
    private int consumeEscapedCodePoint() throws IOException {
        int codePoint = input.read();

        if (isHexDigit(codePoint)) {
            var builder = new StringBuilder(6).appendCodePoint(codePoint);

            for (int i = 0; i < 5; ++i) {
                int next = input.read();

                if (next < 0) {
                    break;
                } else if (isHexDigit(next)) {
                    builder.appendCodePoint(next);
                } else if (!Character.isWhitespace(next)) {
                    input.unread(next);
                }
            }

            int value = Integer.valueOf(builder.toString(), 16);

            return value == 0
                || Character.isSupplementaryCodePoint(value)
                || value > Character.MAX_CODE_POINT ? REPLACEMENT_CHARACTER : value;
        }

        if (codePoint < 0) {
            error(CssParserError.unexpectedEndOfFile());
            return REPLACEMENT_CHARACTER;
        }

        return codePoint;
    }

    /**
     * This algorithm returns a string containing the largest name that can be formed from adjacent code
     * points in the stream, starting from the first.
     * <p>
     * Note: This algorithm does not do the verification of the first few code points that are necessary to
     *       ensure the returned code points would constitute an {@link IdentToken}. If that is the
     *       intended use, ensure that the stream starts with an ident sequence before calling this algorithm.
     *
     * @see <a href="https://www.w3.org/TR/css-syntax-3/#consume-name">Consume an ident sequence</a>
     * @return an ident sequence
     */
    private String consumeIdentSequence() throws IOException {
        var builder = new StringBuilder();
        int codePoint = input.read();

        while (codePoint >= 0) {
            if (isIdentCodePoint(codePoint)) {
                builder.appendCodePoint(codePoint);
            } else {
                input.unread(codePoint);

                if (input.peek(VALID_ESCAPE)) {
                    input.consume(REVERSE_SOLIDUS);
                    builder.appendCodePoint(consumeEscapedCodePoint());
                } else {
                    break;
                }
            }

            codePoint = input.read();
        }

        return builder.toString();
    }

    /**
     * This algorithm returns an {@link IdentToken}, {@link CssFunctionToken},
     * {@link UrlToken}, or {@link BadUrlToken}.
     *
     * @see <a href="https://www.w3.org/TR/css-syntax-3/#consume-ident-like-token">Consume an ident-like token</a>
     * @return a {@code IdentToken}, {@code FunctionToken}, {@code UrlToken}, or {@code BadUrlToken}
     */
    private CssRawToken consumeIdentLikeToken() throws IOException {
        int line = input.currentLine(), column = input.currentColumn();
        String result = consumeIdentSequence();

        if ("url".equalsIgnoreCase(result) && input.consume(LEFT_PARENTHESIS)) {
            while (input.peek(TWO_WHITESPACE)) {
                input.skip(); // consume one whitespace character
            }

            int codePoint = input.peek();

            if (codePoint == QUOTATION_MARK || codePoint == APOSTROPHE || input.peek(WHITESPACE_AND_QUOTE)) {
                return new CssFunctionToken(result, line, column);
            }

            return consumeUrlToken();
        }

        if (input.consume(LEFT_PARENTHESIS)) {
            return new CssFunctionToken(result, line, column);
        }

        return new IdentToken(result, line, column);
    }

    /**
     * This algorithm returns either a {@link UrlToken} or a {@link BadUrlToken}.
     * <p>
     * Note: This algorithm assumes that the initial "url(" has already been consumed. This algorithm also assumes
     *       that it's being called to consume an "unquoted" value, like url(foo). A quoted value, like url("foo"),
     *       is parsed as a {@link CssFunctionToken}. {@link #consumeIdentLikeToken()} automatically handles
     *       this distinction; this algorithm shouldn't be called directly otherwise.
     *
     * @see <a href="https://www.w3.org/TR/css-syntax-3/#consume-url-token">Consume a URL token</a>
     * @return a {@code UrlToken} or a {@code BadUrlToken}
     */
    private CssRawToken consumeUrlToken() throws IOException {
        class LocalMethods {
            static CssRawToken badUrl(CssTokenizer tokenizer, int line, int column) throws IOException {
                tokenizer.error(CssParserError.badUrl(line, column));
                tokenizer.consumeBadUrl();
                return new BadUrlToken(line, column);
            }

            static void consumeWhitespace(CssTokenizer tokenizer) throws IOException {
                while (Character.isWhitespace(tokenizer.input.peek())) {
                    tokenizer.input.skip();
                }
            }
        }

        var builder = new StringBuilder();
        LocalMethods.consumeWhitespace(this);
        int line = input.currentLine(), column = input.currentColumn();

        while (true) {
            switch (input.read()) {
                case RIGHT_PARENTHESIS:
                    return new UrlToken(builder.toString(), line, column);

                case QUOTATION_MARK, APOSTROPHE, LEFT_PARENTHESIS:
                    return LocalMethods.badUrl(this, line, column);

                default:
                    if (Character.isWhitespace(input.currentCodePoint())) {
                        LocalMethods.consumeWhitespace(this);
                        int next = input.peek();

                        if (next == RIGHT_PARENTHESIS) {
                            return new UrlToken(builder.toString(), line, column);
                        } else if (next < 0) {
                            error(CssParserError.unexpectedEndOfFile());
                            return new UrlToken(builder.toString(), line, column);
                        }

                        return LocalMethods.badUrl(this, line, column);
                    } else if (input.currentCodePoint() == REVERSE_SOLIDUS) {
                        if (isValidEscape(REVERSE_SOLIDUS, input.peek())) {
                            input.unread(REVERSE_SOLIDUS);
                            builder.appendCodePoint(consumeEscapedCodePoint());
                        } else {
                            return LocalMethods.badUrl(this, line, column);
                        }
                    } else if (isNonPrintableCodePoint(input.currentCodePoint())) {
                        return LocalMethods.badUrl(this, line, column);
                    } else {
                        builder.appendCodePoint(input.currentCodePoint());
                    }
            }
        }
    }

    /**
     * This algorithm consumes the remnants of a bad URL from a stream of code points, "cleaning up" after
     * the tokenizer realizes that it's in the middle of a {@link BadUrlToken} rather than a
     * {@link UrlToken}. It returns nothing; its sole use is to consume enough of the input stream
     * to reach a recovery point where normal tokenizing can resume.
     *
     * @see <a href="https://www.w3.org/TR/css-syntax-3/#consume-remnants-of-bad-url">Consume a bad URL</a>
     */
    private void consumeBadUrl() throws IOException {
        do {
            if (input.peek(VALID_ESCAPE)) {
                consumeEscapedCodePoint();
            } else {
                int codePoint = input.read();

                if (codePoint < 0 || codePoint == RIGHT_PARENTHESIS) {
                    break;
                }
            }
        } while (true);
    }

    /**
     * This algorithm returns either a {@link StringToken} or {@link BadStringToken}.
     * <p>
     * Note: the current code point of the token stream is assumed to be the string delimiter, i.e.
     *       APOSTROPHE or QUOTATION MARK.
     *
     * @see <a href="https://www.w3.org/TR/css-syntax-3/#consume-string-token">Consume a string token</a>
     * @return a string token
     */
    private CssRawToken consumeStringToken() throws IOException {
        int endingCodePoint = input.currentCodePoint();
        int line = input.currentLine(), column = input.currentColumn();
        var builder = new StringBuilder();

        do {
            int codePoint = input.read();

            if (codePoint < 0) {
                error(CssParserError.unexpectedEndOfFile());
                return new StringToken(builder.toString(), line, column);
            }

            if (codePoint == endingCodePoint) {
                return new StringToken(builder.toString(), line, column);
            }

            if (codePoint == LINE_FEED) {
                error(CssParserError.unexpectedEndOfFile());
                input.unread(codePoint);
                return new BadStringToken(line, column);
            }

            if (codePoint == REVERSE_SOLIDUS) {
                int next = input.peek();

                if (next == LINE_FEED) {
                    input.skip();
                } else if (input.peek(VALID_ESCAPE)) {
                    input.skip();
                    builder.appendCodePoint(consumeEscapedCodePoint());
                }
            } else {
                builder.appendCodePoint(codePoint);
            }
        } while (true);
    }

    /**
     * This algorithm returns a number, which is either {@link Integer} or {@link Double}.
     * <p>
     * Note: This algorithm does not do the verification of the first few code points that are necessary to
     *       ensure a number can be obtained from the stream. Ensure that the stream starts with a number
     *       before calling this algorithm.
     *
     * @see <a href="https://www.w3.org/TR/css-syntax-3/#consume-number">Consume a number</a>
     * @return a {@code Number}
     */
    private Number consumeNumber() throws IOException {
        class LocalMethods {
            static void consumePlusOrMinusSign(CssStreamReader input, StringBuilder builder) throws IOException {
                int codePoint = input.read();

                if (codePoint == PLUS_SIGN || codePoint == HYPHEN_MINUS) {
                    builder.appendCodePoint(codePoint);
                } else {
                    input.unread(codePoint);
                }
            }

            static void consumeDigits(CssStreamReader input, StringBuilder builder) throws IOException {
                while (true) {
                    int codePoint = input.read();

                    if (isDigit(codePoint)) {
                        builder.appendCodePoint(codePoint);
                    } else {
                        input.unread(codePoint);
                        break;
                    }
                }
            }
        }

        boolean isInteger = true;
        var builder = new StringBuilder();
        LocalMethods.consumePlusOrMinusSign(input, builder);
        LocalMethods.consumeDigits(input, builder);

        if (input.peek(FULL_STOP_AND_DIGIT)) {
            builder.appendCodePoint(input.read())
                   .appendCodePoint(input.read());
            isInteger = false;
            LocalMethods.consumeDigits(input, builder);
        }

        if (input.peek(E_NOTATION_LONG)) {
            builder.appendCodePoint(input.read())
                   .appendCodePoint(input.read())
                   .appendCodePoint(input.read());
            isInteger = false;
            LocalMethods.consumeDigits(input, builder);
        } else if (input.peek(E_NOTATION_SHORT)) {
            builder.appendCodePoint(input.read())
                   .appendCodePoint(input.read());
            isInteger = false;
            LocalMethods.consumeDigits(input, builder);
        }

        if (isInteger) {
            return Integer.valueOf(builder.toString());
        }

        return Double.valueOf(builder.toString());
    }

    /**
     * This algorithm returns either a {@link NumberToken}, {@link PercentageToken},
     * or {@link DimensionToken}.
     * <p>
     * Note: This algorithm does not do the verification of the first few code points that are necessary to
     *       ensure a number can be obtained from the stream. Ensure that the stream starts with a number
     *       before calling this algorithm.
     *
     * @see <a href="https://www.w3.org/TR/css-syntax-3/#consume-numeric-token">Consume a numeric token</a>
     * @return a numeric token
     */
    private CssRawToken consumeNumericToken() throws IOException {
        int line = input.currentLine(), column = input.currentColumn();
        Number number = consumeNumber();

        if (input.peek(IDENT_SEQUENCE_START)) {
            String unit = consumeIdentSequence();
            return new DimensionToken(number, unit, line, column);
        }

        if (input.consume(PERCENTAGE_SIGN)) {
            return new PercentageToken(number, line, column);
        }

        return new NumberToken(number, line, column);
    }

    /**
     * Consumes comments from the tokens stream.
     *
     * @see <a href="https://www.w3.org/TR/css-syntax-3/#consume-comment">Consume comments</a>
     */
    private void consumeComment() throws IOException {
        while (input.consume(SOLIDUS, ASTERISK)) {
            int next = input.read();

            while (next >= 0) {
                if (next != ASTERISK) {
                    next = input.read();
                } else if (!input.consume(SOLIDUS)) {
                    next = input.read();
                } else {
                    break;
                }
            }

            if (input.currentCodePoint() < 0) {
                error(CssParserError.unexpectedEndOfFile());
                return;
            }
        }
    }
}
