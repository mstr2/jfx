package javafx.css.syntax;

import com.sun.javafx.css.syntax.CssPreservedToken;
import java.util.Objects;

public record AtKeywordToken(String value, int line, int column) implements ComponentValue, CssPreservedToken {
    @Override
    public String toString() {
        return "<at-keyword>" + value;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof AtKeywordToken token && Objects.equals(token.value, value);
    }
}
