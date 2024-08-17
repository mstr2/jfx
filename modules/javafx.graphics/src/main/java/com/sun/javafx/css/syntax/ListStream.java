package com.sun.javafx.css.syntax;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.RandomAccess;

public final class ListStream<T> {

    private final List<T> source;
    private final int size;
    private int currentIndex = -1;
    private T currentItem;

    public ListStream(List<T> source) {
        if (!(source instanceof RandomAccess)) {
            throw new IllegalArgumentException("source must be a random-access list");
        }

        this.source = source;
        this.size = source.size();
    }

    public int size() {
        return size;
    }

    public T current() {
        return currentItem;
    }

    public int index() {
        return currentIndex;
    }

    public boolean hasMore() {
        return peek() != null;
    }

    public void consume(int count) {
        if (currentIndex + count > size) {
            throw new NoSuchElementException();
        }

        currentIndex += count;
        currentItem = source.get(currentIndex);
    }

    public T consume() {
        if (currentIndex < size - 1) {
            return currentItem = source.get(++currentIndex);
        }

        if (currentIndex < size) {
            currentIndex++;
        }

        return null;
    }

    public void reconsume() {
        if (currentIndex > 0) {
            currentItem = source.get(--currentIndex);
        } else {
            currentItem = null;
            currentIndex = -1;
        }
    }

    public T peek() {
        return currentIndex < size - 1 ? source.get(currentIndex + 1) : null;
    }

    public T peekAhead(int count) {
        int nextIndex = currentIndex + count;
        return nextIndex < size - 1 ? source.get(nextIndex + 1) : null;
    }

    public void reset(int index) {
        currentIndex = index;
        currentItem = index >= 0 ? source.get(index) : null;
    }

    public ListStream<T> subStream(int start, int end) {
        return new ListStream<>(source.subList(start, end));
    }
}
