package javafx.css;

import java.util.Map;

public interface StyleCompositor<T> {
    T compose(Map<CssMetaData<? extends Styleable, ?>, Object> convertedValues);
    Map<CssMetaData<? extends Styleable, ?>, Object> decompose(T value);
}
