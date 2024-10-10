/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.glass.ui.gtk;

import com.sun.glass.ui.NonClientTheme;
import com.sun.javafx.application.PlatformImpl;
import java.util.Locale;
import java.util.Map;

enum WindowControlsTheme {

    GNOME("WindowControlsGnomeLight.css", "WindowControlsGnomeDark.css"),
    KDE("WindowControlsKdeLight.css", "WindowControlsKdeDark.css");

    WindowControlsTheme(String lightStylesheet, String darkStylesheet) {
        this.lightStylesheet = lightStylesheet;
        this.darkStylesheet = darkStylesheet;
    }

    private static final String THEME_NAME_KEY = "GTK.theme_name";

    private static final Map<String, WindowControlsTheme> SIMILAR_THEMES = Map.of(
        "adwaita", WindowControlsTheme.GNOME,
        "yaru", WindowControlsTheme.GNOME,
        "breeze", WindowControlsTheme.KDE
    );

    private final String lightStylesheet;
    private final String darkStylesheet;

    public static WindowControlsTheme getDefault() {
        return GNOME;
    }

    public static WindowControlsTheme findBestFit() {
        return PlatformImpl.getPlatformPreferences()
            .getString(THEME_NAME_KEY)
            .map(name -> {
                for (Map.Entry<String, WindowControlsTheme> entry : SIMILAR_THEMES.entrySet()) {
                    if (name.toLowerCase(Locale.ROOT).startsWith(entry.getKey())) {
                        return entry.getValue();
                    }
                }

                return null;
            })
            .orElse(switch (WindowManager.current()) {
                case GNOME -> WindowControlsTheme.GNOME;
                case KDE -> WindowControlsTheme.KDE;
                default -> getDefault();
            });
    }

    public NonClientTheme getNonClientTheme() {
        return new NonClientTheme(getLightStylesheet(), getDarkStylesheet());
    }

    private String getLightStylesheet() {
        var url = getClass().getResource(lightStylesheet);
        return url != null ? url.toExternalForm() : null;
    }

    private String getDarkStylesheet() {
        var url = getClass().getResource(darkStylesheet);
        return url != null ? url.toExternalForm() : null;
    }
}
