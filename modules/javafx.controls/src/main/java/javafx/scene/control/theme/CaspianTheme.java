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
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.beans.value.WritableValue;
import javafx.css.Stylesheet;

/**
 * {@code Caspian} is a built-in JavaFX theme that shipped as default in JavaFX 2.
 *
 * @since 21
 */
public class CaspianTheme extends ThemeBase {

    private final WritableValue<Stylesheet> highContrastStylesheet;

    /**
     * Creates a new instance of the {@code CaspianTheme} class.
     */
    public CaspianTheme() {
        try {
            addLast(loadThemeStylesheet("com/sun/javafx/scene/control/skin/caspian/caspian.css"));

            if (PlatformImpl.isSupported(ConditionalFeature.INPUT_TOUCH)) {
                addLast(loadThemeStylesheet("com/sun/javafx/scene/control/skin/caspian/embedded.css"));

                if (com.sun.javafx.util.Utils.isQVGAScreen()) {
                    addLast(loadThemeStylesheet("com/sun/javafx/scene/control/skin/caspian/embedded-qvga.css"));
                }

                if (PlatformUtil.isAndroid()) {
                    addLast(loadThemeStylesheet("com/sun/javafx/scene/control/skin/caspian/android.css"));
                }

                if (PlatformUtil.isIOS()) {
                    addLast(loadThemeStylesheet("com/sun/javafx/scene/control/skin/caspian/ios.css"));
                }
            }

            if (PlatformImpl.isSupported(ConditionalFeature.TWO_LEVEL_FOCUS)) {
                addLast(loadThemeStylesheet("com/sun/javafx/scene/control/skin/caspian/two-level-focus.css"));
            }

            if (PlatformImpl.isSupported(ConditionalFeature.VIRTUAL_KEYBOARD)) {
                addLast(loadThemeStylesheet("com/sun/javafx/scene/control/skin/caspian/fxvk.css"));
            }

            if (!PlatformImpl.isSupported(ConditionalFeature.TRANSPARENT_WINDOW)) {
                addLast(loadThemeStylesheet("com/sun/javafx/scene/control/skin/caspian/caspian-no-transparency.css"));
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
        boolean enabled = false;
        String overrideThemeName = System.getProperty("com.sun.javafx.highContrastTheme");
        if (overrideThemeName != null) {
            enabled = true;
        }

        if (!enabled) {
            Platform.Preferences preferences = Platform.getPreferences();
            if (preferences.getBoolean("Windows.SPI.HighContrastOn").orElse(false)) {
                enabled = preferences.getString("Windows.SPI.HighContrastColorScheme").isPresent();
            }
        }

        if (enabled) {
            // caspian has only one high contrast theme, use it regardless of the user or platform theme.
            Stylesheet stylesheet;
            try {
                stylesheet = loadThemeStylesheet("com/sun/javafx/scene/control/skin/caspian/highcontrast.css");
            } catch (Exception ignored) {
                stylesheet = null;
            }

            highContrastStylesheet.setValue(stylesheet);
        } else {
            highContrastStylesheet.setValue(null);
        }
    }

}
