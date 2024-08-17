package javafx.css.syntax;

import java.util.Objects;

public record NumberToken(Number value, int line, int column) implements NumericToken {
    @Override
    public String toString() {
        return "<number>" + value;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof NumberToken token && Objects.equals(token.value, value);
    }
}
