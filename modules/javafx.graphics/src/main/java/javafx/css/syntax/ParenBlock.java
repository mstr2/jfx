package javafx.css.syntax;

import java.util.AbstractList;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

public final class ParenBlock extends AbstractList<ComponentValue> implements SimpleBlock {

    private final List<ComponentValue> values;
    private final boolean containsLookup;
    private final int line;
    private final int column;
    private final int hash;

    public ParenBlock(List<ComponentValue> values, int line, int column) {
        if (!(values instanceof RandomAccess)) {
            throw new IllegalArgumentException("values must be a random-access list");
        }

        this.values = values;
        this.containsLookup = containsLookupImpl(values);
        this.line = line;
        this.column = column;
        this.hash = Objects.hashCode(values);
    }

    @Override
    public ComponentValue get(int index) {
        return values.get(index);
    }

    @Override
    public int size() {
        return values.size();
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
        return obj instanceof ParenBlock block
            && block.hash == hash
            && Objects.equals(block.values, values);
    }

    @Override
    public String toString() {
        return "<paren-block>";
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