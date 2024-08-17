package javafx.css.syntax;

import com.sun.javafx.css.syntax.CssPreservedToken;

public sealed interface NumericToken extends ComponentValue, CssPreservedToken
        permits NumberToken, PercentageToken, DimensionToken {
    Number value();
}
