package javafx.css.syntax;

import com.sun.javafx.css.syntax.CssPreservedToken;

public record CDOToken(int line, int column) implements ComponentValue, CssPreservedToken {
    @Override
    public String toString() {
        return "<CDO>";
    }

    @Override
    public int hashCode() {
        return CDOToken.class.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CDOToken;
    }
}
