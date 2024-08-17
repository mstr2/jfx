package com.sun.javafx.css.syntax;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static com.sun.javafx.css.syntax.CssDefinitions.*;

public final class CssStreamReader extends PushbackReader {

    public static final int MAX_PUSHBACK_SIZE = 4;

    private final List<Integer> lineLengths = new ArrayList<>();
    private int currentCodePoint = -1;
    private int currentColumn = -1;
    private int currentLine;

    public CssStreamReader(InputStream in, Charset cs) {
        super(new CssFilterReader(in, cs), MAX_PUSHBACK_SIZE);
    }

    public int currentColumn() {
        return currentColumn;
    }

    public int currentLine() {
        return currentLine;
    }

    public int currentCodePoint() {
        return currentCodePoint;
    }

    public void skip() throws IOException {
        read();
    }

    @Override
    public int read() throws IOException {
        if (currentCodePoint == LINE_FEED) {
            lineLengths.add(currentColumn);
            currentLine++;
            currentColumn = -1;
        }

        currentCodePoint = super.read();

        if (currentCodePoint >= 0) {
            currentColumn++;
        }

        return currentCodePoint;
    }

    public boolean consume(int... codePoints) throws IOException {
        int[] stack = new int[codePoints.length];
        int index = 0;

        for (; index < codePoints.length; index++) {
            stack[index] = read();

            if (stack[index] != codePoints[index]) {
                for (int i = index; i >= 0; --i) {
                    if (stack[i] >= 0) {
                        unread(stack[i]);
                    }
                }

                return false;
            }
        }

        return true;
    }

    @Override
    public void unread(int c) throws IOException {
        if (currentColumn-- == 0) {
            currentLine--;
            currentColumn = lineLengths.isEmpty() ? -1 : lineLengths.removeLast();
        }

        super.unread(c);
    }

    public int peek() throws IOException {
        int value = read();
        if (value >= 0) {
            unread(value);
        }

        return value;
    }

    public boolean peek(PatternType patternType) throws IOException {
        return switch (patternType.getPattern()) {
            case MonoPattern pattern -> {
                int value = super.read();

                try {
                    yield pattern.test(value);
                } finally {
                    if (value >= 0) super.unread(value);
                }
            }

            case BiPattern pattern -> {
                int value1 = super.read(), value2 = super.read();

                try {
                    yield pattern.test(value1, value2);
                } finally {
                    if (value2 >= 0) super.unread(value2);
                    if (value1 >= 0) super.unread(value1);
                }
            }

            case TriPattern pattern -> {
                int value1 = super.read(), value2 = super.read(), value3 = super.read();

                try {
                    yield pattern.test(value1, value2, value3);
                } finally {
                    if (value3 >= 0) super.unread(value3);
                    if (value2 >= 0) super.unread(value2);
                    if (value1 >= 0) super.unread(value1);
                }
            }
        };
    }
}
