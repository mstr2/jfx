package com.sun.javafx.css.syntax;

public record CssLeftBracketToken(int line, int column) implements CssRawToken {
    @Override
    public String toString() {
        return "<[>";
    }
}
