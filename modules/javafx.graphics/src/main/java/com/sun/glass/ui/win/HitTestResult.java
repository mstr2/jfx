package com.sun.glass.ui.win;

public enum HitTestResult {
    CLIENT(1),
    TITLE(2),
    MIN_BUTTON(8),
    MAX_BUTTON(9),
    CLOSE_BUTTON(20);

    HitTestResult(int value) {
        this.value = value;
    }

    private final int value;

    public int value() {
        return value;
    }
}
