package com.sun.javafx.css.syntax;

import javafx.css.syntax.AtKeywordToken;
import javafx.css.syntax.ColonToken;
import javafx.css.syntax.CDCToken;
import javafx.css.syntax.CDOToken;
import javafx.css.syntax.ComponentValue;
import javafx.css.syntax.CurlyBlock;
import javafx.css.syntax.Function;
import javafx.css.syntax.ParenBlock;
import javafx.css.syntax.RightCurlyToken;
import javafx.css.syntax.RightParenToken;
import javafx.css.syntax.RightBracketToken;
import javafx.css.syntax.SimpleBlock;
import javafx.css.syntax.DelimToken;
import javafx.css.syntax.IdentToken;
import javafx.css.syntax.SemicolonToken;
import javafx.css.syntax.BracketBlock;
import javafx.css.syntax.WhitespaceToken;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * W3C-compliant CSS syntax parser, implementing CSS Syntax Module Level 3.
 *
 * @see <a href="https://www.w3.org/TR/css-syntax-3/#parsing">Parsing</a>
 */
public final class CssSyntaxParser {

    private final List<CssParserError> errors = new ArrayList<>();

    public CssSyntaxParser() {}

    /**
     * Parses a list of rules from the token stream.
     *
     * @return a list of rules
     */
    public List<CssRule> parseRuleList(List<? extends CssRawToken> tokens) {
        return consumeRuleList(new ListStream<>(tokens), true);
    }

    /**
     * Parses a list of declarations from the token stream.
     * <p>
     * Note: Despite the name, this actually parses a mixed list of declarations and at-rules, as CSS 2.1
     * does for @page. Unexpected at-rules (which could be all of them, in a given context) are invalid
     * and will be ignored by the consumer.
     *
     * @return a list of declarations
     */
    public List<CssDeclarationLike> parseDeclarationList(List<? extends CssRawToken> tokens) {
        return consumeDeclarationList(new ListStream<>(tokens));
    }

    private void error(CssParserError error) {
        errors.add(error);
    }

    /**
     * Consumes a {@link CssRule} list from the token stream.
     *
     * <pre>{@code
     *        ╭──────────────────────────────────────╮
     *        │        ┌────────────────────┐        │
     *        │     ╭──┤ <whitespace-token> ├──╮     │
     *        │     │  ╞════════════════════╡  │     │
     *     ╟──┴──╭──┼──┤  Component value   ├──┼──╮──╰──╢
     *           │  │  ╞════════════════════╡  │  │
     *           │  ╰──┤       At-rule      ├──╯  │
     *           │     └────────────────────┘     │
     *           ╰────────────────────────────────╯
     * }</pre>
     *
     * @see <a href="https://www.w3.org/TR/css-syntax-3/#consume-list-of-rules">Consume a list of rules</a>
     * @param input the token stream
     * @return the rule list
     */
    private List<CssRule> consumeRuleList(ListStream<? extends CssRawToken> input, boolean topLevel) {
        var rules = new ArrayList<CssRule>();

        while (true) {
            switch (input.consume()) {
                case WhitespaceToken ignored:
                    break;

                case AtKeywordToken ignored:
                    input.reconsume();
                    rules.add(consumeAtRule(input));
                    break;

                case null:
                    return rules;

                default:
                    if (input.current() instanceof CDOToken
                            || input.current() instanceof CDCToken) {
                        if (!topLevel) {
                            input.reconsume();
                            CssRule rule = consumeQualifiedRule(input);
                            if (rule != null) {
                                rules.add(rule);
                            }
                        }
                    } else {
                        input.reconsume();
                        CssRule rule = consumeQualifiedRule(input);
                        if (rule != null) {
                            rules.add(rule);
                        }
                    }
            }
        }
    }

