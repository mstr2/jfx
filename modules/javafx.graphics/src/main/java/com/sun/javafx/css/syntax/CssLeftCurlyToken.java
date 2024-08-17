package com.sun.javafx.css.syntax;

public record CssLeftCurlyToken(int line, int column) implements CssRawToken {
    @Override
    public String toString() {
        return "<{>";
    }
}
