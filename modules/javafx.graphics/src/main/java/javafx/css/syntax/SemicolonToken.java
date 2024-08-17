package javafx.css.syntax;

import com.sun.javafx.css.syntax.CssPreservedToken;

public record SemicolonToken(int line, int column) implements ComponentValue, CssPreservedToken {
    @Override
    public String toString() {
        return "<semicolon>";
    }

    @Override
    public int hashCode() {
        return SemicolonToken.class.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SemicolonToken;
    }
}
