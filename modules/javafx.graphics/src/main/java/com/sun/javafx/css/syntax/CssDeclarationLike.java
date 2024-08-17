package com.sun.javafx.css.syntax;

public sealed interface CssDeclarationLike
        extends CssRawToken
        permits CssDeclaration, CssAtRule {}
