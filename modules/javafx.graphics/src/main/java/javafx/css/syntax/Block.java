package javafx.css.syntax;

import com.sun.javafx.css.parser.CssDeclaredValue;
import java.util.List;
import java.util.RandomAccess;

public sealed interface Block
        extends List<ComponentValue>, ComponentValue, RandomAccess
        permits SimpleBlock, Function, CssDeclaredValue {
    boolean containsLookup();
}
