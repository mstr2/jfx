/*
 * Copyright (c) 2024 Oracle and/or its affiliates. All rights reserved.
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

package test.com.sun.javafx.application.preferences;

import com.sun.javafx.application.preferences.ApplicationPreferences;
import com.sun.javafx.application.preferences.PreferenceMapping;
import javafx.application.ColorScheme;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ApplicationPreferencesTest {

    ApplicationPreferences prefs;

    @BeforeEach
    void setup() {
        prefs = new ApplicationPreferences(
            // Well-known platform keys and their associated type
            Map.of(
                "test.anInt", Integer.class,
                "test.aDouble", Double.class,
                "test.aBoolean", Boolean.class,
                "test.aString", String.class,
                "test.aColor", Color.class,
                "test.aPaint", Paint.class,
                "test.aPaintArray", Paint[].class
            ),
            // Platform-specific key mappings
            Map.of(
                "test.foregroundColor", new PreferenceMapping<>("foregroundColor", Color.class),
                "test.backgroundColor", new PreferenceMapping<>("backgroundColor", Color.class),
                "test.accentColor", new PreferenceMapping<>("accentColor", Color.class),
                "test.reducedMotion", new PreferenceMapping<>("reducedMotion", Boolean.class),
                "test.enableTransparency", new PreferenceMapping<>("reducedTransparency", Boolean.class, b -> !b)
            ));
    }

    @Test
    void testDefaultValues() {
        assertEquals(Color.WHITE, prefs.getBackgroundColor());
        assertEquals(Color.BLACK, prefs.getForegroundColor());
        assertEquals(Color.web("#157EFB"), prefs.getAccentColor());
        assertEquals(ColorScheme.LIGHT, prefs.getColorScheme());
    }

    @Test
    void testResetSingleMapping() {
        prefs.update(Map.of("k1", 5, "k2", 7.5));

        // Override the "k1" mapping with a user value
        assertEquals(5, prefs.put("k1", 10));
        assertEquals(10, prefs.get("k1"));

        // Clear the user value
        prefs.reset("k1");
        assertEquals(5, prefs.get("k1"));
    }

    @Test
    void testResetAllMappings() {
        prefs.update(Map.of("k1", 5, "k2", 7.5));

        prefs.put("k1", 10);
        prefs.put("k2", 0.123);
        assertEquals(10, prefs.getInteger("k1").orElseThrow());
        assertEquals(0.123, prefs.getDouble("k2").orElseThrow(), 0.001);

        prefs.reset();
        assertEquals(5, prefs.getInteger("k1").orElseThrow());
        assertEquals(7.5, prefs.getDouble("k2").orElseThrow(), 0.001);
    }

    @Test
    void testCannotOverrideWithNullValue() {
        prefs.update(Map.of("k", 5));
        assertThrows(NullPointerException.class, () -> prefs.put("k", null));
    }

    @Test
    void testColorSchemeReflectsForegroundAndBackgroundColors() {
        prefs.update(Map.of("test.foregroundColor", Color.BLACK, "test.backgroundColor", Color.WHITE));
        assertEquals(ColorScheme.LIGHT, prefs.getColorScheme());

        prefs.update(Map.of("test.foregroundColor", Color.WHITE, "test.backgroundColor", Color.BLACK));
        assertEquals(ColorScheme.DARK, prefs.getColorScheme());

        prefs.update(Map.of("test.foregroundColor", Color.DARKGRAY, "test.backgroundColor", Color.LIGHTGRAY));
        assertEquals(ColorScheme.LIGHT, prefs.getColorScheme());

        prefs.update(Map.of("test.foregroundColor", Color.RED, "test.backgroundColor", Color.BLUE));
        assertEquals(ColorScheme.DARK, prefs.getColorScheme());
    }

    @Test
    void testOverriddenColorSchemeIsNotAffectedByBackgroundAndForegroundColors() {
        prefs.setColorScheme(ColorScheme.DARK);
        prefs.setBackgroundColor(Color.WHITE);
        prefs.setForegroundColor(Color.BLACK);
        assertEquals(ColorScheme.DARK, prefs.getColorScheme());
        prefs.setColorScheme(null);
        assertEquals(ColorScheme.LIGHT, prefs.getColorScheme());
    }

    @Test
    void testOverrideReducedMotion() {
        assertFalse(prefs.isReducedMotion());
        prefs.setReducedMotion(true);
        assertTrue(prefs.isReducedMotion());
        prefs.setReducedMotion(null);
        assertFalse(prefs.isReducedMotion());
    }

    @Test
    void testOverrideReducedTransparency() {
        assertFalse(prefs.isReducedTransparency());
        prefs.setReducedTransparency(true);
        assertTrue(prefs.isReducedTransparency());
        prefs.setReducedTransparency(null);
        assertFalse(prefs.isReducedTransparency());
    }

    @Test
    void testOverrideColorScheme() {
        assertEquals(ColorScheme.LIGHT, prefs.getColorScheme());
        prefs.setColorScheme(ColorScheme.DARK);
        assertEquals(ColorScheme.DARK, prefs.getColorScheme());
        prefs.setColorScheme(null);
        assertEquals(ColorScheme.LIGHT, prefs.getColorScheme());
    }

    @Test
    void testOverrideBackgroundColor() {
        assertEquals(Color.WHITE, prefs.getBackgroundColor());
        prefs.setBackgroundColor(Color.GREEN);
        assertEquals(Color.GREEN, prefs.getBackgroundColor());
        prefs.setBackgroundColor(null);
        assertEquals(Color.WHITE, prefs.getBackgroundColor());
    }

    @Test
    void testOverrideForegroundColor() {
        assertEquals(Color.BLACK, prefs.getForegroundColor());
        prefs.setForegroundColor(Color.GREEN);
        assertEquals(Color.GREEN, prefs.getForegroundColor());
        prefs.setForegroundColor(null);
        assertEquals(Color.BLACK, prefs.getForegroundColor());
    }
}
