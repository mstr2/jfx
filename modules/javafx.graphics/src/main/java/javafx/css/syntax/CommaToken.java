package javafx.css.syntax;

import com.sun.javafx.css.syntax.CssPreservedToken;

public record CommaToken(int line, int column) implements ComponentValue, CssPreservedToken {
    @Override
    public String toString() {
        return "<comma>";
    }

    @Override
    public int hashCode() {
        return CommaToken.class.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CommaToken;
    }
}
