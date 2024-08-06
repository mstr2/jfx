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

public interface NonClientHelper {

    boolean handleMouseEvent(int type, int button, int x, int y, int xAbs, int yAbs, int clickCount);

    class SyntheticMoveAndResize implements NonClientHelper {
        private final Window window;
        private final View view;
        private int mouseDownX, mouseDownY;
        private int mouseDownWindowX, mouseDownWindowY;
        private boolean toggleMaximizedOnRelease;
        private boolean dragging;

        public SyntheticMoveAndResize(View view, Window window) {
            this.view = view;
            this.window = window;
        }

        public boolean handleMouseEvent(int type, int button, int x, int y, int xAbs, int yAbs, int clickCount) {
            return switch (type) {
                case MouseEvent.UP -> handleMouseUp(button);
                case MouseEvent.DOWN -> handleMouseDown(button, x, y, xAbs, yAbs, clickCount);
                case MouseEvent.DRAG -> handleMouseDrag(xAbs, yAbs);
                default -> false;
            };
        }

        private boolean handleMouseDown(int button, int x, int y, int xAbs, int yAbs, int clickCount) {
            if (button == MouseEvent.BUTTON_LEFT) {
                mouseDownX = xAbs;
                mouseDownY = yAbs;
                mouseDownWindowX = window.getX();
                mouseDownWindowY = window.getY();
                dragging = isNonClientRegion(x, y);

                if (dragging && clickCount == 2 && !view.isInFullscreen()) {
                    toggleMaximizedOnRelease = true;
                }

                return dragging;
            }

            return false;
        }

        private boolean handleMouseUp(int button) {
            if (dragging && button == MouseEvent.BUTTON_LEFT) {
                dragging = false;

                if (toggleMaximizedOnRelease) {
                    toggleMaximizedOnRelease = false;
                    window.maximize(!window.isMaximized());
                }

                return true;
            }

            return false;
        }

        private boolean handleMouseDrag(int xAbs, int yAbs) {
            if (dragging) {
                toggleMaximizedOnRelease = false;

                // We always handle mouse interactions in the non-client drag area for consistency,
                // but only interfere with the window position if we're not in full-screen mode.
                if (!view.isInFullscreen()) {
                    window.setPosition(
                        mouseDownWindowX + xAbs - mouseDownX,
                        mouseDownWindowY + yAbs - mouseDownY);
                }

                return true;
            }

            return false;
        }

        private boolean isNonClientRegion(int x, int y) {
            View.EventHandler eventHandler = view.getEventHandler();
            return eventHandler != null && eventHandler.isNonClientRegion(
                (int)(x / window.getPlatformScaleX()),
                (int)(y / window.getPlatformScaleY()));
        }
    }
}
