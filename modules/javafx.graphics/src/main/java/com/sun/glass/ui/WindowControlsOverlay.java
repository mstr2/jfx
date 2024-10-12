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
import javafx.stage.StageStyle;
import javafx.util.Subscription;

/**
 * Contains the visuals and logic for the minimize/maximize/close buttons on an {@link StageStyle#EXTENDED}
 * window for platforms that use client-side decorations (Windows and Linux/GTK).
 */
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

    public enum ButtonType {
        MINIMIZE,
        MAXIMIZE,
        CLOSE
    }

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

    public WindowOverlayMetrics getMetrics() {
        double minButtonWidth = boundedWidth(minimizeButton);
        double maxButtonWidth = boundedWidth(maximizeButton);
        double closeButtonWidth = boundedWidth(closeButton);
        double minButtonHeight = boundedHeight(minimizeButton);
        double maxButtonHeight = boundedHeight(maximizeButton);
        double closeButtonHeight = boundedHeight(closeButton);

        return new WindowOverlayMetrics(
            HPos.RIGHT,
            minButtonWidth + maxButtonWidth + closeButtonWidth,
            minButtonHeight + maxButtonHeight + closeButtonHeight);
    }

    /**
     * Classifies the button at the specified coordinate, or returns {@code null} if the
     * specified coordinate does not intersect a button.
     *
     * @param x the X coordinate, in pixels relative to the window
     * @param y the Y coordinate, in pixels relative to the window
     * @return the {@code ButtonType} or {@code null}
     */
    public ButtonType buttonAt(double x, double y) {
        if (minimizeButton.getBoundsInParent().contains(x, y)) {
            return ButtonType.MINIMIZE;
        }

        if (maximizeButton.getBoundsInParent().contains(x, y)) {
            return ButtonType.MAXIMIZE;
        }

        if (closeButton.getBoundsInParent().contains(x, y)) {
            return ButtonType.CLOSE;
        }

        return null;
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
        double wx = x / window.getPlatformScaleX();
        double wy = y / window.getPlatformScaleY();
        ButtonType buttonType = buttonAt(wx, wy);
        Node node = buttonType != null ? switch (buttonType) {
            case MINIMIZE -> minimizeButton;
            case MAXIMIZE -> maximizeButton;
            case CLOSE -> closeButton;
        } : null;

        if (type == MouseEvent.NC_ENTER || type == MouseEvent.NC_MOVE || type == MouseEvent.NC_DRAG) {
            handleMouseOver(node);
        } else if (type == MouseEvent.NC_EXIT) {
            handleMouseExit();
        } else if (type == MouseEvent.NC_UP && button == MouseEvent.BUTTON_LEFT) {
            handleMouseUp(node, buttonType);
        } else if (node != null && type == MouseEvent.NC_DOWN && button == MouseEvent.BUTTON_LEFT) {
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
        for (var node : new Node[] {minimizeButton, maximizeButton, closeButton}) {
            node.pseudoClassStateChanged(HOVER_PSEUDOCLASS, false);
            node.pseudoClassStateChanged(PRESSED_PSEUDOCLASS, false);
        }
    }

    private void handleMouseDown(Node node) {
        mouseDownButton = node;

        if (!node.isDisabled()) {
            node.pseudoClassStateChanged(PRESSED_PSEUDOCLASS, true);
        }
    }

    private void handleMouseUp(Node node, ButtonType buttonType) {
        boolean releasedOnButton = mouseDownButton == node;
        mouseDownButton = null;
        Scene scene = getScene();

        if (node == null || node.isDisabled()
                || scene == null || !(scene.getWindow() instanceof Stage stage)) {
            return;
        }

        node.pseudoClassStateChanged(PRESSED_PSEUDOCLASS, false);

        if (releasedOnButton) {
            switch (buttonType) {
                case MINIMIZE -> stage.setIconified(true);
                case MAXIMIZE -> stage.setMaximized(!stage.isMaximized());
                case CLOSE -> stage.close();
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

    private static double boundedWidth(Node node) {
        return node.isManaged() ? boundedSize(node.minWidth(-1), node.prefWidth(-1), node.maxWidth(-1)) : 0;
    }

    private static double boundedHeight(Node node) {
        return node.isManaged() ? boundedSize(node.minHeight(-1), node.prefHeight(-1), node.maxHeight(-1)) : 0;
    }

    private static double boundedSize(double min, double pref, double max) {
        return Math.min(Math.max(pref, min), Math.max(min, max));
    }
}
