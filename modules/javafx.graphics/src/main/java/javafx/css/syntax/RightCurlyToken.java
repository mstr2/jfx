package javafx.css.syntax;

import com.sun.javafx.css.syntax.CssPreservedToken;

public record RightCurlyToken(int line, int column) implements ComponentValue, CssPreservedToken {
    @Override
    public String toString() {
        return "<}>";
    }

    @Override
    public int hashCode() {
        return RightCurlyToken.class.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof RightCurlyToken;
    }
}
