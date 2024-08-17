package javafx.css.syntax;

import com.sun.javafx.css.syntax.CssPreservedToken;

public record BadUrlToken(int line, int column) implements ComponentValue, CssPreservedToken {
    @Override
    public String toString() {
        return "<bad-url>";
    }

    @Override
    public int hashCode() {
        return BadUrlToken.class.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BadUrlToken;
    }
}
