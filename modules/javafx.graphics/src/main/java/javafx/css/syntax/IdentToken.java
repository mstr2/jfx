package javafx.css.syntax;

import com.sun.javafx.css.syntax.CssPreservedToken;
import java.util.Objects;

public record IdentToken(String value, int line, int column) implements ComponentValue, CssPreservedToken {

    public IdentToken {
        value = value.intern();
    }

    @Override
    public String toString() {
        return "<ident>" + value;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof IdentToken token && Objects.equals(token.value, value);
    }
}
