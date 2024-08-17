package javafx.css.syntax;

import java.util.Objects;

public record DimensionToken(Number value, String unit, int line, int column) implements NumericToken {
    @Override
    public String toString() {
        return "<dimension>" + value + unit;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof DimensionToken;
    }
}
