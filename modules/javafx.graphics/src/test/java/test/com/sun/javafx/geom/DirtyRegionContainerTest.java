/*
 * Copyright (c) 2011, 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.javafx.geom.DirtyRegionContainer;
import com.sun.javafx.geom.RectBounds;
import com.sun.javafx.geom.transform.Affine2D;
import com.sun.javafx.geom.transform.Translate2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DirtyRegionContainerTest {

    static RectBounds[] nonIntersecting_3_Regions = new RectBounds[] {
            new RectBounds(0, 0, 20, 20),
            new RectBounds(25, 25, 50, 50),
            new RectBounds(60, 60, 100, 100)
        };

    @Test
    public void test_maxSpace() {
        DirtyRegionContainer drc = new DirtyRegionContainer(10);
        assertEquals(10, drc.maxSpace());
    }

    @Test
    public void test_size() {
        DirtyRegionContainer drc = new DirtyRegionContainer(5);
        drc.deriveWithNewRegions(nonIntersecting_3_Regions);
        assertEquals(3, drc.size());
    }

    @Test
    public void test_deriveWithNewBounds() {
        DirtyRegionContainer drc = new DirtyRegionContainer(5);
        drc.deriveWithNewRegions(nonIntersecting_3_Regions);
        for (int i = 0; i < drc.size(); i++) {
            RectBounds rb = drc.getDirtyRegion(i);
            assertEquals(nonIntersecting_3_Regions[i], rb);
        }
    }

    @Test
    public void test_deriveWithNewBounds_null() {
        DirtyRegionContainer drc = getDRC_initialized();
        drc.deriveWithNewRegions(null);
        for (int i = 0; i < drc.size(); i++) {
            RectBounds rb = drc.getDirtyRegion(i);
            assertEquals(nonIntersecting_3_Regions[i], rb);
        }
    }

    @Test
    public void test_deriveWithNewBounds_zero_length () {
        DirtyRegionContainer drc = getDRC_initialized();
        drc.deriveWithNewRegions(new RectBounds[]{});
        for (int i = 0; i < drc.size(); i++) {
            RectBounds rb = drc.getDirtyRegion(i);
            assertEquals(nonIntersecting_3_Regions[i], rb);
        }
    }

    @Test
    public void test_deriveWithNewBounds_biger_length () {
        DirtyRegionContainer drc = getDRC_initialized();
        RectBounds[] arry = new RectBounds[]{
            new RectBounds(1, 1, 10, 10),
            new RectBounds(15, 15, 50, 50),
            new RectBounds(60, 60, 100, 100),
            new RectBounds(110, 110, 200, 200)
        };
        drc.deriveWithNewRegions(arry);
        for (int i = 0; i < drc.size(); i++) {
            RectBounds rb = drc.getDirtyRegion(i);
            assertEquals(arry[i], rb);
        }
    }

    @Test
    public void test_copy() {
        DirtyRegionContainer drc = getDRC_initialized();
        DirtyRegionContainer copyDrc = drc.copy();
        assertTrue(copyDrc != drc);
        assertEquals(copyDrc, drc);
    }

    @Test
    public void test_getDirtyRegion() {
        DirtyRegionContainer drc = getDRC_initialized();
        RectBounds dr = drc.getDirtyRegion(1);
        assertEquals(new RectBounds(25, 25, 50, 50), dr);
    }

    @Test
    public void test_getDirtyRegion_AIOOBE() {
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> {
            DirtyRegionContainer drc = getDRC_initialized();
            RectBounds dr = drc.getDirtyRegion(10);
        });
    }

    @Test
    public void test_addDirtyRegion_non_intersecting() {
        DirtyRegionContainer drc = getDRC_initialized();
        RectBounds newregion = new RectBounds(150, 150, 200, 200);
        drc.addDirtyRegion(newregion);

        assertEquals(4, drc.size());
        for(int i = 0; i < drc.size() - 1; i++) {
            assertEquals(nonIntersecting_3_Regions[i], (drc.getDirtyRegion(i)));
        }
        assertEquals(drc.getDirtyRegion(drc.size() - 1), newregion);
    }

    @Test
    public void test_addDirtyRegion_has_space_intersect_once() {
        DirtyRegionContainer drc = getDRC_initialized();

        drc.addDirtyRegion(new RectBounds(10, 10, 22, 15));

        assertEquals(3, drc.size());
        assertEquals(new RectBounds(60, 60, 100, 100), drc.getDirtyRegion(0));
        assertEquals(new RectBounds(25, 25, 50, 50), drc.getDirtyRegion(1));
        assertEquals(new RectBounds(0, 0, 22, 20), drc.getDirtyRegion(2));
    }

    @Test
    public void test_addDirtyRegion_has_space_intersect_twice() {
        DirtyRegionContainer drc = getDRC_initialized();

        drc.addDirtyRegion(new RectBounds(10, 10, 40, 40));

        assertEquals(2, drc.size());
        assertEquals(new RectBounds(60, 60, 100, 100), drc.getDirtyRegion(0));
        assertEquals(new RectBounds(0, 0, 50, 50), drc.getDirtyRegion(1));
    }

    @Test
    public void test_addDirtyRegion_has_space_intersect_all() {
        DirtyRegionContainer drc = getDRC_initialized();
        drc.addDirtyRegion(new RectBounds(10, 10, 80, 80));

        assertEquals(1, drc.size());
        assertEquals(new RectBounds(0, 0, 100, 100), drc.getDirtyRegion(0));
    }

    @Test
    public void test_addDirtyRegion_no_space_intersect_once() {
        DirtyRegionContainer drc = getDRC_initialized();
        drc.addDirtyRegion(new RectBounds(120, 120, 150, 150));

        drc.addDirtyRegion(new RectBounds(10, 10, 22, 15));

        assertEquals(4, drc.size());
        assertEquals(new RectBounds(120, 120, 150, 150), drc.getDirtyRegion(0));
        assertEquals(new RectBounds(25, 25, 50, 50), drc.getDirtyRegion(1));
        assertEquals(new RectBounds(60, 60, 100, 100), drc.getDirtyRegion(2));
        assertEquals(new RectBounds(0, 0, 22, 20), drc.getDirtyRegion(3));
    }

    @Test
    public void test_addDirtyRegion_no_space_intersect_twice() {
        DirtyRegionContainer drc = getDRC_initialized();
        drc.addDirtyRegion(new RectBounds(120, 120, 150, 150));

        drc.addDirtyRegion(new RectBounds(10, 10, 40, 40));

        assertEquals(3, drc.size());
        assertEquals(new RectBounds(120, 120, 150, 150), drc.getDirtyRegion(0));
        assertEquals(new RectBounds(60, 60, 100, 100), drc.getDirtyRegion(1));
        assertEquals(new RectBounds(0, 0, 50, 50), drc.getDirtyRegion(2));
    }

    @Test
    public void test_addDirtyRegion_negativePaddingIsClampedToZero() {
        DirtyRegionContainer drc = new DirtyRegionContainer(4);
        drc.addDirtyRegion(new RectBounds(0, 0, 10, 10), -1, -2, -3, -4);
        assertEquals(1, drc.size());

        DirtyRegionContainer.Entry entry = drc.getEntry(0);
        assertEquals(new RectBounds(0, 0, 10, 10), entry.getExtendedRegion());
        assertPadding(0, 0, 0, 0, entry);
    }

    @Test
    public void test_addDirtyRegion_withPadding_preservesPaddingAndEffectiveBounds() {
        DirtyRegionContainer drc = getDRC_initialized();
        drc.addDirtyRegion(new RectBounds(150, 150, 200, 200), 1, 2, 3, 4);
        assertEquals(4, drc.size());

        DirtyRegionContainer.Entry entry = drc.getEntry(3);
        assertPadding(1, 2, 3, 4, entry);
        assertEquals(149, entry.extendedMinX());
        assertEquals(148, entry.extendedMinY());
        assertEquals(203, entry.extendedMaxX());
        assertEquals(204, entry.extendedMaxY());
    }

    /**
     * A DRC contains the padded dirty region A, then the padded dirty region B is added.
     * Since the padding of both dirty regions overlaps, the cores are combined and the padding remains.
     *
     * <pre>
     *   ┌─────────────┐                                    ┌────────────────────────┐
     *   │   ╔═════╗   │                                    │   ╔════════════════╗   │
     *   │   ║  A  ║   │                 addDirtyRegion     │   ║                ║   │
     *   │   ╚═════╝ ┌─┼───────────┐    --------------->    │   ║     A + B      ║   │
     *   └───────────┼─┘ ╔═════╗   │                        │   ║                ║   │
     *               │   ║  B  ║   │                        │   ║                ║   │
     *               │   ╚═════╝   │                        │   ╚════════════════╝   │
     *               └─────────────┘                        └────────────────────────┘
     * </pre>
     */
    @Test
    public void test_addDirtyRegion_intersecting_paddedRegions() {
        var drc = new DirtyRegionContainer(4);
        drc.addDirtyRegion(new RectBounds(10, 10, 20, 20), 5, 5, 5, 5);
        drc.addDirtyRegion(new RectBounds(28, 28, 38, 38), 5, 5, 5, 5);
        assertEquals(1, drc.size());

        DirtyRegionContainer.Entry entry = drc.getEntry(0);
        assertEquals(new RectBounds(10, 10, 38, 38), entry.getCoreRegion());
        assertEquals(new RectBounds(5, 5, 43, 43), entry.getExtendedRegion());
        assertPadding(5, 5, 5, 5, entry);
    }

    /**
     * A DRC contains the padded dirty region A, then the non-padded dirty region B is added. Since the padding
     * of both dirty regions overlaps, the cores are combined. However, because B has no padding, the resulting
     * dirty region only has padding where it was contributed by A.
     *
     * <pre>
     *   ┌─────────────┐                                ┌────────────────────┐
     *   │   ╔═════╗   │          addDirtyRegion        │   ╔════════════════╗
     *   │   ║  A  ║   │         --------------->       │   ║                ║
     *   │   ╚═════╝ ╔═╪════╗                           │   ║     A + B      ║
     *   └───────────╫─┘ B  ║                           │   ║                ║
     *               ╚══════╝                           └───╚════════════════╝
     * </pre>
     */
    @Test
    public void test_addDirtyRegion_paddedRegion_intersect_nonPaddedRegion() {
        var drc = new DirtyRegionContainer(4);
        drc.addDirtyRegion(new RectBounds(10, 10, 20, 20), 10, 10, 10, 10);
        drc.addDirtyRegion(new RectBounds(28, 28, 38, 38));
        assertEquals(1, drc.size());

        DirtyRegionContainer.Entry entry = drc.getEntry(0);
        assertEquals(new RectBounds(10, 10, 38, 38), entry.getCoreRegion());
        assertEquals(new RectBounds(0, 0, 38, 38), entry.getExtendedRegion());
        assertPadding(10, 10, 0, 0, entry);
    }

    /**
     * A DRC with two padded dirty regions (A and B) is intersected with a clipping rectangle, which leaves A intact,
     * but removes B except for the upper-left portion. After clipping, the DRC still contains two dirty regions.
     * Clipping only affects core regions, the padding is added back to the remaining core region.
     *
     * <pre>
     *   ╔═══════════╤═══════╗                                 ┌───────────┐
     *   ║  ╔═════╗  │       ║ clip                            │  ╔═════╗  │
     *   ║  ║  A  ║  │       ║                                 │  ║  A  ║  │
     *   ║  ╚═════╝  │       ║            intersectWith        │  ╚═════╝  │
     *   ╟───────────┘       ║           --------------->      └───────────┘
     *   ║             ┌─────╫─────┐                                         ┌────────┐
     *   ║             │  ╔══╬══╗  │                                         │  ╔══╗B │
     *   ╚═════════════╪══╬══╝B ║  │                                         │  ╚══╝  │
     *                 │  ╚═════╝  │                                         └────────┘
     *                 └───────────┘
     * </pre>
     */
    @Test
    public void test_intersectWith_clipsCoreWithoutPadding() {
        var drc = new DirtyRegionContainer(4);
        drc.addDirtyRegion(new RectBounds(5, 5, 10, 10), 4, 4, 4, 4);
        drc.addDirtyRegion(new RectBounds(20, 20, 30, 30), 4, 4, 4, 4);
        drc.intersectWith(new RectBounds(0, 0, 25, 25));
        assertEquals(2, drc.size());

        // Core and padding of A stays intact.
        DirtyRegionContainer.Entry a = drc.getEntry(0);
        assertEquals(new RectBounds(5, 5, 10, 10), a.getCoreRegion());
        assertEquals(new RectBounds(1, 1, 14, 14), a.getExtendedRegion());
        assertPadding(4, 4, 4, 4, a);

        // Core of B is clipped, padding stays intact.
        DirtyRegionContainer.Entry b = drc.getEntry(1);
        assertEquals(new RectBounds(20, 20, 25, 25), b.getCoreRegion());
        assertEquals(new RectBounds(16, 16, 29, 29), b.getExtendedRegion());
        assertPadding(4, 4, 4, 4, b);
    }

    @Test
    public void test_intersectWith_removesRegion_whenCoreIsDisjoint() {
        var drc = new DirtyRegionContainer(4);
        drc.addDirtyRegion(new RectBounds(0, 0, 10, 10));
        drc.intersectWith(new RectBounds(20, 20, 30, 30));
        assertEquals(0, drc.size());
    }

    @Test
    public void test_intersectWith_removesRegion_whenOnlyPaddingOverlaps() {
        var drc = new DirtyRegionContainer(4);
        drc.addDirtyRegion(new RectBounds(0, 0, 10, 10), 5, 5, 5, 5);
        drc.intersectWith(new RectBounds(12, 12, 20, 20));
        assertEquals(0, drc.size());
    }

    @Test
    public void test_transform_translate() {
        DirtyRegionContainer drc = new DirtyRegionContainer(4);
        drc.addDirtyRegion(new RectBounds(5, 5, 10, 10), 1, 2, 3, 4);
        drc.transform(new Translate2D(10, 20));
        assertEquals(1, drc.size());
        assertEquals(new RectBounds(15, 25, 20, 30), drc.getDirtyRegion(0));
        assertPadding(1, 2, 3, 4, drc.getEntry(0));
    }

    @Test
    public void test_transform_scale() {
        var drc = new DirtyRegionContainer(4);
        drc.addDirtyRegion(new RectBounds(5, 5, 10, 10), 1, 2, 3, 4);
        drc.transform(Affine2D.getScaleInstance(2, 2));
        assertEquals(1, drc.size());
        assertEquals(new RectBounds(10, 10, 20, 20), drc.getDirtyRegion(0));
        assertPadding(2, 4, 6, 8, drc.getEntry(0));
    }

    @Test
    public void test_transform_rotate45() {
        var drc = new DirtyRegionContainer(4);
        drc.addDirtyRegion(new RectBounds(5, 5, 10, 10), 1, 2, 3, 4);
        drc.transform(Affine2D.getRotateInstance(Math.PI / 4, 0, 0));
        assertEquals(1, drc.size());
        assertRegion(new RectBounds(-3.536f, 7.071f, 3.535f, 14.142f), drc.getEntry(0).getCoreRegion(), 0.001f);
        assertRegion(new RectBounds(-7.071f, 4.95f, 7.071f, 19.092f), drc.getEntry(0).getExtendedRegion(), 0.001f);
        assertPadding(3.536f, 2.121f, 3.536f, 4.95f, drc.getEntry(0), 0.001f);
    }
    @Test
    public void test_transform_rotate45_mergesIntersectingRegions() {
        var drc = new DirtyRegionContainer(4);
        drc.addDirtyRegion(new RectBounds(5, 5, 10, 10), 1, 2, 3, 4);
        drc.addDirtyRegion(new RectBounds(15, 5, 20, 10), 1, 2, 3, 4);
        drc.transform(Affine2D.getRotateInstance(Math.PI / 4, 0, 0));
        assertEquals(1, drc.size());
        assertRegion(new RectBounds(-3.536f, 7.071f, 10.606f, 21.213f), drc.getEntry(0).getCoreRegion(), 0.001f);
        assertRegion(new RectBounds(-7.071f, 4.95f, 14.142f, 26.163f), drc.getEntry(0).getExtendedRegion(), 0.001f);
        assertPadding(3.536f, 2.121f, 3.536f, 4.95f, drc.getEntry(0), 0.001f);
    }

    @Test
    public void test_transform_rotate180() {
        var drc = new DirtyRegionContainer(4);
        drc.addDirtyRegion(new RectBounds(5, 5, 10, 10), 2, 1, 0, 0);
        drc.transform(Affine2D.getRotateInstance(Math.PI, 0, 0));
        assertEquals(1, drc.size());
        assertRegion(new RectBounds(-10, -10, -5, -5), drc.getEntry(0).getCoreRegion(), 0.001f);
        assertRegion(new RectBounds(-10, -10, -3, -4), drc.getEntry(0).getExtendedRegion(), 0.001f);
        assertPadding(0, 0, 2, 1, drc.getEntry(0), 0.001f);
    }

    @Test
    public void test_merge_disjointCoreRegions_withIntersectingPadding() {
        var drc1 = new DirtyRegionContainer(4);
        var drc2 = new DirtyRegionContainer(4);
        drc1.addDirtyRegion(new RectBounds(10, 10, 20, 20), 5, 5, 5, 5);
        drc2.addDirtyRegion(new RectBounds(28, 28, 38, 38), 5, 5, 5, 5);
        drc1.merge(drc2);
        assertEquals(1, drc1.size());

        DirtyRegionContainer.Entry entry = drc1.getEntry(0);
        assertEquals(new RectBounds(10, 10, 38, 38), entry.getCoreRegion());
        assertEquals(new RectBounds(5, 5, 43, 43), entry.getExtendedRegion());
    }

    @Test
    public void test_merge_paddedRegion_withNonPaddedRegion() {
        var drc1 = new DirtyRegionContainer(4);
        var drc2 = new DirtyRegionContainer(4);
        drc1.addDirtyRegion(new RectBounds(10, 10, 20, 20), 10, 10, 10, 10);
        drc2.addDirtyRegion(new RectBounds(28, 28, 38, 38));
        drc1.merge(drc2);
        assertEquals(1, drc1.size());

        DirtyRegionContainer.Entry entry = drc1.getEntry(0);
        assertEquals(new RectBounds(10, 10, 38, 38), entry.getCoreRegion());
        assertEquals(new RectBounds(0, 0, 38, 38), entry.getExtendedRegion());
        assertPadding(10, 10, 0, 0, entry);
    }

    @Test
    public void test_copy_preservesPadding() {
        var drc = new DirtyRegionContainer(4);
        drc.addDirtyRegion(new RectBounds(10, 10, 20, 20), 1, 2, 3, 4);
        DirtyRegionContainer copy = drc.copy();
        DirtyRegionContainer.Entry entry = drc.getEntry(0);

        assertNotSame(copy, drc);
        assertEquals(drc, copy);
        assertEquals(1, copy.size());
        assertEquals(new RectBounds(10, 10, 20, 20), copy.getDirtyRegion(0));
        assertPadding(1, 2, 3, 4, entry);
    }

    @Test
    public void test_compress_usesEffectiveAreaCost() {
        // Capacity 3, fill it with 3 disjoint effective regions.
        // A has a huge left padding so any pair with A yields a huge effective union area.
        // B and C have a small union area, so compression should merge B and C.
        var drc = new DirtyRegionContainer(3);
        var A = new RectBounds(0, 0, 10, 10);
        var B = new RectBounds(100, 0, 110, 10);
        var C = new RectBounds(200, 0, 210, 10);
        drc.addDirtyRegion(A, 1000, 10, 10, 10); // effective minX = -1000
        drc.addDirtyRegion(B, 10, 10, 10, 10);
        drc.addDirtyRegion(C, 10, 10, 10, 10);
        assertEquals(3, drc.size());

        // Add D to force compression (no overlap, and no free slot).
        var D = new RectBounds(300, 0, 310, 10);
        drc.addDirtyRegion(D, 0, 0, 0, 0);

        // Still at capacity after compress + add.
        assertEquals(3, drc.size());

        // A is unchanged in slot 0.
        DirtyRegionContainer.Entry entry0 = drc.getEntry(0);
        assertEquals(new RectBounds(0, 0, 10, 10), entry0.getCoreRegion());
        assertPadding(1000, 10, 10, 10, entry0);

        // Verify that B and C were merged into core union [100..210] in slot 1.
        DirtyRegionContainer.Entry entry1 = drc.getEntry(1);
        assertEquals(new RectBounds(100, 0, 210, 10), entry1.getCoreRegion());
        assertPadding(10, 10, 10, 10, entry1);

        // D is unchanged in slot 2.
        DirtyRegionContainer.Entry entry2 = drc.getEntry(2);
        assertEquals(new RectBounds(300, 0, 310, 10), entry2.getCoreRegion());
        assertPadding(0, 0, 0, 0, entry2);
    }

    private static void assertPadding(float left, float top, float right, float bottom,
                                      DirtyRegionContainer.Entry entry) {
        assertPadding(left, top, right, bottom, entry, 0);
    }

    private static void assertPadding(float left, float top, float right, float bottom,
                                      DirtyRegionContainer.Entry entry, float epsilon) {
        assertEquals(left, entry.leftPadding(), epsilon);
        assertEquals(top, entry.topPadding(), epsilon);
        assertEquals(right, entry.rightPadding(), epsilon);
        assertEquals(bottom, entry.bottomPadding(), epsilon);
    }

    private static void assertRegion(RectBounds expected, RectBounds actual, float epsilon) {
        assertEquals(expected.getMinX(), actual.getMinX(), epsilon);
        assertEquals(expected.getMaxX(), actual.getMaxX(), epsilon);
        assertEquals(expected.getMinY(), actual.getMinY(), epsilon);
        assertEquals(expected.getMaxY(), actual.getMaxY(), epsilon);
    }

    private DirtyRegionContainer getDRC_initialized() {
        DirtyRegionContainer drc = new DirtyRegionContainer(4);
        return drc.deriveWithNewRegions(nonIntersecting_3_Regions);
    }
}