    /**
     * Consumes a {@link CssRule} from the token stream.
     *
     * <pre>{@code
     *        ╭─────────────────────────────╮
     *        │     ┌─────────────────┐     │  ┌──────────┐
     *     ╟──┴──╭──┤ Component value ├──┬──╰──┤ {} block ├──╢
     *           │  └─────────────────┘  │     └──────────┘
     *           ╰───────────────────────╯
     * }</pre>
     *
     * @see <a href="https://www.w3.org/TR/css-syntax-3/#consume-qualified-rule">Consume a qualified rule</a>
     * @param stream the token stream
     * @return the qualified rule
     */
    private CssQualifiedRule consumeQualifiedRule(ListStream<? extends CssRawToken> stream) {
        var prelude = new ArrayList<ComponentValue>();

        while (true) {
            switch (stream.consume()) {
                case CssLeftCurlyToken curlyBracket: {
                    int line, column;
                    if (prelude.isEmpty()) {
                        line = curlyBracket.line();
                        column = curlyBracket.column();
                    } else {
                        line = prelude.getFirst().line();
                        column = prelude.getFirst().column();
                    }

                    return new CssQualifiedRule(
                        prelude, (CurlyBlock)consumeSimpleBlock(stream), line, column);
                }

                case CurlyBlock block: {
                    int line, column;
                    if (prelude.isEmpty()) {
                        line = block.line();
                        column = block.column();
                    } else {
                        line = prelude.getFirst().line();
                        column = prelude.getFirst().column();
                    }

                    return new CssQualifiedRule(prelude, block, line, column);
                }

                case null:
                    error(CssParserError.unexpectedEndOfFile());
                    return null;

                default:
                    stream.reconsume();
                    prelude.add(consumeComponentValue(stream));
            }
        }
    }

    /**
     * Consumes an {@link CssAtRule} from the token stream.
     *
     * <pre>{@code
     *                                ╭─────────────────────────────╮
     *        ┌────────────────────┐  │     ┌─────────────────┐     │     ┌──────────┐
     *     ╟──┤ <at-keyword-token> ├──┴──╭──┤ Component value ├──┬──╰──┬──┤ {} block ├──╭──╢
     *        └────────────────────┘     │  └─────────────────┘  │     │  └──────────┘  │
     *                                   ╰───────────────────────╯     │     ╭───╮      │
     *                                                                 ╰─────┤ ; ├──────╯
     *                                                                       ╰───╯
     * }</pre>
     *
     * @see <a href="https://www.w3.org/TR/css-syntax-3/#consume-at-rule">Consume an at-rule</a>
     * @param stream the token stream
     * @return the at-rule
     */
    private CssAtRule consumeAtRule(ListStream<? extends CssRawToken> stream) {
        var token = Objects.requireNonNull((AtKeywordToken)stream.consume());
        var prelude = new ArrayList<ComponentValue>();

        while (true) {
            switch (stream.consume()) {
                case SemicolonToken ignored:
                    return new CssAtRule(token.value(), prelude, null, token.line(), token.column());
                case null:
                    error(CssParserError.unexpectedEndOfFile());
                    return new CssAtRule(token.value(), prelude, null, token.line(), token.column());
                case CssLeftCurlyToken ignored:
                    var block = (CurlyBlock)consumeSimpleBlock(stream);
                    return new CssAtRule(token.value(), prelude, block, token.line(), token.column());
                case CurlyBlock existingBlock:
                    return new CssAtRule(token.value(), prelude, existingBlock, token.line(), token.column());
                default:
                    stream.reconsume();
                    prelude.add(consumeComponentValue(stream));
            }
        }
    }

    /**
     * Consumes a {@link SimpleBlock} from the token stream.
     *
     * <pre>{@code
     *                              ╭─────────────────────────────╮
     *        ╭──────────────────╮  │     ┌─────────────────┐     │  ╭────────────────╮
     *     ╟──┤ <starting-token> ├──┴──╭──┤ Component value ├──┬──╰──┤ <ending-token> ├──╢
     *        ╰──────────────────╯     │  └─────────────────┘  │     ╰────────────────╯
     *                                 ╰───────────────────────╯
     *
     *     where <starting-token> := { or [ or (
     *           <ending-token>   := } or ] or )  (mirror of <starting-token>)
     * }</pre>
     *
     * Note: This algorithm assumes that the current input token has already been checked to be a valid starting token.
     *
     * @see <a href="https://www.w3.org/TR/css-syntax-3/#consume-simple-block">Consume a simple block</a>
     * @param stream the token stream
     * @return the simple block
     */
    private SimpleBlock consumeSimpleBlock(ListStream<? extends CssRawToken> stream) {
        var startToken = stream.current();
        var values = new ArrayList<ComponentValue>();

        while (true) {
            switch (stream.consume()) {
                case RightParenToken ignored when startToken instanceof CssLeftParenToken:
                    return new ParenBlock(values, startToken.line(), startToken.column());

                case RightBracketToken ignored when startToken instanceof CssLeftBracketToken:
                    return new BracketBlock(values, startToken.line(), startToken.column());

                case RightCurlyToken ignored when startToken instanceof CssLeftCurlyToken:
                    return new CurlyBlock(values, startToken.line(), startToken.column());

                case null:
                    error(CssParserError.unexpectedEndOfFile());

                    return switch (startToken) {
                        case CssLeftParenToken ignored ->
                            new ParenBlock(values, startToken.line(), startToken.column());

                        case CssLeftBracketToken ignored ->
                            new BracketBlock(values, startToken.line(), startToken.column());

                        case CssLeftCurlyToken ignored ->
                            new CurlyBlock(values, startToken.line(), startToken.column());

                        default -> throw new AssertionError();
                    };

                default:
                    stream.reconsume();
                    values.add(consumeComponentValue(stream));
            }
        }
    }

