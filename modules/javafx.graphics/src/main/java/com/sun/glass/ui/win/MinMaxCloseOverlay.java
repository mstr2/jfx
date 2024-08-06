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

package com.sun.glass.ui.win;

import com.sun.glass.events.MouseEvent;
import com.sun.glass.ui.Window;
import com.sun.javafx.scene.DirtyBits;
import com.sun.javafx.scene.NodeHelper;
import com.sun.javafx.util.Utils;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.ResourceBundle;

public class MinMaxCloseOverlay extends Parent {

    private static final String CLOSE_GLYPH;
    private static final String MINIMIZE_GLYPH;
    private static final String MAXIMIZE_GLYPH;
    private static final String RESTORE_GLYPH;
    private static final double BUTTON_WIDTH = 46;
    private static final double BUTTON_HEIGHT = 29;
    private static final double GLYPH_OFFSET_X = -5;
    private static final double GLYPH_OFFSET_Y = 11;
    private static final String RESOURCE_BUNDLE_NAME = "com/sun/glass/ui/win/minmaxclose";
    private static final PseudoClass HOVER_PSEUDOCLASS = PseudoClass.getPseudoClass("hover");
    private static final PseudoClass PRESSED_PSEUDOCLASS = PseudoClass.getPseudoClass("pressed");

    private final Window window;
    private final Rectangle minimizeButton, maximizeButton, closeButton;
    private final SVGPath minimizeGlyph, maximizeGlyph, closeGlyph;
    private Node mouseDownButton;
    private double width;

    static {
        var bundle = ResourceBundle.getBundle(RESOURCE_BUNDLE_NAME);
        MINIMIZE_GLYPH = bundle.getString("minimize");
        MAXIMIZE_GLYPH = bundle.getString("maximize");
        RESTORE_GLYPH = bundle.getString("restore");
        CLOSE_GLYPH = bundle.getString("close");
    }

    public MinMaxCloseOverlay(Window window) {
        this.window = window;

        sceneProperty().flatMap(Scene::fillProperty).map(paint -> {
            if (paint instanceof Color color) {
                return Utils.calculateBrightness(color) < 0.5;
            }

            return false;
        }).subscribe(this::updateStylesheet);

        minimizeButton = new Rectangle(BUTTON_WIDTH, BUTTON_HEIGHT);
        minimizeButton.setId("minimize");

        maximizeButton = new Rectangle(BUTTON_WIDTH, BUTTON_HEIGHT);
        maximizeButton.setId("maximize");

        closeButton = new Rectangle(BUTTON_WIDTH, BUTTON_HEIGHT);
        closeButton.setId("close");

        minimizeGlyph = new SVGPath();
        minimizeGlyph.setId("minimize-glyph");
        minimizeGlyph.setLayoutY(GLYPH_OFFSET_Y);
        minimizeGlyph.setContent(MINIMIZE_GLYPH);

        maximizeGlyph = new SVGPath();
        maximizeGlyph.setId("maximize-glyph");
        maximizeGlyph.setLayoutY(GLYPH_OFFSET_Y);
        maximizeGlyph.contentProperty().bind(
            sceneProperty()
                .flatMap(Scene::windowProperty)
                .flatMap(w -> ((Stage)w).maximizedProperty())
                .map(maximized -> maximized ? RESTORE_GLYPH : MAXIMIZE_GLYPH));

        closeGlyph = new SVGPath();
        closeGlyph.setId("close-glyph");
        closeGlyph.setLayoutY(GLYPH_OFFSET_Y);
        closeGlyph.setContent(CLOSE_GLYPH);

        getChildren().addAll(minimizeButton, maximizeButton, closeButton, minimizeGlyph, maximizeGlyph, closeGlyph);
    }

    public boolean handleMouseEvent(int type, int button, int x, int y) {
        if (!MouseEvent.isNonClientEvent(type)) {
            return false;
        }

        double wx = x / window.getPlatformScaleX();
        double wy = y / window.getPlatformScaleY();
        HitTestResult hitTestResult = hitTest(wx, wy);
        Node node = switch (hitTestResult) {
            case MIN_BUTTON -> minimizeButton;
            case MAX_BUTTON -> maximizeButton;
            case CLOSE_BUTTON -> closeButton;
            default -> null;
        };

        if (type == MouseEvent.NC_ENTER || type == MouseEvent.NC_MOVE) {
            handleMouseOver(node);
        } else if (type == MouseEvent.NC_EXIT) {
            handleMouseExit();
        } else if (node != null && type == MouseEvent.NC_DOWN && button == MouseEvent.BUTTON_LEFT) {
            handleMouseDown(node);
        } else if (node != null && type == MouseEvent.NC_UP && button == MouseEvent.BUTTON_LEFT) {
            handleMouseUp(node, hitTestResult);
        }

        return true;
    }

