/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import javafx.stage.WindowArea;
import java.util.HashMap;
import java.util.Map;

public final class MoveResizeHelper {

    private static final Map<WindowArea, Cursor> RESIZE_CURSORS = new HashMap<>();

    static {
        Application application = Application.GetApplication();
        RESIZE_CURSORS.put(WindowArea.TOP_LEFT, application.createCursor(Cursor.CURSOR_RESIZE_NORTHWEST));
        RESIZE_CURSORS.put(WindowArea.TOP_RIGHT, application.createCursor(Cursor.CURSOR_RESIZE_NORTHEAST));
        RESIZE_CURSORS.put(WindowArea.BOTTOM_LEFT, application.createCursor(Cursor.CURSOR_RESIZE_SOUTHWEST));
        RESIZE_CURSORS.put(WindowArea.BOTTOM_RIGHT, application.createCursor(Cursor.CURSOR_RESIZE_SOUTHEAST));
        RESIZE_CURSORS.put(WindowArea.LEFT, application.createCursor(Cursor.CURSOR_RESIZE_LEFTRIGHT));
        RESIZE_CURSORS.put(WindowArea.RIGHT, application.createCursor(Cursor.CURSOR_RESIZE_LEFTRIGHT));
        RESIZE_CURSORS.put(WindowArea.TOP, application.createCursor(Cursor.CURSOR_RESIZE_UPDOWN));
        RESIZE_CURSORS.put(WindowArea.BOTTOM, application.createCursor(Cursor.CURSOR_RESIZE_UPDOWN));
    }

    private final View view;
    private final Window window;
    private int mouseDownX, mouseDownY;
    private int mouseDownWindowX, mouseDownWindowY;
    private int mouseDownWindowWidth, mouseDownWindowHeight;
    private WindowArea currentWindowArea;
    private Cursor lastCursor;

    public MoveResizeHelper(View view, Window window) {
        this.view = view;
        this.window = window;
    }

    public boolean handleMouseEvent(int type, int button, int x, int y, int xAbs, int yAbs) {
        int wx = (int)(x / window.getPlatformScaleX());
        int wy = (int)(y / window.getPlatformScaleY());

        if (type != MouseEvent.DRAG) {
            View.EventHandler eventHandler = view.getEventHandler();
//            currentWindowArea = eventHandler != null ? eventHandler.pickWindowArea(wx, wy) : null;
            updateCursor(currentWindowArea);

            if (currentWindowArea == null) {
                return false;
            }
        }

        switch (type) {
            case MouseEvent.DRAG -> handleMouseDrag(button, xAbs, yAbs, currentWindowArea);
            case MouseEvent.DOWN -> handleMouseDown(xAbs, yAbs);
        }

        return false;
    }

    private boolean shouldStartMoveDrag(int button, WindowArea region) {
        return button == MouseEvent.BUTTON_LEFT && region == WindowArea.TITLE;
    }

    private boolean shouldStartResizeDrag(int button, WindowArea region) {
        Cursor cursor = region != null ? RESIZE_CURSORS.get(region) : null;
        return button == MouseEvent.BUTTON_LEFT && cursor != null;
    }

    private void handleMouseDrag(int button, int xAbs, int yAbs, WindowArea area) {
        if (shouldStartMoveDrag(button, area)) {
            handleMoveWindow(xAbs, yAbs);
        } else if (shouldStartResizeDrag(button, area)) {
            handleResizeWindow(xAbs, yAbs, area);
        }
    }

    private void handleMouseDown(int xAbs, int yAbs) {
        mouseDownX = xAbs;
        mouseDownY = yAbs;
        mouseDownWindowX = window.getX();
        mouseDownWindowY = window.getY();
        mouseDownWindowWidth = window.getWidth();
        mouseDownWindowHeight = window.getHeight();
    }

    private void handleMoveWindow(int xAbs, int yAbs) {
        window.setPosition(mouseDownWindowX + xAbs - mouseDownX, mouseDownWindowY + yAbs - mouseDownY);
    }

    private void handleResizeWindow(int xAbs, int yAbs, WindowArea region) {
        int dx = xAbs - mouseDownX;
        int dy = yAbs - mouseDownY;

        switch (region) {
            case LEFT -> {
                adjustWindowPosition(dx, 0);
                adjustWindowSize(-dx, 0);
            }
            case RIGHT -> adjustWindowSize(dx, 0);
            case TOP -> {
                adjustWindowPosition(0, dy);
                adjustWindowSize(0, -dy);
            }
            case BOTTOM -> adjustWindowSize(0, dy);
            case TOP_LEFT -> {
                adjustWindowPosition(dx, dy);
                adjustWindowSize(-dx, -dy);
            }
            case TOP_RIGHT -> {
                adjustWindowPosition(0, dy);
                adjustWindowSize(dx, -dy);
            }
            case BOTTOM_LEFT -> {
                adjustWindowPosition(dx, 0);
                adjustWindowSize(-dx, dy);
            }
            case BOTTOM_RIGHT -> adjustWindowSize(dx, dy);
        }
    }

    private void adjustWindowPosition(int dx, int dy) {
        int unclampedWidth = mouseDownWindowWidth - dx;
        int unclampedHeight = mouseDownWindowHeight - dy;
        int clampedWidth = dx != 0 ? clampWidth(unclampedWidth) : unclampedWidth;
        int clampedHeight = dy != 0 ? clampHeight(unclampedHeight) : unclampedHeight;
        int cx = unclampedWidth - clampedWidth;
        int cy = unclampedHeight - clampedHeight;
        window.setPosition(mouseDownWindowX + dx + cx, mouseDownWindowY + dy + cy);
    }

    private void adjustWindowSize(int dx, int dy) {
        int width = dx != 0 ? clampWidth(mouseDownWindowWidth + dx) : mouseDownWindowWidth;
        int height = dy != 0 ? clampHeight(mouseDownWindowHeight + dy) : mouseDownWindowHeight;
        window.setSize(width, height);
    }

    private int clampWidth(int width) {
        return Math.max(window.getMinimumWidth(), Math.min(window.getMaximumWidth(), width));
    }

    private int clampHeight(int height) {
        return Math.max(window.getMinimumHeight(), Math.min(window.getMaximumHeight(), height));
    }

    private void updateCursor(WindowArea area) {
        Cursor newCursor = area != null ? RESIZE_CURSORS.get(area) : null;

        if (lastCursor == null && newCursor != null) {
            lastCursor = window.getCursor();
        } else if (lastCursor != null && newCursor == null) {
            window.setCursor(lastCursor);
            lastCursor = null;
        }

        if (newCursor != null) {
            window.setCursor(newCursor);
        }
    }

}
