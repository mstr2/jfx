package javafx.css.syntax;

import com.sun.javafx.css.syntax.CssPreservedToken;

public record CDCToken(int line, int column) implements ComponentValue, CssPreservedToken {
    @Override
    public String toString() {
        return "<CDC>";
    }

    @Override
    public int hashCode() {
        return CDCToken.class.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CDCToken;
    }
}