    private void handleMouseOver(Node button) {
        setPseudoClass(minimizeButton, HOVER_PSEUDOCLASS, button == minimizeButton);
        setPseudoClass(maximizeButton, HOVER_PSEUDOCLASS, button == maximizeButton);
        setPseudoClass(closeButton, HOVER_PSEUDOCLASS, button == closeButton);

        if (mouseDownButton != null && mouseDownButton != button) {
            setPseudoClass(mouseDownButton, PRESSED_PSEUDOCLASS, false);
            mouseDownButton = null;
        }
    }

    private void handleMouseExit() {
        for (var node : new Node[] {minimizeButton, maximizeButton, closeButton}) {
            setPseudoClass(node, HOVER_PSEUDOCLASS, false);
            setPseudoClass(node, PRESSED_PSEUDOCLASS, false);
        }
    }

    private void handleMouseDown(Node node) {
        setPseudoClass(node, PRESSED_PSEUDOCLASS, true);
        mouseDownButton = node;
    }

    private void handleMouseUp(Node node, HitTestResult hitTestResult) {
        boolean releasedOnButton = mouseDownButton == node;
        mouseDownButton = null;

        Scene scene = getScene();
        if (scene == null || !(scene.getWindow() instanceof Stage stage)) {
            return;
        }

        setPseudoClass(node, PRESSED_PSEUDOCLASS, false);

        if (releasedOnButton) {
            switch (hitTestResult) {
                case MIN_BUTTON -> stage.setIconified(true);
                case MAX_BUTTON -> stage.setMaximized(!stage.isMaximized());
                case CLOSE_BUTTON -> stage.close();
            }
        }
    }

    private void setPseudoClass(Node button, PseudoClass pseudoClass, boolean value) {
        button.pseudoClassStateChanged(pseudoClass, value);
        glyphOf(button).ifPresent(node -> node.pseudoClassStateChanged(pseudoClass, value));
    }

    private Optional<Node> glyphOf(Node button) {
        if (button == minimizeButton) {
            return Optional.of(minimizeGlyph);
        }

        if (button == maximizeButton) {
            return Optional.of(maximizeGlyph);
        }

        if (button == closeButton) {
            return Optional.of(closeGlyph);
        }

        return Optional.empty();
    }

    private void updateStylesheet(Boolean oldDarkMode, Boolean newDarkMode) {
        String color = newDarkMode ? "white" : "black";
        String stylesheet = ResourceBundle.getBundle(RESOURCE_BUNDLE_NAME).getString("stylesheet");
        getStylesheets().add("data:text/css;base64," + Base64.getEncoder().encodeToString(
            stylesheet.formatted(color, color, color).getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public boolean isResizable() {
        return true;
    }

    @Override
    public void resize(double width, double height) {
        this.width = width;
        NodeHelper.layoutBoundsChanged(this);
        NodeHelper.geomChanged(this);
        NodeHelper.markDirty(this, DirtyBits.NODE_GEOMETRY);
        setNeedsLayout(true);
        requestParentLayout();
    }

    @Override
    protected void layoutChildren() {
        minimizeButton.setLayoutX(width - BUTTON_WIDTH * 3);
        maximizeButton.setLayoutX(width - BUTTON_WIDTH * 2);
        closeButton.setLayoutX(width - BUTTON_WIDTH);
        minimizeGlyph.setLayoutX(width - (BUTTON_WIDTH * 2 + BUTTON_WIDTH / 2) + GLYPH_OFFSET_X);
        maximizeGlyph.setLayoutX(width - (BUTTON_WIDTH + BUTTON_WIDTH / 2) + GLYPH_OFFSET_X);
        closeGlyph.setLayoutX(width - BUTTON_WIDTH / 2 + GLYPH_OFFSET_X);
    }

    public HitTestResult hitTest(double x, double y) {
        if (minimizeButton.getBoundsInParent().contains(x, y)) {
            return HitTestResult.MIN_BUTTON;
        }

        if (maximizeButton.getBoundsInParent().contains(x, y)) {
            return HitTestResult.MAX_BUTTON;
        }

        if (closeButton.getBoundsInParent().contains(x, y)) {
            return HitTestResult.CLOSE_BUTTON;
        }

        return HitTestResult.CLIENT;
    }
}
