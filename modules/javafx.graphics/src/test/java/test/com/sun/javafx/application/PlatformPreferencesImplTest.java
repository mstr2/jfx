/*
 * Copyright (c) 2023 Oracle and/or its affiliates. All rights reserved.
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

package test.com.sun.javafx.application;

import com.sun.javafx.application.PlatformPreferencesImpl;
import org.junit.jupiter.api.Test;
import javafx.scene.paint.Color;
import javafx.stage.Appearance;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PlatformPreferencesImplTest {

    @Test
    public void testDefaultValues() {
        var prefs = new PlatformPreferencesImpl();
        assertEquals(Color.WHITE, prefs.getBackgroundColor());
        assertEquals(Color.BLACK, prefs.getForegroundColor());
        assertEquals(Color.web("#157EFB"), prefs.getAccentColor());
        assertEquals(Appearance.LIGHT, prefs.getAppearance());
    }

    @Test
    public void testAppearanceReflectsForegroundAndBackgroundColors() {
        var prefs = new PlatformPreferencesImpl();

        prefs.update(Map.of("Windows.UIColor.Foreground", Color.BLACK, "Windows.UIColor.Background", Color.WHITE));
        assertEquals(Appearance.LIGHT, prefs.getAppearance());

        prefs.update(Map.of("Windows.UIColor.Foreground", Color.WHITE, "Windows.UIColor.Background", Color.BLACK));
        assertEquals(Appearance.DARK, prefs.getAppearance());

        prefs.update(Map.of("Windows.UIColor.Foreground", Color.DARKGRAY, "Windows.UIColor.Background", Color.LIGHTGRAY));
        assertEquals(Appearance.LIGHT, prefs.getAppearance());

        prefs.update(Map.of("Windows.UIColor.Foreground", Color.RED, "Windows.UIColor.Background", Color.BLUE));
        assertEquals(Appearance.DARK, prefs.getAppearance());
    }

}
