package javafx.css.syntax;

import com.sun.javafx.css.syntax.CssPreservedToken;

public record RightParenToken(int line, int column) implements ComponentValue, CssPreservedToken {
    @Override
    public String toString() {
        return "<)>";
    }

    @Override
    public int hashCode() {
        return RightParenToken.class.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof RightParenToken;
    }
}
