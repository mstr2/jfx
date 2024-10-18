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

package javafx.scene.layout;

import com.sun.glass.ui.WindowOverlayMetrics;
import com.sun.javafx.stage.StageHelper;
import com.sun.javafx.tk.quantum.WindowStage;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Dimension2D;
import javafx.geometry.HorizontalDirection;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Subscription;

/**
 * Base class for client-area header bars, intended for application developers to use as a starting
 * point for custom header bar implementations. This class enables the <em>drag to move</em> and
 * <em>double-click to maximize</em> behaviors that are usually afforded by system-provided header bars.
 * <p>
 * Client-area header bars are used as a replacement for the system-provided header bar in stages
 * with the {@link StageStyle#EXTENDED} style.
 * <p>
 * Most application developers should use the {@link HeaderBar} implementation instead of
 * creating a custom header bar.
 *
 * @see HeaderBar
 * @since 24
 */
public abstract class HeaderBarBase extends Region {

    private Subscription metricsSubscription;
    private WindowOverlayMetrics currentMetrics;
    private boolean currentFullScreen;

    protected HeaderBarBase() {
        var stage = sceneProperty()
            .flatMap(Scene::windowProperty)
            .map(w -> w instanceof Stage s ? s : null);

        stage.flatMap(Window::showingProperty)
            .orElse(false)
            .subscribe(this::onShowingChanged);

        stage.flatMap(Stage::fullScreenProperty)
            .orElse(false)
            .subscribe(this::onFullScreenChanged);
    }

    private void onShowingChanged(boolean showing) {
        if (!showing) {
            if (metricsSubscription != null) {
                metricsSubscription.unsubscribe();
                metricsSubscription = null;
            }
        } else if (getScene().getWindow() instanceof Stage stage
                   && StageHelper.getPeer(stage) instanceof WindowStage windowStage) {
            ObservableValue<WindowOverlayMetrics> metrics =
                windowStage.getPlatformWindow().windowOverlayMetrics();

            if (metrics != null) {
                metricsSubscription = metrics.subscribe(this::onMetricsChanged);
            }
        }
    }

    private void onMetricsChanged(WindowOverlayMetrics metrics) {
        currentMetrics = metrics;
        updateInsets();
    }

    private void onFullScreenChanged(boolean fullScreen) {
        currentFullScreen = fullScreen;
        updateInsets();
    }

    private void updateInsets() {
        if (currentFullScreen || currentMetrics == null) {
            leftInset.set(new Dimension2D(0, 0));
            rightInset.set(new Dimension2D(0, 0));
        } else if (currentMetrics.placement() == HorizontalDirection.LEFT) {
            leftInset.set(currentMetrics.size());
            rightInset.set(new Dimension2D(0, 0));
        } else if (currentMetrics.placement() == HorizontalDirection.RIGHT) {
            leftInset.set(new Dimension2D(0, 0));
            rightInset.set(currentMetrics.size());
        } else {
            leftInset.set(new Dimension2D(0, 0));
            rightInset.set(new Dimension2D(0, 0));
        }
    }

    private final ReadOnlyObjectWrapper<Dimension2D> leftInset =
        new ReadOnlyObjectWrapper<>(this, "leftInset", new Dimension2D(0, 0)) {
            @Override
            protected void invalidated() {
                requestLayout();
            }
        };

    public final ReadOnlyObjectWrapper<Dimension2D> leftInsetProperty() {
        return leftInset;
    }

    public final Dimension2D getLeftInset() {
        return leftInset.get();
    }

    private final ReadOnlyObjectWrapper<Dimension2D> rightInset =
        new ReadOnlyObjectWrapper<>(this, "rightInset", new Dimension2D(0, 0)) {
            @Override
            protected void invalidated() {
                requestLayout();
            }
        };

    public final ReadOnlyObjectWrapper<Dimension2D> rightInsetProperty() {
        return rightInset;
    }

    public final Dimension2D getRightInset() {
        return rightInset.get();
    }
}
