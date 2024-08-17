package com.sun.javafx.css.syntax;

import javafx.css.syntax.ComponentValue;
import java.util.List;

public record CssDeclaration(String name,
                             List<ComponentValue> values,
                             boolean important,
                             int line, int column)
        implements CssDeclarationLike {}
