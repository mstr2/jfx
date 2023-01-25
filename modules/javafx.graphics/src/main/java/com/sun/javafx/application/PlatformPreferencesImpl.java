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

import com.sun.javafx.util.Utils;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectPropertyBase;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.MapChangeListener;
import javafx.scene.paint.Color;
import javafx.stage.Appearance;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PlatformPreferencesImpl extends AbstractMap<String, Object> implements Platform.Preferences {

    private final Map<String, Object> modifiableMap = new HashMap<>();
    private final Set<Entry<String, Object>> unmodifiableEntrySet = Collections.unmodifiableSet(modifiableMap.entrySet());
    private final List<InvalidationListener> invalidationListeners = new CopyOnWriteArrayList<>();
    private final List<MapChangeListener<? super String, ? super Object>> changeListeners = new CopyOnWriteArrayList<>();

    private final ColorProperty backgroundColor = new ColorProperty("backgroundColor", Color.BLACK,
        new String[] {
            "Windows.UIColor.Background",
            "macOS.NSColor.textBackgroundColor",
            "GTK.theme_bg_color"
        });

    private final ColorProperty foregroundColor = new ColorProperty("foregroundColor", Color.WHITE,
        new String[] {
            "Windows.UIColor.Foreground",
            "macOS.NSColor.textColor",
            "GTK.theme_fg_color"
        });

    private final ColorProperty accentColor = new ColorProperty("accentColor", Color.rgb(21, 126, 251),
        new String[] {
            "Windows.UIColor.Accent",
            "macOS.NSColor.controlAccentColor"
            // GTK: no accent color
        });

    private final ColorProperty[] colorProperties = new ColorProperty[] {
        backgroundColor, foregroundColor, accentColor
    };

    private final ReadOnlyObjectWrapper<Appearance> appearance =
        new ReadOnlyObjectWrapper<>(this, "appearance") {
            {
                InvalidationListener listener = observable -> update();
                backgroundColor.addListener(listener);
                foregroundColor.addListener(listener);
                update();
            }

            private void update() {
                Color background = backgroundColor.get();
                Color foreground = foregroundColor.get();
                boolean isDark = Utils.calculateBrightness(background) < Utils.calculateBrightness(foreground);
                set(isDark ? Appearance.DARK : Appearance.LIGHT);
            }
        };

    @Override
    public ReadOnlyObjectProperty<Appearance> appearanceProperty() {
        return appearance.getReadOnlyProperty();
    }

    @Override
    public ReadOnlyObjectProperty<Color> backgroundColorProperty() {
        return backgroundColor;
    }

    @Override
    public ReadOnlyObjectProperty<Color> foregroundColorProperty() {
        return foregroundColor;
    }

    @Override
    public ReadOnlyObjectProperty<Color> accentColorProperty() {
        return accentColor;
    }

    @Override
    public String getString(String key) {
        Object value = modifiableMap.get(key);
        if (value instanceof String s) {
            return s;
        }

        return null;
    }

    @Override
    public Boolean getBoolean(String key) {
        Object value = modifiableMap.get(key);
        if (value instanceof Boolean b) {
            return b;
        }

        return null;
    }

    @Override
    public Color getColor(String key) {
        Object value = modifiableMap.get(key);
        if (value instanceof Color c) {
            return c;
        }

        return null;
    }

    public Map<String, Object> getModifiableMap() {
        return modifiableMap;
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return unmodifiableEntrySet;
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

    void firePreferencesChanged(Map<String, Object> changed) {
        for (Map.Entry<String, Object> entry : changed.entrySet()) {
            if (entry.getValue() instanceof Color color) {
                for (ColorProperty property : colorProperties) {
                    property.trySet(entry.getKey(), color);
                }
            }
        }

        for (ColorProperty property : colorProperties) {
            property.fireValueChangedEvent();
        }

        for (InvalidationListener listener : invalidationListeners) {
            try {
                listener.invalidated(this);
            } catch (Exception e) {
                Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
            }
        }

        for (Map.Entry<String, Object> entry : changed.entrySet()) {
            boolean keyExists = modifiableMap.containsKey(entry.getKey());
            Object oldValue = keyExists ? modifiableMap.get(entry.getKey()) : null;
            MapChangeListener.Change<String, Object> change = new MapChangeListener.Change<>(this) {
                @Override public boolean wasAdded() { return true; }
                @Override public boolean wasRemoved() { return keyExists; }
                @Override public String getKey() { return entry.getKey(); }
                @Override public Object getValueAdded() { return entry.getValue(); }
                @Override public Object getValueRemoved() { return oldValue; }
            };

            for (MapChangeListener<? super String, ? super Object> listener : changeListeners) {
                try {
                    listener.onChanged(change);
                } catch (Exception e) {
                    Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
                }
            }
        }
    }

    private final class ColorProperty extends ReadOnlyObjectPropertyBase<Color> {
        final String name;
        final String[] platformKeys;
        Color currentValue;
        Color newValue;

        ColorProperty(String name, Color initialValue, String[] platformKeys) {
            this.name = name;
            this.currentValue = initialValue;
            this.newValue = initialValue;
            this.platformKeys = platformKeys;
        }

        @Override
        public Object getBean() {
            return PlatformPreferencesImpl.this;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Color get() {
            return currentValue;
        }

        public void trySet(String key, Color value) {
            for (String platformKey : platformKeys) {
                if (Objects.equals(platformKey, key)) {
                    this.newValue = value;
                    return;
                }
            }
        }

        @Override
        public void fireValueChangedEvent() {
            if (!Objects.equals(currentValue, newValue)) {
                currentValue = newValue;
                super.fireValueChangedEvent();
            }
        }
    }

}
