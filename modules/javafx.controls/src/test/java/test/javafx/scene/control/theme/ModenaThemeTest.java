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

package test.javafx.scene.control.theme;

import com.sun.javafx.application.PlatformImpl;
import com.sun.javafx.application.PlatformPreferencesImpl;
import org.junit.jupiter.api.Test;
import javafx.scene.control.theme.ModenaTheme;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ModenaThemeTest {

    @Test
    public void testHighContrastThemeWithSystemProperty() {
        var theme = new ModenaTheme();
        assertFalse(theme.getStylesheets().stream().anyMatch(fileName -> fileName.contains("blackOnWhite.css")));
        System.setProperty("com.sun.javafx.highContrastTheme", "BLACKONWHITE");
        theme = new ModenaTheme();
        assertTrue(theme.getStylesheets().stream().anyMatch(fileName -> fileName.contains("blackOnWhite.css")));
        System.clearProperty("com.sun.javafx.highContrastTheme");
    }

    @Test
    public void testHighContrastThemeWithPlatformPreference() {
        var theme = new ModenaTheme();
        assertFalse(theme.getStylesheets().stream().anyMatch(fileName -> fileName.contains("blackOnWhite.css")));

        PlatformPreferencesImpl prefs = (PlatformPreferencesImpl)PlatformImpl.getPlatformPreferences();
        Object originalOn = prefs.get("Windows.SPI.HighContrastOn");
        Object originalName = prefs.get("Windows.SPI.HighContrastColorScheme");

        prefs.update(Map.of(
            "Windows.SPI.HighContrastOn", true,
            "Windows.SPI.HighContrastColorScheme", "BLACKONWHITE"));

        theme = new ModenaTheme();
        assertTrue(theme.getStylesheets().stream().anyMatch(fileName -> fileName.contains("blackOnWhite.css")));

        prefs.update(new HashMap<>() {{
            put("Windows.SPI.HighContrastOn", originalOn);
            put("Windows.SPI.HighContrastColorScheme", originalName);
        }});
    }

}