    /**
     * Consumes a {@link ComponentValue} from the token stream.
     *
     * <pre>{@code
     *           ┌─────────────────┐
     *     ╟──┬──┤ Preserved token ├──┬──╢
     *        │  ╞═════════════════╡  │
     *        ├──┤    {} block     ├──┤
     *        │  ╞═════════════════╡  │
     *        ├──┤    () block     ├──┤
     *        │  ╞═════════════════╡  │
     *        ├──┤    {} block     ├──┤
     *        │  ╞═════════════════╡  │
     *        └──┤ Function block  ├──┘
     *           └─────────────────┘
     * }</pre>
     *
     * @see <a href="https://www.w3.org/TR/css-syntax-3/#consume-component-value">Consume a component value</a>
     * @param stream the token steam
     * @return the component value
     */
    private ComponentValue consumeComponentValue(ListStream<? extends CssRawToken> stream) {
        return switch (stream.consume()) {
            case CssPreservedToken token when token instanceof ComponentValue value -> value;
            case CssLeftCurlyToken ignored -> consumeSimpleBlock(stream);
            case CssLeftParenToken ignored -> consumeSimpleBlock(stream);
            case CssLeftBracketToken ignored -> consumeSimpleBlock(stream);
            case CssFunctionToken ignored -> consumeFunction(stream);
            case SimpleBlock block -> block;
            case Function function -> function;
            case null, default -> throw new AssertionError();
        };
    }

    /**
     * Consumes a {@link Function} from the token stream.
     *
     * <pre>{@code
     *                              ╭─────────────────────────────╮
     *        ┌──────────────────┐  │     ┌─────────────────┐     │  ╭───╮
     *     ╟──┤ <function-token> ├──┴──╭──┤ Component value ├──┬──╰──┤ ) ├──╢
     *        └──────────────────┘     │  └─────────────────┘  │     ╰───╯
     *                                 ╰───────────────────────╯
     * }</pre>
     *
     * Note: This algorithm assumes that the current input token has already been checked
     *       to be a {@link CssFunctionToken}.
     *
     * @see <a href="https://www.w3.org/TR/css-syntax-3/#consume-function">Consume a function</a>
     * @param stream the token stream
     * @return the function block
     */
    private Function consumeFunction(ListStream<? extends CssRawToken> stream) {
        var function = (CssFunctionToken)stream.current();
        var values = new ArrayList<ComponentValue>();

        while (true) {
            switch (stream.consume()) {
                case RightParenToken ignored:
                    return new Function(function.name(), values, function.line(), function.column());
                case null:
                    error(CssParserError.unexpectedEndOfFile());
                    return new Function(function.name(), values, function.line(), function.column());
                default:
                    stream.reconsume();
                    values.add(consumeComponentValue(stream));
            }
        }
    }

