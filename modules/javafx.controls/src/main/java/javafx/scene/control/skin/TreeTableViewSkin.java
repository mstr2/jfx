/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

package javafx.scene.control.skin;

import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.ObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.NodeOrientation;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTablePosition;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import com.sun.javafx.scene.control.ListenerHelper;
import com.sun.javafx.scene.control.TreeTableViewBackingList;
import com.sun.javafx.scene.control.behavior.TreeTableViewBehavior;

/**
 * Default skin implementation for the {@link TreeTableView} control.
 *
 * @param <T> the tree table item type
 * @see TreeTableView
 * @since 9
 */
public class TreeTableViewSkin<T> extends TableViewSkinBase<T, TreeItem<T>, TreeTableView<T>, TreeTableRow<T>, TreeTableColumn<T,?>> {

    /* *************************************************************************
     *                                                                         *
     * Private Fields                                                          *
     *                                                                         *
     **************************************************************************/

    TreeTableViewBackingList<T> tableBackingList;
    ObjectProperty<ObservableList<TreeItem<T>>> tableBackingListProperty;
    private final TreeTableViewBehavior<T>  behavior;
    private final EventHandler<TreeItem.TreeModificationEvent<T>> rootListener;



    /* *************************************************************************
     *                                                                         *
     * Constructors                                                            *
     *                                                                         *
     **************************************************************************/

    /**
     * Creates a new TreeTableViewSkin instance, installing the necessary child
     * nodes into the Control {@link Control#getChildren() children} list, as
     * well as the necessary input mappings for handling key, mouse, etc events.
     *
     * @param control The control that this skin should be installed onto.
     */
    public TreeTableViewSkin(final TreeTableView<T> control) {
        super(control);

        // install default input map for the TreeTableView control
        behavior = new TreeTableViewBehavior<>(control);

        flow.setFixedCellSize(control.getFixedCellSize());
        flow.setCellFactory(flow -> createCell());

        ListenerHelper lh = ListenerHelper.get(this);

        EventHandler<MouseEvent> ml = event -> {
            // This ensures that the table maintains the focus, even when the vbar
            // and hbar controls inside the flow are clicked. Without this, the
            // focus border will not be shown when the user interacts with the
            // scrollbars, and more importantly, keyboard navigation won't be
            // available to the user.
            if (control.isFocusTraversable()) {
                control.requestFocus();
            }
        };
        lh.addEventFilter(flow.getVbar(), MouseEvent.MOUSE_PRESSED, ml);
        lh.addEventFilter(flow.getHbar(), MouseEvent.MOUSE_PRESSED, ml);

        // init the behavior 'closures'
        behavior.setOnFocusPreviousRow(() -> onFocusAboveCell());
        behavior.setOnFocusNextRow(() -> onFocusBelowCell());
        behavior.setOnMoveToFirstCell(() -> onMoveToFirstCell());
        behavior.setOnMoveToLastCell(() -> onMoveToLastCell());
        behavior.setOnScrollPageDown(isFocusDriven -> onScrollPageDown(isFocusDriven));
        behavior.setOnScrollPageUp(isFocusDriven -> onScrollPageUp(isFocusDriven));
        behavior.setOnSelectPreviousRow(() -> onSelectAboveCell());
        behavior.setOnSelectNextRow(() -> onSelectBelowCell());
        behavior.setOnSelectLeftCell(() -> onSelectLeftCell());
        behavior.setOnSelectRightCell(() -> onSelectRightCell());
        behavior.setOnFocusLeftCell(() -> onFocusLeftCell());
        behavior.setOnFocusRightCell(() -> onFocusRightCell());
        behavior.setOnHorizontalUnitScroll(this::horizontalUnitScroll);
        behavior.setOnVerticalUnitScroll(this::verticalUnitScroll);

        rootListener = (ch) -> {
            if (ch.wasAdded() && ch.wasRemoved() && ch.getAddedSize() == ch.getRemovedSize()) {
                // Fix for JDK-8114432, where the children of a TreeItem were changing,
                // but because the overall item count was staying the same, there was
                // no event being fired to the skin to be informed that the items
                // had changed. So, here we just watch for the case where the number
                // of items being added is equal to the number of items being removed.
                markItemCountDirty();
                control.requestLayout();
            } else if (ch.getEventType().equals(TreeItem.valueChangedEvent())) {
                // Fix for JDK-8114657 and JDK-8114610.
                requestRebuildCells();
            } else {
                // Fix for JDK-8115929. We are checking to see if the event coming
                // from the TreeItem root is an event where the count has changed.
                EventType<?> eventType = ch.getEventType();
                while (eventType != null) {
                    if (eventType.equals(TreeItem.<T>expandedItemCountChangeEvent())) {
                        markItemCountDirty();
                        control.requestLayout();
                        break;
                    }
                    eventType = eventType.getSuperType();
                }
            }

            // fix for JDK-8094887
            control.edit(-1, null);
        };

        lh.addChangeListener(control.rootProperty(), true, (src, prev, root) -> {
            if (prev != null) {
                prev.removeEventHandler(TreeItem.<T>treeNotificationEvent(), rootListener);
            }
            if (root != null) {
                root.addEventHandler(TreeItem.<T>treeNotificationEvent(), rootListener);
            }
            // fix for JDK-8094887
            control.edit(-1, null);

            if (root == null || root.getValue() == null) {
                requestRebuildCells();
            }

            updateItemCount();
        });

        lh.addChangeListener(control.showRootProperty(), (ev) -> {
            // if we turn off showing the root, then we must ensure the root
            // is expanded - otherwise we end up with no visible items in
            // the tree.
            if (!control.isShowRoot()) {
                TreeItem<T> root = control.getRoot();
                if (root != null) {
                    root.setExpanded(true);
                }
            }
            // update the item count in the flow and behavior instances
            updateItemCount();
        });

        lh.addChangeListener(control.expandedItemCountProperty(), (ev) -> {
            markItemCountDirty();
        });

        lh.addChangeListener(control.fixedCellSizeProperty(), (ev) -> {
            flow.setFixedCellSize(getSkinnable().getFixedCellSize());
        });

        updateItemCount();
    }


