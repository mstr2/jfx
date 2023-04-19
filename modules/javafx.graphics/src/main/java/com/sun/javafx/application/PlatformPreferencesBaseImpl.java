/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.javafx.application;

import com.sun.javafx.binding.MapExpressionHelper;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.collections.MapChangeListener;
import javafx.scene.paint.Color;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Contains the {@link Map} implementation of the {@link Platform.Preferences} interface.
 * The property-based API is implemented in the {@link PlatformPreferencesImpl} class.
 */
abstract class PlatformPreferencesBaseImpl extends AbstractMap<String, Object> implements Platform.Preferences {

    private final Map<String, ValueEntry> backingMap = new HashMap<>() {
        @Override
        public ValueEntry put(String key, ValueEntry value) {
            entrySet.invalidateSize();
            return super.put(key, value);
        }
    };

    private final EntrySet entrySet = new EntrySet(backingMap.entrySet());
    private final List<InvalidationListener> invalidationListeners = new CopyOnWriteArrayList<>();
    private final List<MapChangeListener<? super String, ? super Object>> changeListeners = new CopyOnWriteArrayList<>();

    @Override
    public Optional<Integer> getInteger(String key) {
        return getValue(key, Integer.class);
    }

    @Override
    public Optional<Double> getDouble(String key) {
        return getValue(key, Double.class);
    }

    @Override
    public Optional<Boolean> getBoolean(String key) {
        return getValue(key, Boolean.class);
    }

    @Override
    public Optional<String> getString(String key) {
        return getValue(key, String.class);
    }

