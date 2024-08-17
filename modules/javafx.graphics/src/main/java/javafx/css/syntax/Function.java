package javafx.css.syntax;

import java.util.AbstractList;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

public final class Function extends AbstractList<ComponentValue> implements Block {

    private final String name;
    private final List<ComponentValue> values;
    private final boolean containsLookup;
    private final int line;
    private final int column;
    private final int hash;

    public Function(String name,
                    List<ComponentValue> values,
                    int line, int column) {
        if (!(values instanceof RandomAccess)) {
            throw new IllegalArgumentException("values must be a random-access list");
        }

        this.name = name.intern();
        this.values = values;
        this.containsLookup = "var".equals(name) || containsLookupImpl(values);
        this.line = line;
        this.column = column;
        this.hash = Objects.hashCode(name) + 31 * Objects.hashCode(values);
    }

    @Override
    public ComponentValue get(int index) {
        return values.get(index);
    }

    @Override
    public int size() {
        return values.size();
    }

    public String name() {
        return name;
    }

    public boolean containsLookup() {
        return containsLookup;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Function func
            && func.hash == hash
            && Objects.equals(func.name, name)
            && Objects.equals(func.values, values);
    }

    @Override
    public String toString() {
        return "<function>" + name;
    }

    private static boolean containsLookupImpl(List<ComponentValue> values) {
        for (ComponentValue value : values) {
            if (value instanceof Block block && block.containsLookup()) {
                return true;
            }
        }

        return false;
    }
}
