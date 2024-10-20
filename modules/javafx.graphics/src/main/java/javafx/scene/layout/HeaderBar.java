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
import javafx.geometry.NodeOrientation;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.stage.StageStyle;

/**
 * A client-area header bar that is used as a replacement for the system-provided header bar in stages
 * with the {@link StageStyle#EXTENDED} style. This class enables the <em>click-and-drag</em> and
 * <em>double-click to maximize</em> behaviors that are usually afforded by system-provided header bars.
 * The entire {@code HeaderBar} background is draggable by default, but its content is not. Applications
 * can specify draggable content nodes of the {@code HeaderBar} with the {@link #setDraggable} method.
 * <p>
 * {@code HeaderBar} is a layout container that allows applications to place scene graph nodes
 * in three areas: {@link #leadingProperty() leading}, {@link #centerProperty() center}, and
 * {@link #trailingProperty() trailing}. {@code HeaderBar} ensures that the leading and trailing areas
 * account for the default window buttons (minimize, maximize, close). If a child is configured to be
 * centered in the {@code center} area, it is laid out with respect to the stage, and not with respect
 * to the {@code center} area. This ensures that the child will appear centered in the stage regardless
 * of leading or trailing children or the platform-specific placement of default window buttons.
 * <p>
 * All children will be resized to their preferred widths and extend the height of the {@code HeaderBar}.
 * {@code HeaderBar} honors the minimum, preferred, and maximum sizes of its children. If the child's
 * resizable range prevents it from be resized to fit within its position, it will be vertically centered
 * relative to the available space; this alignment can be customized with a layout constraint.
 *
 * <h2>Layout constraints</h2>
 * An application may set constraints on individual children to customize their layout.
 * For each constraint, {@code HeaderBar} provides static getter and setter methods.
 * <table style="white-space: nowrap">
 *     <caption>Layout constraints of {@code HeaderBar}</caption>
 *     <thead>
 *         <tr><th>Constraint</th><th>Type</th><th>Description</th></tr>
 *     </thead>
 *     <tbody>
 *         <tr><th>alignment</th><td>{@link Pos}</td>
 *             <td>The alignment of the child within its area of the {@code HeaderBar}.</td>
 *         </tr>
 *         <tr><th>margin</th>
 *             <td>{@link Insets}</td><td>Margin space around the outside of the child.</td>
 *         </tr>
 *     </tbody>
 * </table>
 *
 * <h2>Example</h2>
 * <pre>{@code
 *     var button = new Button("My button");
 *     HeaderBar.setAlignment(button, Pos.CENTER_LEFT);
 *     HeaderBar.setMargin(button, new Insets(5));
 *     myHeaderBar.setCenter(button);
 * }</pre>
 *
 * @see HeaderBarBase
 * @since 24
 */
public class HeaderBar extends HeaderBarBase {

    private static final String ALIGNMENT = "headerbar-alignment";
    private static final String MARGIN = "headerbar-margin";

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

    /**
     * Creates a new {@code HeaderBar}.
     */
    public HeaderBar() {
    }

    /**
     * Creates a new {@code HeaderBar} with the specified children.
     *
     * @param leading the leading node
     * @param center the center node
     * @param trailing the trailing node
     */
    public HeaderBar(Node leading, Node center, Node trailing) {
        setLeading(leading);
        setCenter(center);
        setTrailing(trailing);
    }

    /**
     * The leading area of the {@code HeaderBar}.
     * <p>
     * Usually, this corresponds to the left side of the header bar; with right-to-left orientation,
     * this corresponds to the right side of the header bar.
     */
    private final ObjectProperty<Node> leading = new NodeProperty() {
        @Override
        public String getName() {
            return "leading";
        }
    };

    public final ObjectProperty<Node> leadingProperty() {
        return leading;
    }

    public final Node getLeading() {
        return leading.get();
    }

    public final void setLeading(Node value) {
        leading.set(value);
    }

    /**
     * The center area of the {@code HeaderBar}.
     */
    private final ObjectProperty<Node> center = new NodeProperty() {
        @Override
        public String getName() {
            return "center";
        }
    };

    public final ObjectProperty<Node> centerProperty() {
        return center;
    }

    public final Node getCenter() {
        return center.get();
    }

    public final void setCenter(Node value) {
        center.set(value);
    }

