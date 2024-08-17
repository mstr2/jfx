package javafx.css.syntax;

import com.sun.javafx.css.syntax.CssPreservedToken;
import java.util.Objects;

public record HashToken(String value, Type type, int line, int column) implements ComponentValue, CssPreservedToken {
    public enum Type {ID, UNRESTRICTED}

    @Override
    public String toString() {
        return "<hash>" + value;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value) + 31 * Objects.hashCode(type);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof HashToken token
            && token.type == type
            && Objects.equals(token.value, value);
    }
}
