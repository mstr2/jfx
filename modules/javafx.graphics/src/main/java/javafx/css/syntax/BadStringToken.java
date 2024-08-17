package javafx.css.syntax;

import com.sun.javafx.css.syntax.CssPreservedToken;

public record BadStringToken(int line, int column) implements ComponentValue, CssPreservedToken {
    @Override
    public String toString() {
        return "<bad-string>";
    }

    @Override
    public int hashCode() {
        return BadStringToken.class.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BadStringToken;
    }
}