    /**
     * Consumes a list of {@link CssDeclaration} and {@link CssAtRule} from the token stream.
     *
     * <pre>{@code
     *                    ╭───────────────────╮  ╭───────────────────────────────╮
     *        ┌─────┐     │  ┌─────────────┐  │  │  ╭───╮  ┌──────────────────┐  │
     *     ╟──┤ ws* ├──┬──┴──┤ Declaration ├──╰──┴──┤ ; ├──┤ Declaration list ├──╰──╭──╢
     *        └─────┘  │     └─────────────┘        ╰───╯  └──────────────────┘     │
     *                 │             ┌─────────┐  ┌──────────────────┐              │
     *                 ╰─────────────┤ At-rule ├──┤ Declaration list ├──────────────╯
     *                               └─────────┘  └──────────────────┘
     * }</pre>
     *
     * @see <a href="https://www.w3.org/TR/css-syntax-3/#consume-list-of-declarations">Consume a list of declarations</a>
     * @param stream the token stream
     * @return the list of declarations and at-rules
     */
    private List<CssDeclarationLike> consumeDeclarationList(ListStream<? extends CssRawToken> stream) {
        var declarations = new ArrayList<CssDeclarationLike>();

        while (true) {
            switch (stream.consume()) {
                case WhitespaceToken ignored:
                    break;

                case SemicolonToken ignored:
                    break;

                case AtKeywordToken ignored:
                    stream.reconsume();
                    declarations.add(consumeAtRule(stream));
                    break;

                case IdentToken ignored:
                    var temp = new ArrayList<CssRawToken>();
                    temp.add(stream.current());

                    while (stream.peek() != null && !(stream.peek() instanceof SemicolonToken)) {
                        temp.add(consumeComponentValue(stream));
                    }

                    declarations.add(consumeDeclaration(new ListStream<>(temp)));
                    break;

                case null:
                    return declarations;

                default:
                    error(CssParserError.unexpectedToken(stream.current()));
                    stream.reconsume();
                    if (!(stream.peek() instanceof SemicolonToken)) {
                        consumeComponentValue(stream);
                    }
            }
        }
    }

    /**
     * Consumes a {@link CssDeclaration} from the token stream.
     *
     * <pre>{@code
     *                                           ╭─────────────────────────────╮
     *        ┌───────────────┐  ┌─────┐  ╭───╮  │     ┌─────────────────┐     │
     *     ╟──┤ <ident-token> ├──┤ ws* ├──┤ ; ├──┴──╭──┤ Component value ├──┬──╰──┬──────────────────╭──╢
     *        └───────────────┘  └─────┘  ╰───╯     │  └─────────────────┘  │     │  ┌────────────┐  │
     *                                              ╰───────────────────────╯     ╰──┤ !important ├──╯
     *                                                                               └────────────┘
     * }</pre>
     *
     * Note: This algorithm assumes that the next input token has already been checked to be an {@link IdentToken}.
     *
     * @see <a href="https://www.w3.org/TR/css-syntax-3/#consume-declaration">Consume a declaration</a>
     * @param stream the token stream
     * @return the declaration
     */
    private CssDeclaration consumeDeclaration(ListStream<? extends CssRawToken> stream) {
        var identToken = Objects.requireNonNull((IdentToken)stream.consume());
        String name = identToken.value();
        List<ComponentValue> values = new ArrayList<>();

        // Remove as much whitespace as possible.
        while (stream.peek() instanceof WhitespaceToken) {
            stream.consume();
        }

        if (stream.peek() == null) {
            error(CssParserError.unexpectedEndOfFile());
            return null;
        }

        // The next token must be a colon.
        if (!(stream.consume() instanceof ColonToken)) {
            error(CssParserError.requiredToken(new ColonToken(stream.current().line(), stream.current().column())));
            return null;
        }

        // Remove as much whitespace as possible.
        while (stream.peek() instanceof WhitespaceToken) {
            stream.consume();
        }

        // Consume a component value until the stream is empty.
        while (stream.peek() != null) {
            values.add(consumeComponentValue(stream));
        }

        // Remove trailing whitespace.
        while (values.getLast() instanceof WhitespaceToken) {
            values.removeLast();
        }

        boolean important = false;

        // If the last two non-whitespace tokens are "!" and "important", remove them and all whitespace
        // tokens in-between, and set the 'important' flag.
        if (values.getLast() instanceof IdentToken ident && "important".equalsIgnoreCase(ident.value())) {
            for (int i = values.size() - 2; i >= 0; --i) {
                if (values.get(i) instanceof DelimToken delim
                       && delim.codePoint() == CssDefinitions.EXCLAMATION_MARK) {
                    for (int j = values.size() - 2; j >= i; --j) {
                        values.remove(i);
                    }
                    break;
                } else if (!(values.get(i) instanceof WhitespaceToken)) {
                    break;
                }
            }
        }

        return new CssDeclaration(name, values, important, identToken.line(), identToken.column());
    }
}
