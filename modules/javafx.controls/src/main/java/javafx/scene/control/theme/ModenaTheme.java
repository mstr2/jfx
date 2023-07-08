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

package javafx.scene.control.theme;

import com.sun.javafx.PlatformUtil;
import com.sun.javafx.application.PlatformImpl;
import com.sun.javafx.scene.control.HighContrastScheme;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.beans.value.WritableValue;
import javafx.css.Stylesheet;
import java.util.ResourceBundle;

/**
 * {@code Modena} is the default built-in JavaFX theme as of JavaFX 8.
 *
 * @since 21
 */
public class ModenaTheme extends ThemeBase {

    private final WritableValue<Stylesheet> highContrastStylesheet;

    /**
     * Creates a new instance of the {@code ModenaTheme} class.
     */
    public ModenaTheme() {
        try {
            addLast(loadThemeStylesheet("com/sun/javafx/scene/control/skin/modena/modena.css"));

            if (PlatformImpl.isSupported(ConditionalFeature.INPUT_TOUCH)) {
                addLast(loadThemeStylesheet("com/sun/javafx/scene/control/skin/modena/touch.css"));
            }

            if (PlatformUtil.isEmbedded()) {
                addLast(loadThemeStylesheet("com/sun/javafx/scene/control/skin/modena/modena-embedded-performance.css"));
            }

            if (PlatformUtil.isAndroid()) {
                addLast(loadThemeStylesheet("com/sun/javafx/scene/control/skin/modena/android.css"));
            }

            if (PlatformUtil.isIOS()) {
                addLast(loadThemeStylesheet("com/sun/javafx/scene/control/skin/modena/ios.css"));
            }

            if (PlatformImpl.isSupported(ConditionalFeature.TWO_LEVEL_FOCUS)) {
                addLast(loadThemeStylesheet("com/sun/javafx/scene/control/skin/modena/two-level-focus.css"));
            }

            if (PlatformImpl.isSupported(ConditionalFeature.VIRTUAL_KEYBOARD)) {
                addLast(loadThemeStylesheet("com/sun/javafx/scene/control/skin/caspian/fxvk.css"));
            }

            if (!PlatformImpl.isSupported(ConditionalFeature.TRANSPARENT_WINDOW)) {
                addLast(loadThemeStylesheet("com/sun/javafx/scene/control/skin/modena/modena-no-transparency.css"));
            }

            highContrastStylesheet = addLast(null);
            updateHighContrastTheme();
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }

    @Override
    protected void onPreferencesChanged() {
        updateHighContrastTheme();
    }

    private void updateHighContrastTheme() {
        String themeName = null;
        String overrideThemeName = System.getProperty("com.sun.javafx.highContrastTheme");
        if (overrideThemeName != null) {
            themeName = overrideThemeName;
        }

        if (themeName == null) {
            Platform.Preferences preferences = Platform.getPreferences();
            if (preferences.getBoolean("Windows.SPI.HighContrastOn").orElse(false)) {
                themeName = preferences.getString("Windows.SPI.HighContrastColorScheme").orElse(null);
            }
        }

        if (themeName != null) {
            String stylesheetName = switch (themeName.toUpperCase()) {
                case "BLACKONWHITE" -> "com/sun/javafx/scene/control/skin/modena/blackOnWhite.css";
                case "WHITEONBLACK" -> "com/sun/javafx/scene/control/skin/modena/whiteOnBlack.css";
                case "YELLOWONBLACK" -> "com/sun/javafx/scene/control/skin/modena/yellowOnBlack.css";
                default -> null;
            };

            if (stylesheetName == null) {
                ResourceBundle bundle = ResourceBundle.getBundle("com/sun/javafx/scene/control/high-contrast-scheme-names");
                String enumValue = HighContrastScheme.fromThemeName(bundle::getString, themeName);

                stylesheetName = enumValue != null ? switch (HighContrastScheme.valueOf(enumValue)) {
                    case HIGH_CONTRAST_WHITE -> "com/sun/javafx/scene/control/skin/modena/blackOnWhite.css";
                    case HIGH_CONTRAST_BLACK -> "com/sun/javafx/scene/control/skin/modena/whiteOnBlack.css";
                    case HIGH_CONTRAST_1, HIGH_CONTRAST_2 -> "com/sun/javafx/scene/control/skin/modena/yellowOnBlack.css";
                } : null;
            }

            Stylesheet stylesheet;
            try {
                stylesheet = loadThemeStylesheet(stylesheetName);
            } catch (Exception ignored) {
                stylesheet = null;
            }

            highContrastStylesheet.setValue(stylesheet);
        } else {
            highContrastStylesheet.setValue(null);
        }
    }

}
