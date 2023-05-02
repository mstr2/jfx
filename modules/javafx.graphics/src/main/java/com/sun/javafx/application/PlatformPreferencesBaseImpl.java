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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Contains the {@link Map} implementation of the {@link Platform.Preferences} interface.
 * The property-based API is implemented in the {@link PlatformPreferencesImpl} class.
 */
abstract class PlatformPreferencesBaseImpl extends AbstractMap<String, Object> implements Platform.Preferences {

    private final List<InvalidationListener> invalidationListeners = new ArrayList<>();
    private final List<MapChangeListener<? super String, ? super Object>> changeListeners = new ArrayList<>();
    private final Map<String, Object> platformPreferences = new HashMap<>();
    private final Map<String, Object> userPreferences = new HashMap<>();
    private final Map<String, Object> effectivePreferences = new HashMap<>();
    private final Map<String, Object> unmodifiableEffectivePreferences = Collections.unmodifiableMap(effectivePreferences);
    private Map<String, Object> lastEffectivePreferences = Map.of();
    private boolean effectivePreferencesChanged;

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return unmodifiableEffectivePreferences.entrySet();
    }

    @Override
    public Object remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void addListener(InvalidationListener listener) {
        invalidationListeners.add(listener);
    }

    @Override
    public synchronized void removeListener(InvalidationListener listener) {
        invalidationListeners.remove(listener);
    }

    @Override
    public synchronized void addListener(MapChangeListener<? super String, ? super Object> listener) {
        changeListeners.add(listener);
    }

    @Override
    public synchronized void removeListener(MapChangeListener<? super String, ? super Object> listener) {
        changeListeners.remove(listener);
    }

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
        Object value = effectivePreferences.get(key);

        if (value == null) {
            return Optional.empty();
        }

        if (type.isInstance(value)) {
            return Optional.of((T)value);
        }

        throw new IllegalArgumentException(
            "Incompatible types: requested = " + type.getName() +
            ", actual = " + value.getClass().getName());
    }

    @Override
    public Object put(String key, Object value) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        Object effectiveValue = effectivePreferences.get(key);

        if (effectiveValue != null && !effectiveValue.getClass().isInstance(value)) {
            throw new IllegalArgumentException(
                "Cannot override a value of type " + effectiveValue.getClass().getName() +
                " with a value of type " + value.getClass().getName());
        }

        userPreferences.put(key, value);
        effectivePreferences.put(key, value);

        if (!Objects.equals(effectiveValue, value)) {
            var changedPreferences = Map.of(key, new ChangedValue(effectiveValue, value));
            updateDerivedPreferences(changedPreferences);
            fireValueChangedEvent(changedPreferences);
            effectivePreferencesChanged = true;
        }

        return effectiveValue;
    }

    @Override
    public void reset(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        Object oldValue = effectivePreferences.get(key);
        Object newValue;

        userPreferences.remove(key);

        if (platformPreferences.containsKey(key)) {
            newValue = platformPreferences.get(key);
            effectivePreferences.put(key, newValue);
        } else {
            newValue = null;
            effectivePreferences.remove(key);
        }

        if (!Objects.equals(oldValue, newValue)) {
            var changedPreferences = Map.of(key, new ChangedValue(oldValue, newValue));
            updateDerivedPreferences(changedPreferences);
            fireValueChangedEvent(changedPreferences);
            effectivePreferencesChanged = true;
        }
    }

    @Override
    public void reset() {
        forEach((key, value) -> reset(key));
    }

    /**
     * Returns an unmodifiable map that contains all new or effectively changed mappings
     * since the last time this method was called.
     */
    public final Map<String, Object> pollChanges() {
        if (!effectivePreferencesChanged) {
            return Map.of();
        }

        Map<String, Object> changes = new HashMap<>(size());

        for (var pref : getEffectiveChanges(lastEffectivePreferences, effectivePreferences).entrySet()) {
            changes.put(pref.getKey(), pref.getValue().newValue());
        }

        effectivePreferencesChanged = false;
        lastEffectivePreferences = new HashMap<>(effectivePreferences);

        return Collections.unmodifiableMap(changes);
    }

    /**
     * Updates this map of preferences with a set of new or changed platform preferences.
     * The specified preferences may include all available preferences, or only the new/changed preferences.
     */
    public final void update(Map<String, Object> newOrChangedPreferences) {
        Map<String, Object> currentEffectivePreferences = new HashMap<>(effectivePreferences);
        platformPreferences.putAll(newOrChangedPreferences);
        effectivePreferences.clear();
        effectivePreferences.putAll(platformPreferences);
        effectivePreferences.putAll(userPreferences);

        // Only fire change notifications if any preference has effectively changed.
        var effectivelyChangedPreferences = getEffectiveChanges(currentEffectivePreferences, effectivePreferences);
        if (!effectivelyChangedPreferences.isEmpty()) {
            updateDerivedPreferences(effectivelyChangedPreferences);
            fireValueChangedEvent(effectivelyChangedPreferences);
            effectivePreferencesChanged = true;
        }
    }

    /**
     * Returns a map that contains the new or changed mappings of {@code change} compared to {@code base}.
     */
    private Map<String, ChangedValue> getEffectiveChanges(Map<String, Object> base, Map<String, Object> change) {
        Map<String, ChangedValue> changed = null;

        for (Map.Entry<String, Object> entry : change.entrySet()) {
            Object newValue = entry.getValue();
            Object oldValue = base.get(entry.getKey());
            boolean equals = false;

            if (oldValue instanceof Object[] oldArray && newValue instanceof Object[] newArray) {
                equals = Arrays.equals(oldArray, newArray);
            } else if (!(oldValue instanceof Object[]) && !(newValue instanceof Object[])) {
                equals = Objects.equals(oldValue, newValue);
            }

            if (!equals) {
                if (changed == null) {
                    changed = new HashMap<>();
                }

                changed.put(entry.getKey(), new ChangedValue(oldValue, newValue));
            }
        }

        return changed != null ? changed : Map.of();
    }

    private synchronized void fireValueChangedEvent(Map<String, ChangedValue> changedEntries) {
        for (InvalidationListener listener : invalidationListeners) {
            try {
                listener.invalidated(this);
            } catch (Exception e) {
                Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
            }
        }

        if (changeListeners.size() > 0) {
            var change = new MapExpressionHelper.SimpleChange<>(this);

            for (Map.Entry<String, ChangedValue> entry : changedEntries.entrySet()) {
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

    abstract void updateDerivedPreferences(Map<String, ChangedValue> changedPreferences);

    record ChangedValue(Object oldValue, Object newValue) {}

}
