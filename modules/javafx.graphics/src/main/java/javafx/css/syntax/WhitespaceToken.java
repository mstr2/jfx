package javafx.css.syntax;

import com.sun.javafx.css.syntax.CssPreservedToken;

public record WhitespaceToken(int line, int column) implements ComponentValue, CssPreservedToken {
    @Override
    public String toString() {
        return "<whitespace>";
    }

    @Override
    public int hashCode() {
        return WhitespaceToken.class.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof WhitespaceToken;
    }
}
