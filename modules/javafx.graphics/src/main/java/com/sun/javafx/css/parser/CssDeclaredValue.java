package com.sun.javafx.css.parser;

import javafx.css.syntax.Block;
import javafx.css.syntax.ComponentValue;
import java.util.AbstractList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

public non-sealed abstract class CssDeclaredValue extends AbstractList<ComponentValue> implements Block {

    public static CssDeclaredValue of() {
        return new CssDeclaredValue() {
            @Override
            public ComponentValue get(int index) {
                throw new NoSuchElementException();
            }

            @Override
            public int size() {
                return 0;
            }

            @Override
            public boolean containsLookup() {
                return false;
            }

            @Override
            public int line() {
                return -1;
            }

            @Override
            public int column() {
                return -1;
            }

            @Override
            public int hashCode() {
                return 0;
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof CssDeclaredValue v && v.isEmpty();
            }
        };
    }

    public static CssDeclaredValue of(ComponentValue value) {
        return new CssDeclaredValue() {
            final boolean containsLookup = value instanceof Block block && block.containsLookup();

            @Override
            public ComponentValue get(int index) {
                Objects.checkIndex(index, 1);
                return value;
            }

            @Override
            public int size() {
                return 1;
            }

            @Override
            public boolean containsLookup() {
                return containsLookup;
            }

            @Override
            public int line() {
                return value.line();
            }

            @Override
            public int column() {
                return value.column();
            }

            @Override
            public int hashCode() {
                return value.hashCode();
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof CssDeclaredValue v
                    && v.size() == 1
                    && Objects.equals(value, v.getFirst());
            }
        };
    }

    public static CssDeclaredValue of(List<ComponentValue> values) {
        return new CssDeclaredValue() {
            private final int hash;
            private final boolean containsLookup;

            {
                int hash = 1;
                boolean containsLookup = false;

                for (ComponentValue value : values) {
                    hash += 31 * value.hashCode();

                    if (!containsLookup && value instanceof Block block && block.containsLookup()) {
                        containsLookup = true;
                    }
                }

                this.hash = hash;
                this.containsLookup = containsLookup;
            }

            @Override
            public ComponentValue get(int index) {
                return values.get(index);
            }

            @Override
            public int size() {
                return values.size();
            }

            @Override
            public boolean containsLookup() {
                return containsLookup;
            }

            @Override
            public int line() {
                return values.isEmpty() ? -1 : values.getFirst().line();
            }

            @Override
            public int column() {
                return values.isEmpty() ? -1 : values.getFirst().column();
            }

            @Override
            public int hashCode() {
                return hash;
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof CssDeclaredValue v && Objects.equals(values, v);
            }
        };
    }

    public static CssDeclaredValue of(ComponentValue[] values, int length) {
        return new CssDeclaredValue() {
            private final int hash;
            private final boolean containsLookup;

            {
                int hash = 1;
                boolean containsLookup = false;

                for (ComponentValue value : values) {
                    hash += 31 * value.hashCode();

                    if (!containsLookup && value instanceof Block block && block.containsLookup()) {
                        containsLookup = true;
                    }
                }

                this.hash = hash;
                this.containsLookup = containsLookup;
            }

            @Override
            public ComponentValue get(int index) {
                Objects.checkIndex(0, length);
                return values[index];
            }

            @Override
            public int size() {
                return length;
            }

            @Override
            public boolean containsLookup() {
                return containsLookup;
            }

            @Override
            public int line() {
                return values.length == 0 ? -1 : values[0].line();
            }

            @Override
            public int column() {
                return values.length == 0 ? -1 : values[0].column();
            }

            @Override
            public int hashCode() {
                return hash;
            }

            @Override
            public boolean equals(Object o) {
                if (!(o instanceof CssDeclaredValue v) ||  values.length != v.size()) {
                    return false;
                }

                for (int i = 0; i < values.length; ++i) {
                    if (!Objects.equals(values[i], v.get(i))) {
                        return false;
                    }
                }

                return true;
            }
        };
    }

    public static CssDeclaredValue ofResolved(ComponentValue value) {
        return new CssDeclaredValue() {
            @Override
            public ComponentValue get(int index) {
                Objects.checkIndex(index, 1);
                return value;
            }

            @Override
            public int size() {
                return 1;
            }

            @Override
            public boolean containsLookup() {
                return false;
            }

            @Override
            public int line() {
                return value.line();
            }

            @Override
            public int column() {
                return value.column();
            }

            @Override
            public int hashCode() {
                return value.hashCode();
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof CssDeclaredValue v
                    && v.size() == 1
                    && Objects.equals(value, v.getFirst());
            }
        };
    }

    public static CssDeclaredValue ofResolved(List<ComponentValue> values) {
        return new CssDeclaredValue() {
            private final int hash;

            {
                int hash = 1;

                for (ComponentValue value : values) {
                    hash += 31 * value.hashCode();
                }

                this.hash = hash;
            }

            @Override
            public ComponentValue get(int index) {
                return values.get(index);
            }

            @Override
            public int size() {
                return values.size();
            }

            @Override
            public boolean containsLookup() {
                return false;
            }

            @Override
            public int line() {
                return values.isEmpty() ? -1 : values.getFirst().line();
            }

            @Override
            public int column() {
                return values.isEmpty() ? -1 : values.getFirst().column();
            }

            @Override
            public int hashCode() {
                return hash;
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof CssDeclaredValue v && Objects.equals(values, v);
            }
        };
    }
}
