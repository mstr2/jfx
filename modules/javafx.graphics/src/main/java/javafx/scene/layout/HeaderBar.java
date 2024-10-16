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

import com.sun.javafx.geom.Vec2d;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;

/**
 *
 */
public class HeaderBar extends HeaderBarBase {

    private static final String MARGIN = "headerbar-margin";
    private static final String ALIGNMENT = "headerbar-alignment";

    /**
     * Sets the alignment for the child when contained in a {@code HeaderBar}.
     * If set, will override the header bar's default alignment for the child's position.
     * Setting the value to {@code null} will remove the constraint.
     *
     * @param child the child node
     * @param value the alignment position
     */
    public static void setAlignment(Node child, Pos value) {
        Pane.setConstraint(child, ALIGNMENT, value);
    }

    /**
     * Returns the child's alignment in the {@code HeaderBar}.
     *
     * @param child the child node
     * @return the alignment position for the child, or {@code null} if no alignment was set
     */
    public static Pos getAlignment(Node child) {
        return (Pos)Pane.getConstraint(child, ALIGNMENT);
    }

    /**
     * Sets the margin for the child when contained in a {@code HeaderBar}.
     * If set, the header bar will lay it out with the margin space around it.
     * Setting the value to {@code null} will remove the constraint.
     *
     * @param child the child node
     * @param value the margin of space around the child
     */
    public static void setMargin(Node child, Insets value) {
        Pane.setConstraint(child, MARGIN, value);
    }

    /**
     * Returns the child's margin.
     *
     * @param child the child node
     * @return the margin for the child, or {@code null} if no margin was set
     */
    public static Insets getMargin(Node child) {
        return (Insets)Pane.getConstraint(child, MARGIN);
    }

    private static Insets getNodeMargin(Node child) {
        Insets margin = getMargin(child);
        return margin != null ? margin : Insets.EMPTY;
    }

    public HeaderBar() {
    }

    private final ObjectProperty<Node> left = new NodeProperty("left");

    public final ObjectProperty<Node> leftProperty() {
        return left;
    }

    public final Node getLeft() {
        return left.get();
    }

    public final void setLeft(Node value) {
        left.set(value);
    }

    private final ObjectProperty<Node> center = new NodeProperty("center");

    public final ObjectProperty<Node> centerProperty() {
        return center;
    }

    public final Node getCenter() {
        return center.get();
    }

    public final void setCenter(Node value) {
        center.set(value);
    }

    private final ObjectProperty<Node> right = new NodeProperty("right");

    public final ObjectProperty<Node> rightProperty() {
        return right;
    }

    public final Node getRight() {
        return right.get();
    }

    public final void setRight(Node value) {
        right.set(value);
    }

    @Override
    protected double computeMinWidth(double height) {
        Node left = getLeft(), center = getCenter(), right = getRight();
        Insets insets = getInsets();
        double leftPrefWidth;
        double rightPrefWidth;
        double centerMinWidth;

        if (height != -1
                && (childHasContentBias(left, Orientation.VERTICAL) ||
                    childHasContentBias(right, Orientation.VERTICAL) ||
                    childHasContentBias(center, Orientation.VERTICAL))) {
            double areaHeight = Math.max(0, height);
            leftPrefWidth = getAreaWidth(left, areaHeight, false);
            rightPrefWidth = getAreaWidth(right, areaHeight, false);
            centerMinWidth = getAreaWidth(center, areaHeight, true);
        } else {
            leftPrefWidth = getAreaWidth(left, -1, false);
            rightPrefWidth = getAreaWidth(right, -1, false);
            centerMinWidth = getAreaWidth(center, -1, true);
        }

        return insets.getLeft()
             + leftPrefWidth
             + centerMinWidth
             + rightPrefWidth
             + insets.getRight()
             + getLeftInset().getWidth()
             + getRightInset().getWidth();
    }