    @Override
    public Optional<Color> getColor(String key) {
        return getValue(key, Color.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getValue(String key, Class<T> type) {
        Objects.requireNonNull(key, "key cannot be null");
        Object value = ValueEntry.getEffectiveValue(backingMap.get(key));
        if (value != null && !type.isInstance(value)) {
            throw new IllegalArgumentException(
                "Incompatible types: requested = " + type.getName() + ", actual = " + value.getClass().getName());
        }

        return value != null ? Optional.of((T)value) : Optional.empty();
    }

    @Override
    public Object get(Object key) {
        Objects.requireNonNull(key, "key cannot be null");
        return ValueEntry.getEffectiveValue(backingMap.get(key));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T override(String key, T value) {
        Objects.requireNonNull(key, "key cannot be null");
        ValueEntry entry = backingMap.get(key);
        Object existingValue = ValueEntry.getEffectiveValue(entry);

        if (value != null && existingValue != null && !existingValue.getClass().isInstance(value)) {
            throw new IllegalArgumentException(
                "Cannot override a value of type " + existingValue.getClass().getName() +
                " with a value of type " + value.getClass().getName());
        }

        backingMap.put(key, ValueEntry.withUserValue(entry, value));
        return (T)existingValue;
    }

    @Override
    public Object remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return entrySet;
    }

    @Override
    public void addListener(InvalidationListener listener) {
        invalidationListeners.add(listener);
    }

    @Override
    public void removeListener(InvalidationListener listener) {
        invalidationListeners.remove(listener);
    }

    @Override
    public void addListener(MapChangeListener<? super String, ? super Object> listener) {
        changeListeners.add(listener);
    }

    @Override
    public void removeListener(MapChangeListener<? super String, ? super Object> listener) {
        changeListeners.remove(listener);
    }

    @Override
    public void commit() {
        Map<String, ChangedValue> changedPreferences = null;

        for (Map.Entry<String, ValueEntry> entry : backingMap.entrySet()) {
            ValueEntry valueEntry = entry.getValue();
            ChangedValue changedValue = valueEntry.tryCommit();

            if (changedValue != null) {
                if (changedPreferences == null) {
                    changedPreferences = new HashMap<>();
                }

                changedPreferences.put(entry.getKey(), changedValue);
            }
        }

        if (changedPreferences != null) {
            fireValueChangedEvent(changedPreferences);
        }
    }

    protected abstract void updateDerivedPreferences(Map<String, ChangedValue> changedPreferences);

    /**
     * Updates this map of preferences with a set of new or changed platform preferences.
     * <p>
     * The new preferences may include all available preferences, or only the new/changed preferences.
     * The implementation delays firing notifications until all preferences have been applied to ensure
     * that observers will never observe this map in an inconsistent state.
     * InvalidationListeners are only notified once, even if several preferences have changed.
     */
    public final void update(Map<String, Object> preferences) {
        Map<String, ChangedValue> changedPreferences = getChangedPreferences(preferences);
        if (changedPreferences.isEmpty()) {
            return;
        }

        for (Map.Entry<String, ChangedValue> entry : changedPreferences.entrySet()) {
            ValueEntry existingEntry = backingMap.get(entry.getKey());
            ValueEntry newEntry = ValueEntry.withPlatformValue(existingEntry, entry.getValue().newValue);
            backingMap.put(entry.getKey(), newEntry);
        }

        updateDerivedPreferences(changedPreferences);
        fireValueChangedEvent(changedPreferences);
    }

    /**
     * Given a map of new preferences, this method returns a new map that only contains
     * mappings that have actually changed compared to the currently known preferences.
     */
    private Map<String, ChangedValue> getChangedPreferences(Map<String, Object> preferences) {
        Map<String, ChangedValue> changed = new HashMap<>();

        for (Map.Entry<String, Object> entry : preferences.entrySet()) {
            Object existingValue = ValueEntry.getEffectiveValue(backingMap.get(entry.getKey()));
            Object newValue = entry.getValue();
            boolean equals = false;

            if (existingValue instanceof Object[] && newValue instanceof Object[]) {
                equals = Arrays.equals((Object[]) existingValue, (Object[]) newValue);
            } else if (!(existingValue instanceof Object[]) && !(newValue instanceof Object[])) {
                equals = Objects.equals(existingValue, newValue);
            }

            if (!equals) {
                changed.put(entry.getKey(), new ChangedValue(existingValue, newValue));
            }
        }

        return changed;
    }

    private void fireValueChangedEvent(Map<String, ChangedValue> changedPreferences) {
        for (InvalidationListener listener : invalidationListeners) {
            try {
                listener.invalidated(this);
            } catch (Exception e) {
                Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
            }
        }

        if (changeListeners.size() > 0) {
            var change = new MapExpressionHelper.SimpleChange<>(this);

            for (Map.Entry<String, ChangedValue> entry : changedPreferences.entrySet()) {
                change.setPut(entry.getKey(), entry.getValue().oldValue, entry.getValue().newValue);

                for (MapChangeListener<? super String, ? super Object> listener : changeListeners) {
                    try {
                        listener.onChanged(change);
                    } catch (Exception e) {
                        Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
                    }
                }
            }
        }
    }

    protected record ChangedValue(Object oldValue, Object newValue) {}

    private static class ValueEntry {
        /**
         * Returns the effective value of the specified {@code ValueEntry}, which is the
         * user value (if not null) or the platform value (if the user value is null).
         * The effective value may be null.
         */
        static Object getEffectiveValue(ValueEntry entry) {
            if (entry == null) {
                return null;
            }

            return entry.userValue != null ? entry.userValue : entry.platformValue;
        }

        /**
         * Creates a new {@code ValueEntry} with the specified platform value.
         * If the existing entry has an uncommitted attachment, it is automatically committed.
         */
        static ValueEntry withPlatformValue(ValueEntry entry, Object platformValue) {
            Object userValue;
            if (entry != null) {
                userValue = entry.uncommittedEntry != null ? entry.uncommittedEntry.userValue : entry.userValue;
            } else {
                userValue = null;
            }

            return new ValueEntry(platformValue, userValue);
        }

        /**
         * Creates a new {@code ValueEntry} with the specified user value.
         * The user value will be recorded in the uncommitted attachment, which means that the
         * returned {@code ValueEntry} will still resolve to its old value until it is committed.
         */
        static ValueEntry withUserValue(ValueEntry entry, Object userValue) {
            if (entry != null) {
                var uncommittedEntry = new ValueEntry(entry.platformValue, userValue);
                return new ValueEntry(entry.platformValue, entry.userValue, uncommittedEntry);
            } else if (userValue != null) {
                var uncommittedEntry = new ValueEntry(null, userValue);
                return new ValueEntry(null, null, uncommittedEntry);
            } else {
                return new ValueEntry(null, null);
            }
        }

        private Object platformValue;
        private Object userValue;
        private ValueEntry uncommittedEntry;

        private ValueEntry(Object platformValue, Object userValue) {
            this.platformValue = platformValue;
            this.userValue = userValue;
        }

        private ValueEntry(Object platformValue, Object userValue, ValueEntry uncommittedEntry) {
            this(platformValue, userValue);
            this.uncommittedEntry = uncommittedEntry;
        }

        /**
         * Tries to commit an uncommitted attachment, and returns a {@link ChangedValue} if the
         * effective value of this {@code ValueEntry} changed as a result.
         */
        ChangedValue tryCommit() {
            if (uncommittedEntry == null) {
                return null;
            }

            Object oldEffectiveValue = getEffectiveValue(this);
            Object newEffectiveValue = getEffectiveValue(uncommittedEntry);
            platformValue = uncommittedEntry.platformValue;
            userValue = uncommittedEntry.userValue;
            uncommittedEntry = null;

            if (oldEffectiveValue != newEffectiveValue) {
                return new ChangedValue(oldEffectiveValue, newEffectiveValue);
            }

            return null;
        }
    }

    /**
     * The entry set contains all mappings of the backing map where the effective value is not null.
     */
    private static class EntrySet extends AbstractSet<Entry<String, Object>> {
        private final Set<Entry<String, ValueEntry>> backingSet;
        private int size = -1;

        EntrySet(Set<Entry<String, ValueEntry>> backingSet) {
            this.backingSet = backingSet;
        }

        @Override
        public Iterator<Entry<String, Object>> iterator() {
            return new Iterator<>() {
                final Iterator<Entry<String, ValueEntry>> it = backingSet.stream()
                    .filter(entry -> ValueEntry.getEffectiveValue(entry.getValue()) != null)
                    .iterator();

                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public Entry<String, Object> next() {
                    Entry<String, ValueEntry> entry = it.next();
                    return new AbstractMap.SimpleImmutableEntry<>(
                        entry.getKey(), ValueEntry.getEffectiveValue(entry.getValue()));
                }
            };
        }

        @Override
        public int size() {
            if (size < 0) {
                size = (int)backingSet.stream()
                    .filter(entry -> ValueEntry.getEffectiveValue(entry.getValue()) != null)
                    .count();
            }

            return size;
        }

        void invalidateSize() {
            size = -1;
        }
    }

}
