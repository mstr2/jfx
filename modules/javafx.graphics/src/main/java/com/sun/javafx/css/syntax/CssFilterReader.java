package com.sun.javafx.css.syntax;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

/**
 * Filters the input stream to normalize line endings and replace {@code U+0000 NULL} and
 * surrogate code points with {@code U+FFFD REPLACEMENT CHARACTER}.
 *
 * @see <a href="https://www.w3.org/TR/css-syntax-3/#input-preprocessing">Preprocessing the input stream</a>
 */
public final class CssFilterReader extends InputStreamReader {

    public CssFilterReader(InputStream in, Charset cs) {
        super(in, cs);
    }

    private int nextValue = -1;

    @Override
    public int read() throws IOException {
        int value;

        if (nextValue >= 0) {
            value = nextValue;
            nextValue = -1;
        } else {
            value = super.read();
        }

        if (value == '\0' || Character.isSupplementaryCodePoint(value)) {
            return CssDefinitions.REPLACEMENT_CHARACTER;
        }

        if (value == CssDefinitions.FORM_FEED) {
            return CssDefinitions.LINE_FEED;
        }

        if (value == CssDefinitions.CARRIAGE_RETURN) {
            int secondValue = super.read();
            if (secondValue != CssDefinitions.LINE_FEED) {
                nextValue = secondValue;
            }

            return CssDefinitions.LINE_FEED;
        }

        return value;
    }
}
