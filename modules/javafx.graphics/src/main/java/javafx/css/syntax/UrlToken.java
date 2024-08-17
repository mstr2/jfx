package javafx.css.syntax;

import com.sun.javafx.css.syntax.CssPreservedToken;
import java.util.Objects;

public record UrlToken(String value, int line, int column) implements ComponentValue, CssPreservedToken {
    @Override
    public String toString() {
        return "<url>" + value;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof UrlToken token && Objects.equals(token.value, value);
    }
}