    /**
     * The trailing area of the {@code HeaderBar}.
     * <p>
     * Usually, this corresponds to the right side of the header bar; with right-to-left orientation,
     * this corresponds to the left side of the header bar.
     */
    private final ObjectProperty<Node> trailing = new NodeProperty() {
        @Override
        public String getName() {
            return "trailing";
        }
    };

    public final ObjectProperty<Node> trailingProperty() {
        return trailing;
    }

    public final Node getTrailing() {
        return trailing.get();
    }

    public final void setTrailing(Node value) {
        trailing.set(value);
    }

    @Override
    protected double computeMinWidth(double height) {
        Node leading = getLeading(), center = getCenter(), trailing = getTrailing();
        Insets insets = getInsets();
        double leftPrefWidth;
        double rightPrefWidth;
        double centerMinWidth;

        if (height != -1
                && (childHasContentBias(leading, Orientation.VERTICAL) ||
                    childHasContentBias(trailing, Orientation.VERTICAL) ||
                    childHasContentBias(center, Orientation.VERTICAL))) {
            double areaHeight = Math.max(0, height);
            leftPrefWidth = getAreaWidth(leading, areaHeight, false);
            rightPrefWidth = getAreaWidth(trailing, areaHeight, false);
            centerMinWidth = getAreaWidth(center, areaHeight, true);
        } else {
            leftPrefWidth = getAreaWidth(leading, -1, false);
            rightPrefWidth = getAreaWidth(trailing, -1, false);
            centerMinWidth = getAreaWidth(center, -1, true);
        }

        return insets.getLeft()
             + leftPrefWidth
             + centerMinWidth
             + rightPrefWidth
             + insets.getRight()
             + getLeftSystemInset().getWidth()
             + getRightSystemInset().getWidth();
    }

    @Override
    protected double computeMinHeight(double width) {
        Node leading = getLeading(), center = getCenter(), trailing = getTrailing();
        Insets insets = getInsets();
        double leadingMinHeight = getAreaHeight(leading, -1, true);
        double trailingMinHeight = getAreaHeight(trailing, -1, true);
        double centerMinHeight;

        if (width != -1 && childHasContentBias(center, Orientation.HORIZONTAL)) {
            double leadingPrefWidth = getAreaWidth(leading, -1, false);
            double trailingPrefWidth = getAreaWidth(trailing, -1, false);
            centerMinHeight = getAreaHeight(center, Math.max(0, width - leadingPrefWidth - trailingPrefWidth), true);
        } else {
            centerMinHeight = getAreaHeight(center, -1, true);
        }

        return insets.getTop()
             + insets.getBottom()
             + Math.max(centerMinHeight, Math.max(trailingMinHeight, leadingMinHeight));
    }

    @Override
    protected double computePrefHeight(double width) {
        Node leading = getLeading(), center = getCenter(), trailing = getTrailing();
        Insets insets = getInsets();
        double leadingPrefHeight = getAreaHeight(leading, -1, false);
        double trailingPrefHeight = getAreaHeight(trailing, -1, false);
        double centerPrefHeight;

        if (width != -1 && childHasContentBias(center, Orientation.HORIZONTAL)) {
            double leadingPrefWidth = getAreaWidth(leading, -1, false);
            double trailingPrefWidth = getAreaWidth(trailing, -1, false);
            centerPrefHeight = getAreaHeight(center, Math.max(0, width - leadingPrefWidth - trailingPrefWidth), false);
        } else {
            centerPrefHeight = getAreaHeight(center, -1, false);
        }

        return insets.getTop()
             + insets.getBottom()
             + Math.max(centerPrefHeight, Math.max(trailingPrefHeight, leadingPrefHeight));
    }

    @Override
    public boolean usesMirroring() {
        return false;
    }

