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

package com.sun.glass.ui;

import com.sun.glass.events.MouseEvent;
import com.sun.javafx.util.Utils;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import javafx.util.Subscription;

public final class WindowControlsOverlay extends Region {

    private static final PseudoClass HOVER_PSEUDOCLASS = PseudoClass.getPseudoClass("hover");
    private static final PseudoClass PRESSED_PSEUDOCLASS = PseudoClass.getPseudoClass("pressed");
    private static final PseudoClass RESTORE_PSEUDOCLASS = PseudoClass.getPseudoClass("restore");
    private static final PseudoClass FOCUSED_PSEUDOCLASS = PseudoClass.getPseudoClass("focused");

    private final Window window;
    private final Subscription subscriptions;
    private final ObservableValue<NonClientTheme> theme;
    private final Region minimizeButton = new ButtonRegion("minimize-button");
    private final Region maximizeButton = new ButtonRegion("maximize-button");
    private final Region closeButton = new ButtonRegion("close-button");

    private Node mouseDownButton;

    private static class ButtonRegion extends Region {
        final Region glyph = new Region();

        ButtonRegion(String styleClass) {
            glyph.getStyleClass().setAll("glyph");
            getChildren().add(glyph);
            getStyleClass().setAll(styleClass);
        }

        @Override
        protected void layoutChildren() {
            layoutInArea(glyph, 0, 0, getWidth(), getHeight(), 0, HPos.LEFT, VPos.TOP);
        }
    }

    public WindowControlsOverlay(Window window, ObservableValue<NonClientTheme> theme) {
        this.window = window;
        this.theme = theme;

        var stage = sceneProperty()
            .flatMap(Scene::windowProperty)
            .map(w -> w instanceof Stage ? (Stage)w : null);

        var focusedSubscription = stage
            .flatMap(Stage::focusedProperty)
            .orElse(true)
            .subscribe(this::onFocusedChanged);

        var resizableSubscription = stage
            .flatMap(Stage::resizableProperty)
            .orElse(true)
            .subscribe(this::onResizableChanged);

        var maximizedSubscription = stage
            .flatMap(Stage::maximizedProperty)
            .orElse(false)
            .subscribe(this::onMaximizedChanged);

        var updateStylesheetSubscription = sceneProperty()
            .flatMap(Scene::fillProperty)
            .map(this::isDarkBackground)
            .orElse(false)
            .subscribe(this::updateStylesheet);

        subscriptions = Subscription.combine(
            focusedSubscription,
            resizableSubscription,
            maximizedSubscription,
            updateStylesheetSubscription,
            theme.subscribe(this::updateStylesheet));

        getChildren().addAll(minimizeButton, maximizeButton, closeButton);
    }

    public void dispose() {
        subscriptions.unsubscribe();
    }

    public WindowControlsMetrics getMetrics() {
        return new WindowControlsMetrics(
            HPos.RIGHT, boundedWidth(minimizeButton) + boundedWidth(maximizeButton) + boundedWidth(closeButton));
    }

    private void updateStylesheet() {
        boolean darkScene = isDarkBackground(getScene() != null ? getScene().getFill() : null);
        String stylesheet = theme.getValue().effectiveStylesheet(darkScene);

        if (stylesheet != null) {
            getStylesheets().setAll(stylesheet);
        } else {
            getStylesheets().clear();
        }
    }

    private boolean isDarkBackground(Paint paint) {
        if (paint instanceof Color color) {
            return Utils.calculateBrightness(color) < 0.5;
        }

        return false;
    }

    /**
     * Handles the specified mouse event.
     *
     * @param type the event type
     * @param button the button type
     * @param x the X coordinate, in physical pixels
     * @param y the Y coordinate, in physical pixels
     * @return {@code true} if the event was handled, {@code false} otherwise
     */
    public boolean handleMouseEvent(int type, int button, int x, int y) {
//        if (!MouseEvent.isNonClientEvent(type)) {
//            return false;
//        }

        double wx = x / window.getPlatformScaleX();
        double wy = y / window.getPlatformScaleY();
        HitTestResult hitTestResult = hitTest(wx, wy);
        Node node = hitTestResult != null ? switch (hitTestResult) {
            case MIN_BUTTON -> minimizeButton;
            case MAX_BUTTON -> maximizeButton;
            case CLOSE_BUTTON -> closeButton;
            default -> null;
        } : null;

        if (type == MouseEvent.ENTER || type == MouseEvent.MOVE || type == MouseEvent.DRAG) {
            handleMouseOver(node);
        } else if (type == MouseEvent.EXIT) {
            handleMouseExit();
        } else if (type == MouseEvent.UP && button == MouseEvent.BUTTON_LEFT) {
            handleMouseUp(node, hitTestResult);
        } else if (node != null && type == MouseEvent.DOWN && button == MouseEvent.BUTTON_LEFT) {
            handleMouseDown(node);
        }

        return node != null || mouseDownButton != null;
    }

    private void handleMouseOver(Node button) {
        minimizeButton.pseudoClassStateChanged(HOVER_PSEUDOCLASS, button == minimizeButton);
        maximizeButton.pseudoClassStateChanged(HOVER_PSEUDOCLASS, button == maximizeButton);
        closeButton.pseudoClassStateChanged(HOVER_PSEUDOCLASS, button == closeButton);

        if (mouseDownButton != null && mouseDownButton != button) {
            mouseDownButton.pseudoClassStateChanged(PRESSED_PSEUDOCLASS, false);
        }
    }