    @Override
    protected double computeMinHeight(double width) {
        Node left = getLeft(), center = getCenter(), right = getRight();
        Insets insets = getInsets();
        double leftMinHeight = getAreaHeight(left, -1, true);
        double rightMinHeight = getAreaHeight(right, -1, true);
        double centerMinHeight;

        if (width != -1 && childHasContentBias(center, Orientation.HORIZONTAL)) {
            double leftPrefWidth = getAreaWidth(left, -1, false);
            double rightPrefWidth = getAreaWidth(right, -1, false);
            centerMinHeight = getAreaHeight(center, Math.max(0, width - leftPrefWidth - rightPrefWidth), true);
        } else {
            centerMinHeight = getAreaHeight(center, -1, true);
        }

        return insets.getTop()
             + insets.getBottom()
             + Math.max(centerMinHeight, Math.max(rightMinHeight, leftMinHeight));
    }

    @Override
    protected double computePrefHeight(double width) {
        Node left = getLeft(), center = getCenter(), right = getRight();
        Insets insets = getInsets();
        double leftPrefHeight = getAreaHeight(left, -1, false);
        double rightPrefHeight = getAreaHeight(right, -1, false);
        double centerPrefHeight;

        if (width != -1 && childHasContentBias(center, Orientation.HORIZONTAL)) {
            double leftPrefWidth = getAreaWidth(left, -1, false);
            double rightPrefWidth = getAreaWidth(right, -1, false);
            centerPrefHeight = getAreaHeight(center, Math.max(0, width - leftPrefWidth - rightPrefWidth), false);
        } else {
            centerPrefHeight = getAreaHeight(center, -1, false);
        }

        return insets.getTop()
             + insets.getBottom()
             + Math.max(centerPrefHeight, Math.max(rightPrefHeight, leftPrefHeight));
    }