    @Override
    protected void layoutChildren() {
        Node center = getCenter();
        Node left, right;
        Insets insets = getInsets();
        boolean rtl = getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT;
        double width = Math.max(getWidth(), minWidth(-1));
        double height = Math.max(getHeight(), minHeight(-1));
        double leftSystemInset = getLeftSystemInset().getWidth();
        double rightSystemInset = getRightSystemInset().getWidth();
        double leftWidth = 0;
        double rightWidth = 0;
        double insideY = insets.getTop();
        double insideHeight = height - insideY - insets.getBottom();
        double insideX, insideWidth;

        if (rtl) {
            left = getTrailing();
            right = getLeading();
            insideX = insets.getRight() + leftSystemInset;
            insideWidth = width - insideX - insets.getLeft() - rightSystemInset;
        } else {
            left = getLeading();
            right = getTrailing();
            insideX = insets.getLeft() + leftSystemInset;
            insideWidth = width - insideX - insets.getRight() - rightSystemInset;
        }

        if (left != null && left.isManaged()) {
            Insets leftMargin = adjustMarginForRTL(getNodeMargin(left), rtl);
            double adjustedWidth = adjustWidthByMargin(insideWidth, leftMargin);
            Vec2d childSize = resizeChild(left, adjustedWidth, insideHeight, leftMargin);
            leftWidth = snapSpaceX(leftMargin.getLeft()) + childSize.x + snapSpaceX(leftMargin.getRight());
            Pos alignment = getAlignment(left);

            positionInArea(
                left, insideX, insideY,
                leftWidth, insideHeight, 0,
                leftMargin,
                alignment != null ? alignment.getHpos() : HPos.CENTER,
                alignment != null ? alignment.getVpos() : VPos.CENTER,
                isSnapToPixel());
        }

        if (right != null && right.isManaged()) {
            Insets rightMargin = adjustMarginForRTL(getNodeMargin(right), rtl);
            double adjustedWidth = adjustWidthByMargin(insideWidth - leftWidth, rightMargin);
            Vec2d childSize = resizeChild(right, adjustedWidth, insideHeight, rightMargin);
            rightWidth = snapSpaceX(rightMargin.getLeft()) + childSize.x + snapSpaceX(rightMargin.getRight());
            Pos alignment = getAlignment(right);

            positionInArea(
                right, insideX + insideWidth - rightWidth, insideY,
                rightWidth, insideHeight, 0,
                rightMargin,
                alignment != null ? alignment.getHpos() : HPos.CENTER,
                alignment != null ? alignment.getVpos() : VPos.CENTER,
                isSnapToPixel());
        }

        if (center != null && center.isManaged()) {
            Insets centerMargin = adjustMarginForRTL(getNodeMargin(center), rtl);
            double adjustedWidth = adjustWidthByMargin(insideWidth - leftWidth - rightWidth, centerMargin);
            Vec2d childSize = resizeChild(center, adjustedWidth, insideHeight, centerMargin);
            double centerWidth = childSize.x;
            Pos alignment = getAlignment(center);

            if (alignment == null || alignment.getHpos() == HPos.CENTER) {
                double idealX = width / 2 - centerWidth / 2;
                double minX = insideX + leftWidth + centerMargin.getLeft();
                double maxX = insideX + insideWidth - rightWidth - centerMargin.getRight();
                double adjustedX;

                if (idealX < minX) {
                    adjustedX = minX;
                } else if (idealX + centerWidth > maxX) {
                    adjustedX = maxX - centerWidth;
                } else {
                    adjustedX = idealX;
                }

                positionInArea(
                    center,
                    adjustedX, insideY,
                    centerWidth, insideHeight, 0,
                    new Insets(centerMargin.getTop(), 0, centerMargin.getBottom(), 0),
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

    private Insets adjustMarginForRTL(Insets margin, boolean rtl) {
        if (margin == null) {
            return null;
        }

        return rtl
            ? new Insets(margin.getTop(), margin.getLeft(), margin.getBottom(), margin.getRight())
            : margin;
    }

    private boolean childHasContentBias(Node child, Orientation orientation) {
        if (child != null && child.isManaged()) {
            return child.getContentBias() == orientation;
        }

        return false;
    }

    private Vec2d resizeChild(Node child, double adjustedWidth, double insideHeight, Insets margin) {
        double adjustedHeight = adjustHeightByMargin(insideHeight, margin);
        double childWidth = Math.min(snapSizeX(child.prefWidth(adjustedHeight)), adjustedWidth);
        Vec2d size = boundedNodeSizeWithBias(child, childWidth, adjustedHeight, false, true, TEMP_VEC2D);
        size.x = snapSizeX(size.x);
        size.y = snapSizeX(size.y);
        child.resize(size.x, size.y);
        return size;
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

    private abstract class NodeProperty extends ObjectPropertyBase<Node> {
        private Node value;

        @Override
        public Object getBean() {
            return HeaderBar.this;
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
