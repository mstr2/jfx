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
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.MapChangeListener;

final class NonClientThemeChooser {

    private static final String THEME_NAME_KEY = "GTK.theme_name";

    private final ReadOnlyObjectWrapper<NonClientTheme> nonClientTheme =
            new ReadOnlyObjectWrapper<>(this, "nonClientTheme");

    private NonClientThemeChooser() {
        PlatformImpl.getPlatformPreferences().addListener((MapChangeListener<String, Object>) change -> {
            if (THEME_NAME_KEY.equals(change.getKey())) {
                updateThemeStylesheets();
            }
        });

        updateThemeStylesheets();
    }

    public static NonClientThemeChooser getInstance() {
        class Holder {
            static final NonClientThemeChooser instance = new NonClientThemeChooser();
        }

        return Holder.instance;
    }

    public ReadOnlyObjectProperty<NonClientTheme> nonClientThemeProperty() {
        return nonClientTheme.getReadOnlyProperty();
    }

    private void updateThemeStylesheets() {
        var controlsTheme = WindowControlsTheme.findBestFit();
        nonClientTheme.set(controlsTheme.getNonClientTheme());
    }
}