    @Override
    protected void layoutChildren() {
        Insets insets = getInsets();
        double width = Math.max(getWidth(), minWidth(-1));
        double height = Math.max(getHeight(), minHeight(-1));
        double leftInset = getLeftInset().getWidth();
        double rightInset = getRightInset().getWidth();
        double insideX = insets.getLeft() + leftInset;
        double insideY = insets.getTop();
        double insideWidth = width - insideX - insets.getRight() - rightInset;
        double insideHeight = height - insideY - insets.getBottom();
        double leftWidth = 0;
        double rightWidth = 0;
        Node center = getCenter();
        Node right = getRight();
        Node left = getLeft();

        if (left != null && left.isManaged()) {
            Insets leftMargin = getNodeMargin(left);
            double adjustedWidth = adjustWidthByMargin(insideWidth, leftMargin);
            double adjustedHeight = adjustHeightByMargin(insideHeight, leftMargin);
            leftWidth = snapSizeX(left.prefWidth(adjustedHeight));
            leftWidth = Math.min(leftWidth, adjustedWidth);
            Vec2d result = boundedNodeSizeWithBias(left, leftWidth, adjustedHeight, true, true, TEMP_VEC2D);
            leftWidth = snapSizeX(result.x);
            left.resize(leftWidth, snapSizeY(result.y));

            leftWidth = snapSpaceX(leftMargin.getLeft()) + leftWidth + snapSpaceX(leftMargin.getRight());
            Pos alignment = getAlignment(left);
            positionInArea(
                left, insideX, insideY,
                leftWidth, insideHeight, 0,
                leftMargin,
                alignment != null ? alignment.getHpos() : HPos.LEFT,
                alignment != null ? alignment.getVpos() : VPos.CENTER,
                isSnapToPixel());
        }

        if (right != null && right.isManaged()) {
            Insets rightMargin = getNodeMargin(right);
            double adjustedWidth = adjustWidthByMargin(insideWidth - leftWidth, rightMargin);
            double adjustedHeight = adjustHeightByMargin(insideHeight, rightMargin);

            rightWidth = snapSizeX(right.prefWidth(adjustedHeight));
            rightWidth = Math.min(rightWidth, adjustedWidth);
            Vec2d result = boundedNodeSizeWithBias(right, rightWidth, adjustedHeight, true, true, TEMP_VEC2D);
            rightWidth = snapSizeX(result.x);
            right.resize(rightWidth, snapSizeY(result.y));

            rightWidth = snapSpaceX(rightMargin.getLeft()) + rightWidth + snapSpaceX(rightMargin.getRight());
            Pos alignment = getAlignment(right);
            positionInArea(
                right, insideX + insideWidth - rightWidth, insideY,
                rightWidth, insideHeight, 0,
                rightMargin,
                alignment != null ? alignment.getHpos() : HPos.RIGHT,
                alignment != null ? alignment.getVpos() : VPos.CENTER,
                isSnapToPixel());
        }

        if (center != null && center.isManaged()) {
            Insets centerMargin = getNodeMargin(center);
            double adjustedWidth = adjustWidthByMargin(insideWidth - leftWidth - rightWidth, centerMargin);
            double adjustedHeight = adjustHeightByMargin(insideHeight, centerMargin);

            double centerWidth = snapSizeX(center.prefWidth(adjustedHeight));
            centerWidth = Math.min(centerWidth, adjustedWidth);
            Vec2d result = boundedNodeSizeWithBias(center, centerWidth, adjustedHeight, true, true, TEMP_VEC2D);
            centerWidth = snapSizeX(result.x);
            center.resize(centerWidth, snapSizeY(result.y));

            Pos alignment = getAlignment(center);
            if (alignment == null || alignment.getHpos() == HPos.CENTER) {
                double offsetX = (rightInset - leftInset) / 2 - (leftWidth - rightWidth) / 2;
                double idealX = insideX + leftWidth + offsetX + (adjustedWidth - centerWidth) / 2;
                double adjustedX;

                if (offsetX < 0) {
                    double excess = (insideX + leftWidth) - idealX;
                    adjustedX = excess > 0 ? idealX + excess : idealX;
                } else {
                    double excess = (idealX + centerWidth) - (insideX + insideWidth - rightWidth);
                    adjustedX = excess > 0 ? idealX - excess : idealX;
                }

                positionInArea(
                    center,
                    adjustedX, insideY,
                    centerWidth, insideHeight, 0,
                    centerMargin,
                    HPos.LEFT, alignment != null ? alignment.getVpos() : VPos.CENTER,
                    isSnapToPixel());
            } else {
                positionInArea(
                    center,
                    insideX + leftWidth, insideY,
                    insideWidth - leftWidth - rightWidth, insideHeight, 0,
                    centerMargin,
                    alignment.getHpos(), alignment.getVpos(),
                    isSnapToPixel());
            }
        }
    }

    private boolean childHasContentBias(Node child, Orientation orientation) {
        if (child != null && child.isManaged()) {
            return child.getContentBias() == orientation;
        }

        return false;
    }

    private double getAreaWidth(Node child, double height, boolean minimum) {
        if (child != null && child.isManaged()) {
            Insets margin = getNodeMargin(child);
            return minimum
                ? computeChildMinAreaWidth(child, -1, margin, height, false)
                : computeChildPrefAreaWidth(child, -1, margin, height, false);
        }

        return 0;
    }

    private double getAreaHeight(Node child, double width, boolean minimum) {
        if (child != null && child.isManaged()) {
            Insets margin = getNodeMargin(child);
            return minimum
                ? computeChildMinAreaHeight(child, -1, margin, width)
                : computeChildPrefAreaHeight(child, -1, margin, width);
        }

        return 0;
    }

    private class NodeProperty extends ObjectPropertyBase<Node> {
        private final String name;
        private Node value;

        NodeProperty(String name) {
            this.name = name;
        }

        @Override
        public Object getBean() {
            return HeaderBar.this;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        protected void invalidated() {
            if (value != null) {
                getChildren().remove(value);
            }

            value = get();

            if (value != null) {
                getChildren().add(value);
            }
        }
    }
}
