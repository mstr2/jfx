/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package test.com.sun.javafx.sg.prism;

import com.sun.javafx.geom.BackdropRegionContainer;
import com.sun.javafx.geom.BackdropRegionPool;
import com.sun.javafx.geom.DirtyRegionContainer;
import com.sun.javafx.geom.DirtyRegionPool;
import com.sun.javafx.geom.RectBounds;
import com.sun.javafx.geom.transform.BaseTransform;
import com.sun.javafx.geom.transform.GeneralTransform3D;
import com.sun.javafx.sg.prism.NGNode;
import com.sun.scenario.effect.GaussianBlur;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BackdropDirtyRegionTest extends NGTestBase {

    /**
     * Tests that a node with a backdrop effect is counted as a dirty region if the backdrop
     * that contributes to the node's backdrop effect is dirty.
     */
    @Test
    public void nodeIsDirty_whenBackdropIsDirty() {
        NGNode node1 = createRectangle(0, 0, 50, 50);
        NGNode node2 = createRectangle(0, 0, 50, 50);
        NGNode root = createGroup(node1, node2);

        // The node has a backdrop blur effect that expands dirty regions by its radius.
        node2.setBackdropEffect(new GaussianBlur(8));

        // Move node2 to the side so it doesn't overlap with node1.
        translate(node2, 60, 0);
        root.clearDirty();

        // Next, we translate node1 by (5, 5). This does two things:
        // 1. It marks the area (0,0)-(55,55) as dirty (union of old and new areas covered by node1).
        // 2. Since the backdrop-effect area of node2 now extends into the dirty area of node1, the entire
        //    area of node2 will also be marked dirty. Note that the content of node1 and node2 do NOT overlap
        //    even after the translation; they only overlap in the extended backdrop-effect area.
        //    The final dirty area is therefore (0,0)-(110,55).
        translate(node1, 5, 5);
        DirtyRegionContainer drc = accumulateDirtyRegions(root);
        assertEquals(new RectBounds(0, 0, 110, 55), drc.getDirtyRegion(0));
    }

    /**
     * Tests that a node is not considered dirty if its backdrop-effect area does not overlap
     * with a dirty region of its backdrop.
     */
    @Test
    public void nodeIsNotDirty_whenBackdropEffectBoundsDoNotOverlapWithDirtyBackdrop() {
        NGNode node1 = createRectangle(0, 0, 50, 50);
        NGNode node2 = createRectangle(0, 0, 50, 50);
        NGNode root = createGroup(node1, node2);

        // The node has a backdrop blur effect that expands dirty regions by its radius.
        node2.setBackdropEffect(new GaussianBlur(8));

        // Move node2 to the side so it doesn't overlap with node1.
        translate(node2, 60, 0);
        root.clearDirty();

        // Next, we translate node1 by (0, 5). This does two things:
        // 1. It marks the area (0,0)-(50,55) as dirty (union of old and new areas covered by node1).
        // 2. Since the backdrop-aware area of node2 does NOT extend into the dirty area of node1, the
        //    area of node2 will NOT be marked dirty. The final dirty area is therefore (0,0)-(50,55).
        translate(node1, 0, 5);
        DirtyRegionContainer drc = accumulateDirtyRegions(root);
        assertEquals(new RectBounds(0, 0, 50, 55), drc.getDirtyRegion(0));
    }

    private DirtyRegionContainer accumulateDirtyRegions(NGNode node) {
        DirtyRegionPool drPool = new DirtyRegionPool(1);
        DirtyRegionContainer drc = drPool.checkOut();
        BackdropRegionPool brPool = new BackdropRegionPool();
        BackdropRegionContainer brc = brPool.checkOut();
        RectBounds dirtyTemp = new RectBounds();
        GeneralTransform3D pvTx = new GeneralTransform3D();

        node.accumulateDirtyRegions(
            new RectBounds(-10000, -10000, 10000, 10000),
            dirtyTemp,
            drPool, drc,
            brPool, brc,
            BaseTransform.IDENTITY_TRANSFORM,
            pvTx);

        return drc;
    }
}
