package javafx.css;

import com.sun.javafx.css.parser.CssDeclaredValue;
import com.sun.javafx.css.parser.CssSelectorParser;
import com.sun.javafx.css.syntax.CssDeclaration;
import com.sun.javafx.css.syntax.CssDeclarationLike;
import com.sun.javafx.css.syntax.CssSyntaxParser;
import com.sun.javafx.css.syntax.CssQualifiedRule;
import com.sun.javafx.css.syntax.CssTokenizer;
import javafx.css.syntax.Block;
import javafx.css.syntax.ComponentValue;
import javafx.css.syntax.WhitespaceToken;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class NewCssParser {

    private NewCssParser() {}

    public static Stylesheet parse(String stylesheetText) {
        try {
            return parse(
                new ByteArrayInputStream(stylesheetText.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8,
                null);
        } catch (IOException ex) {
            return null;
        }
    }

    public static Stylesheet parse(URL url) throws IOException {
        String path = url != null ? url.toExternalForm() : null;

        if (url != null) {
            try (var stream = url.openStream()) {
                return parse(stream, StandardCharsets.UTF_8, path);
            }
        } else {
            return new Stylesheet();
        }
    }

    private static Stylesheet parse(InputStream input, Charset charset, String path) throws IOException {
        var tokenizer = new CssTokenizer(input, charset);
        var syntaxParser = new CssSyntaxParser();
        var selectorParser = new CssSelectorParser();
        var tokens = tokenizer.tokenize();
        var rules = syntaxParser.parseRuleList(tokens);
        var styleRules = new ArrayList<Rule>();

        for (var rule : rules) {
            if (rule instanceof CssQualifiedRule qualifiedRule) {
                List<Selector> selectors = selectorParser.parseSelectors(qualifiedRule.prelude());
                if (selectors.isEmpty()) {
                    continue;
                }

                List<Declaration> declarations = parseDeclarations(syntaxParser, qualifiedRule);
                styleRules.add(new Rule(selectors, declarations));
            }
        }

        var stylesheet = new Stylesheet(path);
        stylesheet.getRules().addAll(styleRules);
        return stylesheet;
    }

    private static List<Declaration> parseDeclarations(CssSyntaxParser syntaxParser, CssQualifiedRule rule) {
        var declarations = new ArrayList<Declaration>();

        for (CssDeclarationLike decl : syntaxParser.parseDeclarationList(rule.block())) {
            if (decl instanceof CssDeclaration declaration) {
                declarations.add(
                    new Declaration(
                        declaration.name(),
                        parseDeclaredValue(declaration.values()),
                        declaration.important()));
            }
        }

        return declarations;
    }

    private static Block parseDeclaredValue(List<ComponentValue> values) {
        ComponentValue[] result = new ComponentValue[values.size()];
        int length = 0;

        for (ComponentValue value : values) {
            if (!(value instanceof WhitespaceToken)) {
                result[length++] = value;
            }
        }

        return CssDeclaredValue.of(result, length);
    }
}
