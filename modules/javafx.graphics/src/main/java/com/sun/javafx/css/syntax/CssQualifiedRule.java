package com.sun.javafx.css.syntax;

import javafx.css.syntax.ComponentValue;
import javafx.css.syntax.CurlyBlock;
import java.util.List;

public record CssQualifiedRule(List<ComponentValue> prelude,
                               CurlyBlock block,
                               int line, int column)
        implements CssRule {}
