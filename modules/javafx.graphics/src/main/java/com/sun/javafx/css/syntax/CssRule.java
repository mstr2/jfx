package com.sun.javafx.css.syntax;

public sealed interface CssRule permits CssAtRule, CssQualifiedRule {
    int line();
    int column();
}
