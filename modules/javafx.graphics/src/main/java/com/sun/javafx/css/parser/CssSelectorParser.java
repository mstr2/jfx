package com.sun.javafx.css.parser;

import com.sun.javafx.css.Combinator;
import com.sun.javafx.css.syntax.ListStream;
import com.sun.javafx.css.syntax.CssParserError;
import javafx.css.CompoundSelector;
import javafx.css.Selector;
import javafx.css.SimpleSelector;
import javafx.css.syntax.ColonToken;
import javafx.css.syntax.CommaToken;
import javafx.css.syntax.ComponentValue;
import javafx.css.syntax.Function;
import javafx.css.syntax.DelimToken;
import javafx.css.syntax.HashToken;
import javafx.css.syntax.IdentToken;
import javafx.css.syntax.WhitespaceToken;
import java.util.ArrayList;
import java.util.List;

import static com.sun.javafx.css.syntax.CssDefinitions.*;
import static com.sun.javafx.css.syntax.CssParserError.*;

/**
 * Parser for JavaFX selectors.
 */
@SuppressWarnings("removal")
public final class CssSelectorParser {

    private final List<CssParserError> errors = new ArrayList<>();

    private void error(CssParserError error) {
        errors.add(error);
    }

    public List<Selector> parseSelectors(List<? extends ComponentValue> input) {
        var stream = new ListStream<>(input);
        var selectors = new ArrayList<Selector>();
        Selector selector;

        do {
            selector = parseSelector(stream);
            if (selector != null) {
                selectors.add(selector);
            }

            consumeWhitespace(stream);

            if (stream.peek() instanceof CommaToken) {
                stream.consume();
            }
        } while (selector != null);

        return selectors;
    }

    private Selector parseSelector(ListStream<? extends ComponentValue> stream) {
        List<Combinator> combinators = null;
        List<SimpleSelector> selectors = null;

        SimpleSelector ancestor = consumeSimpleSelector(stream);
        if (ancestor == null) {
            return null;
        }

        while (true) {
            Combinator combinator = consumeCombinator(stream);
            if (combinator != null) {
                if (combinators == null) {
                    combinators = new ArrayList<>();
                }

                combinators.add(combinator);
                SimpleSelector descendant = consumeSimpleSelector(stream);
                if (descendant == null) {
                    return null;
                }

                if (selectors == null) {
                    selectors = new ArrayList<>();
                    selectors.add(ancestor);
                }

                selectors.add(descendant);
            } else {
                break;
            }
        }

        if (selectors == null) {
            return ancestor;
        } else {
            return new CompoundSelector(selectors, combinators);
        }
    }

    private SimpleSelector consumeSimpleSelector(ListStream<? extends ComponentValue> stream) {
        int startIndex = stream.index();
        String elementSelector = "*";
        String idSelector = "";
        List<String> classSelectors = null;
        List<String> pseudoClassSelectors = null;

        consumeWhitespace(stream);

        while (true) {
            switch (stream.consume()) {
                case HashToken hash ->
                    idSelector = hash.value();

                case IdentToken ident ->
                    elementSelector = ident.value();

                case DelimToken delim when delim.codePoint() == ASTERISK ->
                    elementSelector = "*";

                case DelimToken delim when delim.codePoint() == FULL_STOP -> {
                    switch (stream.consume()) {
                        case IdentToken ident -> {
                            if (classSelectors == null) {
                                classSelectors = new ArrayList<>();
                            }

                            classSelectors.add(ident.value());
                        }

                        case null -> error(unexpectedEndOfFile());

                        default -> error(unexpectedToken(stream.current()));
                    }
                }

                case ColonToken ignored -> {
                    switch (stream.consume()) {
                        case IdentToken ident -> {
                            if (pseudoClassSelectors == null) pseudoClassSelectors = new ArrayList<>();
                            pseudoClassSelectors.add(ident.value());
                        }

                        case Function func -> {
                            String pseudoClass = formatFunctionalPseudoClass(func);
                            if (pseudoClass != null) {
                                if (pseudoClassSelectors == null) {
                                    pseudoClassSelectors = new ArrayList<>();
                                }

                                pseudoClassSelectors.add(pseudoClass);
                            } else {
                                stream.reset(startIndex);
                                return null;
                            }
                        }

                        case null -> error(unexpectedEndOfFile());

                        default -> error(unexpectedToken(stream.current()));
                    }
                }

                case null -> {
                    stream.reset(startIndex);
                    error(unexpectedEndOfFile());
                    return null;
                }

                default -> {
                    var current = stream.current();
                    if (current instanceof WhitespaceToken ||
                            current instanceof CommaToken ||
                            current instanceof DelimToken delim && delim.codePoint() == GREATER_THAN_SIGN) {
                        stream.reconsume();
                        return new SimpleSelector(elementSelector, classSelectors, pseudoClassSelectors, idSelector);
                    }

                    stream.reset(startIndex);
                    error(unexpectedToken(stream.current()));
                    return null;
                }
            }
        }
    }

    private Combinator consumeCombinator(ListStream<? extends ComponentValue> stream) {
        int startIndex = stream.index();
        Combinator combinator = null;

        consumeWhitespace(stream);

        while (true) {
            switch (stream.consume()) {
                case WhitespaceToken ignored -> {
                    if (combinator == null) {
                        combinator = Combinator.DESCENDANT;
                    }
                }

                case DelimToken delim when delim.codePoint() == GREATER_THAN_SIGN ->
                    combinator = Combinator.CHILD;

                case null, default -> {
                    var current = stream.current();
                    if (current instanceof ColonToken ||
                            current instanceof HashToken ||
                            current instanceof IdentToken ||
                            current instanceof DelimToken delim &&
                                (delim.codePoint() == ASTERISK || delim.codePoint() == FULL_STOP)) {
                        stream.reconsume();
                        return combinator;
                    }

                    stream.reset(startIndex);
                    return null;
                }
            }
        }
    }

    private String formatFunctionalPseudoClass(Function function) {
        var builder = new StringBuilder(function.name()).append('(');

        if (function.isEmpty()) {
            return builder.append(')').toString();
        }

        for (int i = 0; i < function.size(); ++i) {
            if (i == 0) {
                if (function.get(i) instanceof IdentToken ident) {
                    builder.append(ident.value());
                }
            } else {
                error(unexpectedToken(function.get(i)));
                return null;
            }
        }

        return builder.append(')').toString();
    }

    private void consumeWhitespace(ListStream<? extends ComponentValue> input) {
        while (input.peek() instanceof WhitespaceToken) {
            input.consume();
        }
    }
}
