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
import javafx.scene.paint.Color;
import javafx.stage.Appearance;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contains the property-based API of the {@link Platform.Preferences} interface.
 * The key-value API is implemented in the {@link PlatformPreferencesMapImpl} class.
 */
public final class PlatformPreferencesImpl extends PlatformPreferencesMapImpl {

    private static final Color DEFAULT_BACKGROUND_COLOR = Color.WHITE;
    private static final Color DEFAULT_FOREGROUND_COLOR = Color.BLACK;
    private static final Color DEFAULT_ACCENT_COLOR = Color.rgb(21, 126, 251);

    private final ColorProperty backgroundColor = new ColorProperty(
        "backgroundColor", DEFAULT_BACKGROUND_COLOR, List.of(
            "Windows.UIColor.Background",
            "macOS.NSColor.textBackgroundColor",
            "GTK.theme_bg_color"
        ));

    private final ColorProperty foregroundColor = new ColorProperty(
        "foregroundColor", DEFAULT_FOREGROUND_COLOR, List.of(
            "Windows.UIColor.Foreground",
            "macOS.NSColor.textColor",
            "GTK.theme_fg_color"
        ));

    private final ColorProperty accentColor = new ColorProperty(
        "accentColor", DEFAULT_ACCENT_COLOR, List.of(
            "Windows.UIColor.Accent",
            "macOS.NSColor.controlAccentColor"
            // GTK: no accent color
        ));

    private final List<ColorProperty> allColors = List.of(backgroundColor, foregroundColor, accentColor);

    private final class ColorProperty extends ReadOnlyObjectPropertyBase<Color> {
        private final String name;
        private final List<String> platformKeys;
        private final Color defaultValue;
        private Color overrideValue;
        private Color effectiveValue;
        private Color platformValue;

        ColorProperty(String name, Color initialValue, List<String> platformKeys) {
            this.name = name;
            this.defaultValue = initialValue;
            this.effectiveValue = initialValue;
            this.platformValue = initialValue;
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
            return effectiveValue;
        }

        public boolean matchesKey(String key) {
            return platformKeys.contains(key);
        }

        public void setValue(Color value) {
            this.platformValue = value;
        }

        public void setValueOverride(Color value) {
            this.overrideValue = value;
        }

        public void commit() {
            Color newValue = Objects.requireNonNullElse(
                overrideValue != null ? overrideValue : platformValue,
                defaultValue);

            if (!Objects.equals(effectiveValue, newValue)) {
                effectiveValue = newValue;
                fireValueChangedEvent();
            }
        }
    }

    private final AppearanceProperty appearance = new AppearanceProperty();

    private class AppearanceProperty extends ReadOnlyObjectWrapper<Appearance> {
        private Appearance appearanceOverride;

        AppearanceProperty() {
            super(PlatformPreferencesImpl.this, "appearance");
            InvalidationListener listener = observable -> commit();
            backgroundColor.addListener(listener);
            foregroundColor.addListener(listener);
        }

        public void setValueOverride(Appearance appearance) {
            appearanceOverride = appearance;
        }

        public void commit() {
            if (appearanceOverride != null) {
                set(appearanceOverride);
            } else {
                Color background = backgroundColor.get();
                Color foreground = foregroundColor.get();
                boolean isDark = Utils.calculateBrightness(background) < Utils.calculateBrightness(foreground);
                set(isDark ? Appearance.DARK : Appearance.LIGHT);
            }
        }
    }

    public PlatformPreferencesImpl() {
        commit();
    }

    @Override
    public void commit() {
        super.commit();
        backgroundColor.commit();
        foregroundColor.commit();
        accentColor.commit();
        appearance.commit();
    }

    @Override
    public ReadOnlyObjectProperty<Appearance> appearanceProperty() {
        return appearance.getReadOnlyProperty();
    }

    @Override
    public Appearance getAppearance() {
        return appearance.get();
    }

    @Override
    public void setAppearance(Appearance value) {
        appearance.setValueOverride(value);
    }

    @Override
    public ReadOnlyObjectProperty<Color> backgroundColorProperty() {
        return backgroundColor;
    }

    @Override
    public Color getBackgroundColor() {
        return backgroundColor.get();
    }

    @Override
    public void setBackgroundColor(Color color) {
        backgroundColor.setValueOverride(color);
    }

    @Override
    public ReadOnlyObjectProperty<Color> foregroundColorProperty() {
        return foregroundColor;
    }

    @Override
    public Color getForegroundColor() {
        return foregroundColor.get();
    }

    @Override
    public void setForegroundColor(Color color) {
        foregroundColor.setValueOverride(color);
    }

    @Override
    public ReadOnlyObjectProperty<Color> accentColorProperty() {
        return accentColor;
    }

    @Override
    public Color getAccentColor() {
        return accentColor.get();
    }

    @Override
    public void setAccentColor(Color color) {
        accentColor.setValueOverride(color);
    }

    @Override
    protected void updateDerivedPreferences(Map<String, ChangedValue> changedPreferences) {
        List<ColorProperty> changedColorProperties = new ArrayList<>();

        for (Map.Entry<String, ChangedValue> entry : changedPreferences.entrySet()) {
            if (entry.getValue().newValue() instanceof Color color) {
                for (ColorProperty colorProperty : allColors) {
                    if (colorProperty.matchesKey(entry.getKey())) {
                        colorProperty.setValue(color);
                        changedColorProperties.add(colorProperty);
                    }
                }
            }
        }

        changedColorProperties.forEach(ColorProperty::commit);
    }

}
