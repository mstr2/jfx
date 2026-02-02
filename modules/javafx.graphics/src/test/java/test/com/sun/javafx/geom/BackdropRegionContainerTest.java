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

package test.com.sun.javafx.geom;

import com.sun.javafx.geom.RectBounds;
import com.sun.javafx.geom.BackdropRegionContainer;
import com.sun.javafx.geom.transform.Affine2D;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BackdropRegionContainerTest {

    private BackdropRegionContainer container;

    @BeforeEach
    void setup() {
        container = new BackdropRegionContainer();
    }

    @Test
    public void computeRepaintRegionRejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> container.computeRepaintRegion(null, new RectBounds()));
        assertThrows(NullPointerException.class, () -> container.computeRepaintRegion(new RectBounds(), null));
    }

    @Test
    public void testComputeRepaintRegionEmptyRegionIsNoOp() {
        container.add(new RectBounds(0, 0, 10, 10), new RectBounds(-1, -1, 11, 11), false);

        var empty = new RectBounds();
        assertTrue(empty.isEmpty());

        var exp = new RectBounds();
        container.computeRepaintRegion(empty, exp);
        assertEquals(empty, exp);
    }

    @Test
    public void transformIsNoOpWhenStartIndexAtOrPastEntryCount() {
        container.add(new RectBounds(0, 0, 10, 10), new RectBounds(-1, -1, 11, 11), false);

        var tx = new Affine2D();
        tx.translate(100, 0);

        // startIndex == entryCount: no-op
        container.transform(tx, container.entryCount());
        var exp = new RectBounds();
        container.computeRepaintRegion(new RectBounds(0, 0, 10, 10), exp);
        assertEquals(new RectBounds(-1, -1, 11, 11), exp);

        // startIndex > entryCount: no-op
        container.reset();
        container.add(new RectBounds(0, 0, 10, 10), new RectBounds(-1, -1, 11, 11), false);
        container.transform(tx, container.entryCount() + 10);
        var exp2 = new RectBounds();
        container.computeRepaintRegion(new RectBounds(0, 0, 10, 10), exp2);
        assertEquals(new RectBounds(-1, -1, 11, 11), exp2);
    }

    @Test
    public void resetClearsEntryCountAndActiveFlags() {
        container.add(new RectBounds(0, 0, 10, 10), new RectBounds(-1, -1, 11, 11), false);
        container.add(new RectBounds(20, 0, 30, 10), new RectBounds(19, -1, 31, 11), true);
        assertEquals(2, container.entryCount());

        var region = new RectBounds(0, 0, 10, 10);
        var exp = new RectBounds();
        container.computeRepaintRegion(region, exp);
        container.reset();
        assertEquals(0, container.entryCount());

        // After reset, queries are no-ops
        var exp2 = new RectBounds();
        container.computeRepaintRegion(region, exp2);
        assertEquals(region, exp2);

        // After reset, we can add again and queries work
        container.add(new RectBounds(0, 0, 10, 10), new RectBounds(-1, -1, 11, 11), false);
        var exp3 = new RectBounds();
        container.computeRepaintRegion(region, exp3);
        assertEquals(new RectBounds(-1, -1, 11, 11), exp3);
    }

    @Test
    public void computeNoOverlapDoesNotChangeExpansionOrConsume() {
        container.add(new RectBounds(0, 0, 10, 10), new RectBounds(-1, -1, 11, 11), false);

        var region = new RectBounds(100, 100, 110, 110);
        var regionCopy = new RectBounds(region);
        var exp = new RectBounds();

        container.computeRepaintRegion(region, exp);
        assertEquals(region, exp);
        assertEquals(regionCopy, region); // region argument is not mutated

        // Entry should not be consumed by a disjoint query; a later overlapping query must still expand.
        var exp2 = new RectBounds();
        container.computeRepaintRegion(new RectBounds(0, 0, 10, 10), exp2);
        assertEquals(new RectBounds(-1, -1, 11, 11), exp2);
    }

    @Test
    public void nonLocalHitExpandsToFullInputAndIsConsumed() {
        container.add(new RectBounds(0, 0, 10, 10), new RectBounds(-5, -5, 15, 15), false /* non-local */);

        var exp = new RectBounds();
        container.computeRepaintRegion(new RectBounds(0, 0, 10, 10), exp);
        assertEquals(new RectBounds(-5, -5, 15, 15), exp);

        // Query again: entry must be consumed, so no expansion should occur.
        var exp2 = new RectBounds();
        container.computeRepaintRegion(new RectBounds(0, 0, 10, 10), exp2);
        assertEquals(new RectBounds(0, 0, 10, 10), exp2);
    }

    @Test
    public void localPartialHitDoesNotConsume() {
        // Output: 0..100, Input inflated by 10.
        container.add(new RectBounds(0, 0, 100, 100), new RectBounds(-10, -10, 110, 110), true /* local */);

        // Query overlaps only right strip [90..100]; for local, required input is overlap expanded by 10:
        // X: [80..110], Y: [-10..110]
        var exp = new RectBounds();
        container.computeRepaintRegion(new RectBounds(90, 0, 100, 100), exp);
        assertEquals(new RectBounds(80, -10, 110, 110), exp);

        // Because this was only a partial overlap, the entry must remain active.
        // A later query on the left side should still expand.
        var exp2 = new RectBounds();
        container.computeRepaintRegion(new RectBounds(0, 0, 10, 100), exp2);
        assertEquals(new RectBounds(-10, -10, 20, 110), exp2);
    }

    @Test
    public void localConsumedWhenRepaintRegionFullyCoversOutput() {
        container.add(new RectBounds(0, 0, 100, 100), new RectBounds(-10, -10, 110, 110), true /* local */);

        // Region fully covers output -> local entry can be consumed.
        var exp = new RectBounds();
        container.computeRepaintRegion(new RectBounds(0, 0, 100, 100), exp);
        assertEquals(new RectBounds(-10, -10, 110, 110), exp);

        // Query again: must not expand because entry was consumed.
        var exp2 = new RectBounds();
        container.computeRepaintRegion(new RectBounds(0, 0, 100, 100), exp2);
        assertEquals(new RectBounds(0, 0, 100, 100), exp2);
    }

    @Test
    public void backwardDependencyExpansionNonLocalChain() {
        // Render order: 0, 1, 2
        // Entry 2 depends on pixels produced by 1, entry 1 depends on 0, all are non-local.
        container.add(new RectBounds(0, 0, 10, 10), new RectBounds(0, 0, 10, 10), false);   // e0
        container.add(new RectBounds(20, 0, 30, 10), new RectBounds(0, 0, 30, 10), false);  // e1
        container.add(new RectBounds(40, 0, 50, 10), new RectBounds(20, 0, 50, 10), false); // e2

        var exp = new RectBounds();
        container.computeRepaintRegion(new RectBounds(40, 0, 50, 10), exp);
        assertEquals(new RectBounds(0, 0, 50, 10), exp);

        // All were consumed (non-local). Any future query should not expand.
        var exp2 = new RectBounds();
        container.computeRepaintRegion(new RectBounds(40, 0, 50, 10), exp2);
        assertEquals(new RectBounds(40, 0, 50, 10), exp2);
    }

    @Test
    public void localChainStopsWhenQueryIsInterior() {
        // Three adjacent blur-like local entries: output widths 100, inflated input by 10 each side.
        container.add(new RectBounds(0, 0, 100, 100), new RectBounds(-10, -10, 110, 110), true);
        container.add(new RectBounds(100, 0, 200, 100), new RectBounds(90, -10, 210, 110), true);
        container.add(new RectBounds(200, 0, 300, 100), new RectBounds(190, -10, 310, 110), true);

        // Query interior of the last output far from edges so required input does NOT touch prior outputs.
        var exp = new RectBounds();
        container.computeRepaintRegion(new RectBounds(260, 0, 280, 100), exp);
        assertEquals(new RectBounds(250, -10, 290, 110), exp);
    }

    @Test
    public void localChainPropagatesWhenQueryTouchesEdge() {
        container.add(new RectBounds(0, 0, 100, 100), new RectBounds(-10, -10, 110, 110), true);
        container.add(new RectBounds(100, 0, 200, 100), new RectBounds(90, -10, 210, 110), true);
        container.add(new RectBounds(200, 0, 300, 100), new RectBounds(190, -10, 310, 110), true);

        // Query near the left edge of the last output so required input overlaps
        // the second output, but not the first.
        var exp = new RectBounds();
        container.computeRepaintRegion(new RectBounds(200, 0, 205, 100), exp);

        // Last entry requires input [190..215] (x) and [-10..110] (y), which overlaps entry1 output [100..200].
        // Entry1 overlap sub-rect is [190..200], expanded by 10 -> required input [180..210].
        // Union gives x:[180..215], y:[-10..110].
        assertEquals(new RectBounds(180, -10, 215, 110), exp);
    }

    @Test
    public void transformAppliesFromStartIndexOnly() {
        container.add(new RectBounds(0, 0, 10, 10), new RectBounds(-1, -1, 11, 11), false);
        container.add(new RectBounds(20, 0, 30, 10), new RectBounds(19, -1, 31, 11), false);

        // Translate only the second entry by (+100, +0).
        var tx = new Affine2D();
        tx.translate(100, 0);
        container.transform(tx, 1);

        // First entry remains at original place.
        var exp1 = new RectBounds();
        container.computeRepaintRegion(new RectBounds(0, 0, 10, 10), exp1);
        assertEquals(new RectBounds(-1, -1, 11, 11), exp1);

        // Second entry should now be at x 120..130 with input 119..131.
        var exp2 = new RectBounds();
        container.computeRepaintRegion(new RectBounds(120, 0, 130, 10), exp2);
        assertEquals(new RectBounds(119, -1, 131, 11), exp2);

        // Querying at old position should not hit second entry.
        var exp3 = new RectBounds();
        container.computeRepaintRegion(new RectBounds(20, 0, 30, 10), exp3);
        assertEquals(new RectBounds(20, 0, 30, 10), exp3);
    }

    @Test
    public void bitsetAcrossMultipleWordsRemainsFunctionalAfterConsumption() {
        // Add > 128 entries to cover multiple long[] words.
        // Each entry is disjoint.
        for (int i = 0; i < 130; i++) {
            float x0 = i * 20;
            container.add(
                new RectBounds(x0, 0, x0 + 10, 10),
                new RectBounds(x0 - 1, -1, x0 + 11, 11),
                false /* non-local */);
        }

        assertEquals(130, container.entryCount());

        // Consume the last entry (index 129).
        var exp = new RectBounds();
        container.computeRepaintRegion(new RectBounds(129 * 20.0f, 0, 129 * 20.0f + 10, 10), exp);

        // Now query an earlier entry; if lastActiveIndex / findLastActive are broken, this may fail.
        var exp2 = new RectBounds();
        container.computeRepaintRegion(new RectBounds(100 * 20.0f, 0, 100 * 20.0f + 10, 10), exp2);
        assertEquals(new RectBounds(100 * 20.0f - 1, -1, 100 * 20.0f + 11, 11), exp2);
    }

    @Test
    public void mergeIntegratesRemovedAndAddedEntries() {
        container.add(new RectBounds(0, 0, 10, 10), new RectBounds(-5, -5, 15, 15), false); // idx 0 (will be consumed)
        container.add(new RectBounds(20, 0, 30, 10), new RectBounds(19, -1, 31, 11), true); // idx 1 (kept active)

        int baseIndex = container.entryCount();

        // Local copy of the container
        var local = new BackdropRegionContainer();
        local.merge(container, 0);

        // Consume entry 0 inside the local container.
        var expLocal = new RectBounds();
        local.computeRepaintRegion(new RectBounds(0, 0, 10, 10), expLocal);
        assertEquals(new RectBounds(-5, -5, 15, 15), expLocal);

        // Add a new entry in the local container (so merge() will perform remove + append).
        local.add(new RectBounds(100, 0, 110, 10), new RectBounds(99, -1, 111, 11), false);

        // Merge local copy back into original container.
        container.merge(local, baseIndex);
        assertEquals(baseIndex + 1, container.entryCount());

        // Verify that original entry 0 is now consumed in the original container.
        var exp0 = new RectBounds();
        container.computeRepaintRegion(new RectBounds(0, 0, 10, 10), exp0);
        assertEquals(new RectBounds(0, 0, 10, 10), exp0);

        // Verify that the appended entry exists and behaves as expected.
        var expNew = new RectBounds();
        container.computeRepaintRegion(new RectBounds(100, 0, 110, 10), expNew);
        assertEquals(new RectBounds(99, -1, 111, 11), expNew);
    }
}