    private void handleMouseExit() {
        mouseDownButton = null;

        for (var node : new Node[] {minimizeButton, maximizeButton, closeButton}) {
            node.pseudoClassStateChanged(HOVER_PSEUDOCLASS, false);
            node.pseudoClassStateChanged(PRESSED_PSEUDOCLASS, false);
        }
    }

    private void handleMouseDown(Node node) {
        node.pseudoClassStateChanged(PRESSED_PSEUDOCLASS, true);
        mouseDownButton = node;
    }

    private void handleMouseUp(Node node, HitTestResult hitTestResult) {
        boolean releasedOnButton = mouseDownButton == node;
        mouseDownButton = null;

        Scene scene = getScene();
        if (node == null || scene == null || !(scene.getWindow() instanceof Stage stage)) {
            return;
        }

        node.pseudoClassStateChanged(PRESSED_PSEUDOCLASS, false);

        if (releasedOnButton) {
            switch (hitTestResult) {
                case MIN_BUTTON -> stage.setIconified(true);
                case MAX_BUTTON -> stage.setMaximized(!stage.isMaximized());
                case CLOSE_BUTTON -> stage.close();
            }
        }
    }

    private void onFocusedChanged(boolean focused) {
        for (var node : new Node[] {minimizeButton, maximizeButton, closeButton}) {
            node.pseudoClassStateChanged(FOCUSED_PSEUDOCLASS, focused);
        }
    }

    private void onResizableChanged(boolean resizable) {
        maximizeButton.setDisable(!resizable);
    }

    private void onMaximizedChanged(boolean maximized) {
        maximizeButton.pseudoClassStateChanged(RESTORE_PSEUDOCLASS, maximized);
    }

    @Override
    protected double computeMinWidth(double height) {
        return minimizeButton.minWidth(height) + maximizeButton.minWidth(height) + closeButton.minWidth(height);
    }

    @Override
    protected double computeMinHeight(double width) {
        double max = Math.max(minimizeButton.minHeight(width), maximizeButton.minHeight(width));
        return Math.max(closeButton.minHeight(width), max);
    }

    @Override
    protected double computeMaxWidth(double height) {
        return minimizeButton.maxWidth(height) + maximizeButton.maxWidth(height) + closeButton.maxWidth(height);
    }

    @Override
    protected double computeMaxHeight(double width) {
        double max = Math.max(minimizeButton.maxHeight(width), maximizeButton.maxHeight(width));
        return Math.max(closeButton.maxHeight(width), max);
    }

    @Override
    protected double computePrefWidth(double height) {
        return minimizeButton.prefWidth(height) + maximizeButton.prefWidth(height) + closeButton.prefWidth(height);
    }

    @Override
    protected double computePrefHeight(double width) {
        double max = Math.max(minimizeButton.prefHeight(width), maximizeButton.prefHeight(width));
        return Math.max(closeButton.prefHeight(width), max);
    }

    @Override
    protected void layoutChildren() {
        double minButtonWidth = boundedWidth(minimizeButton);
        double maxButtonWidth = boundedWidth(maximizeButton);
        double closeButtonWidth = boundedWidth(closeButton);
        double minButtonHeight = boundedHeight(minimizeButton);
        double maxButtonHeight = boundedHeight(maximizeButton);
        double closeButtonHeight = boundedHeight(closeButton);

        layoutInArea(minimizeButton, getWidth() - minButtonWidth - maxButtonWidth - closeButtonWidth, 0,
                     minButtonWidth, minButtonHeight, BASELINE_OFFSET_SAME_AS_HEIGHT, HPos.LEFT, VPos.TOP);

        layoutInArea(maximizeButton, getWidth() - maxButtonWidth - closeButtonWidth, 0,
                     maxButtonWidth, maxButtonHeight, BASELINE_OFFSET_SAME_AS_HEIGHT, HPos.LEFT, VPos.TOP);

        layoutInArea(closeButton, getWidth() - closeButtonWidth, 0,
                     closeButtonWidth, closeButtonHeight, BASELINE_OFFSET_SAME_AS_HEIGHT, HPos.LEFT, VPos.TOP);
    }

    /**
     * Classifies the area at the specified coordinate.
     *
     * @param x the X coordinate, in pixels relative to the window
     * @param y the Y coordinate, in pixels relative to the window
     * @return {@link HitTestResult#MIN_BUTTON},
     *         {@link HitTestResult#MAX_BUTTON},
     *         {@link HitTestResult#CLOSE_BUTTON},
     *         or {@code null}
     */
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

        return null;
    }

    private static double boundedWidth(Node node) {
        return boundedSize(node.minWidth(-1), node.prefWidth(-1), node.maxWidth(-1));
    }

    private static double boundedHeight(Node node) {
        return boundedSize(node.minHeight(-1), node.prefHeight(-1), node.maxHeight(-1));
    }

    private static double boundedSize(double min, double pref, double max) {
        return Math.min(Math.max(pref, min), Math.max(min, max));
    }
}