    /* *************************************************************************
     *                                                                         *
     * Public API                                                              *
     *                                                                         *
     **************************************************************************/

    /** {@inheritDoc} */
    @Override
    public void dispose() {
        flow.setCellFactory(null);

        if (behavior != null) {
            behavior.dispose();
        }

        super.dispose();
    }

    /** {@inheritDoc} */
    @Override protected Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        switch (attribute) {
            case ROW_AT_INDEX: {
                final int rowIndex = (Integer)parameters[0];
                return rowIndex < 0 ? null : flow.getPrivateCell(rowIndex);
            }
            case SELECTED_ITEMS: {
                List<Node> selection = new ArrayList<>();
                TreeTableView.TreeTableViewSelectionModel<T> sm = getSkinnable().getSelectionModel();
                if (sm != null) {
                    for (TreeTablePosition<T,?> pos : sm.getSelectedCells()) {
                        TreeTableRow<T> row = flow.getPrivateCell(pos.getRow());
                        if (row != null) selection.add(row);
                    }
                }
                return FXCollections.observableArrayList(selection);
            }
            case FOCUS_ITEM: // TableViewSkinBase
            case CELL_AT_ROW_COLUMN: // TableViewSkinBase
            case COLUMN_AT_INDEX: // TableViewSkinBase
            case HEADER: // TableViewSkinBase
            case VERTICAL_SCROLLBAR: // TableViewSkinBase
            case HORIZONTAL_SCROLLBAR: // TableViewSkinBase
            default: return super.queryAccessibleAttribute(attribute, parameters);
        }
    }

    @Override
    protected void executeAccessibleAction(AccessibleAction action, Object... parameters) {
        switch (action) {
            case SHOW_ITEM: {
                Node item = (Node)parameters[0];
                if (item instanceof TreeTableCell) {
                    @SuppressWarnings("unchecked")
                    TreeTableCell<T, ?> cell = (TreeTableCell<T, ?>)item;
                    flow.scrollTo(cell.getIndex());
                }
                break;
            }
            case SET_SELECTED_ITEMS: {
                @SuppressWarnings("unchecked")
                ObservableList<Node> items = (ObservableList<Node>)parameters[0];
                if (items != null) {
                    TreeTableView.TreeTableViewSelectionModel<T> sm = getSkinnable().getSelectionModel();
                    if (sm != null) {
                        sm.clearSelection();
                        for (Node item : items) {
                            if (item instanceof TreeTableCell) {
                                @SuppressWarnings("unchecked")
                                TreeTableCell<T, ?> cell = (TreeTableCell<T, ?>)item;
                                sm.select(cell.getIndex(), cell.getTableColumn());
                            }
                        }
                    }
                }
                break;
            }
            default: super.executeAccessibleAction(action, parameters);
        }
    }



    /* *************************************************************************
     *                                                                         *
     * Private methods                                                         *
     *                                                                         *
     **************************************************************************/

    /** {@inheritDoc} */
    private TreeTableRow<T> createCell() {
        TreeTableRow<T> cell;

        TreeTableView<T> treeTableView = getSkinnable();
        if (treeTableView.getRowFactory() != null) {
            cell = treeTableView.getRowFactory().call(treeTableView);
        } else {
            cell = new TreeTableRow<>();
        }

        // If there is no disclosure node, then add one of my own
        if (cell.getDisclosureNode() == null) {
            final StackPane disclosureNode = new StackPane();
            disclosureNode.getStyleClass().setAll("tree-disclosure-node");
            disclosureNode.setMouseTransparent(true);

            final StackPane disclosureNodeArrow = new StackPane();
            disclosureNodeArrow.getStyleClass().setAll("arrow");
            disclosureNode.getChildren().add(disclosureNodeArrow);

            cell.setDisclosureNode(disclosureNode);
        }

        cell.updateTreeTableView(treeTableView);
        return cell;
    }

    /** {@inheritDoc} */
    @Override protected int getItemCount() {
        return getSkinnable().getExpandedItemCount();
    }

    /** {@inheritDoc} */
    @Override void horizontalScroll() {
        super.horizontalScroll();
        if (getSkinnable().getFixedCellSize() > 0) {
            flow.requestCellLayout();
        }
    }

    /** {@inheritDoc} */
    @Override protected void updateItemCount() {
        updatePlaceholderRegionVisibility();

        tableBackingList.resetSize();

        int oldCount = flow.getCellCount();
        int newCount = getItemCount();

        // if this is not called even when the count is the same, we get a
        // memory leak in VirtualFlow.sheet.children. This can probably be
        // optimised in the future when time permits.
        flow.setCellCount(newCount);

        if (newCount != oldCount) {
            // The following line is (perhaps temporarily) disabled to
            // resolve two issues: JDK-8155798 and JDK-8147483.
            // A unit test exists in TreeTableViewTest to ensure that
            // the performance issue covered in JDK-8147483 doesn't regress.
            // requestRebuildCells();
        } else {
            needCellsReconfigured = true;
        }
    }

    private void horizontalUnitScroll(boolean right) {
        if (getSkinnable().getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT) {
            right = !right;
        }
        ScrollBar sb = flow.getHbar();
        if (right) {
            sb.increment();
        } else {
            sb.decrement();
        }
    }

    private void verticalUnitScroll(boolean down) {
        ScrollBar sb = flow.getVbar();
        if (down) {
            sb.increment();
        } else {
            sb.decrement();
        }
    }
}
