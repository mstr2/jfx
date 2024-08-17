package javafx.css.syntax;

import com.sun.javafx.css.syntax.CssPreservedToken;

public record DelimToken(int codePoint, int line, int column) implements ComponentValue, CssPreservedToken {
    @Override
    public String toString() {
        return "<delim>" + Character.toString(codePoint);
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(codePoint);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof DelimToken token && token.codePoint == codePoint;
    }
}
