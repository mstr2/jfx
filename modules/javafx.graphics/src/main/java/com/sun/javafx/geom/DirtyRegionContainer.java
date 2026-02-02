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

package com.sun.javafx.geom;

import com.sun.javafx.geom.transform.BaseTransform;

/**
 * Container for array of dirty regions. This container internally holds
 * pointer to the first empty dirty region in the array and index of last
 * modified dirty region. It also introduces convenient methods to modify
 * the array of dirty regions.
 */
public final class DirtyRegionContainer implements Container {

    public static final int DTR_OK = 1;
    public static final int DTR_CONTAINS_CLIP = 0;

    private static final double EPSILON = 1e-12;

    private Entry[] entries;
    private int emptyIndex;

    public DirtyRegionContainer(int count) {
        initDirtyRegions(count);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DirtyRegionContainer other) || size() != other.size()) {
            return false;
        }

        for (int i = 0; i < emptyIndex; i++) {
            Entry a = entries[i];
            Entry b = other.entries[i];
            if (!a.core.equals(b.core)) return false;
            if (Float.compare(a.leftPadding, b.leftPadding) != 0) return false;
            if (Float.compare(a.topPadding, b.topPadding) != 0) return false;
            if (Float.compare(a.rightPadding, b.rightPadding) != 0) return false;
            if (Float.compare(a.bottomPadding, b.bottomPadding) != 0) return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + emptyIndex;
        for (int i = 0; i < emptyIndex; i++) {
            Entry e = entries[i];
            hash = 97 * hash + e.core.hashCode();
            hash = 97 * hash + Float.floatToIntBits(e.leftPadding);
            hash = 97 * hash + Float.floatToIntBits(e.topPadding);
            hash = 97 * hash + Float.floatToIntBits(e.rightPadding);
            hash = 97 * hash + Float.floatToIntBits(e.bottomPadding);
        }
        return hash;
    }

    public DirtyRegionContainer deriveWithNewRegion(RectBounds region) {
        return deriveWithNewRegion(region, 0, 0, 0, 0);
    }

    public DirtyRegionContainer deriveWithNewRegion(RectBounds region,
                                                    float padLeft,
                                                    float padTop,
                                                    float padRight,
                                                    float padBottom) {
        if (region == null) {
            return this;
        }

        entries[0].setBounds(region, padLeft, padTop, padRight, padBottom);
        emptyIndex = 1;
        return this;
    }

    public DirtyRegionContainer deriveWithNewRegions(RectBounds[] regions) {
        if (regions == null || regions.length == 0) {
            return this;
        }
        if (regions.length > maxSpace()) {
            initDirtyRegions(regions.length);
        }

        regioncopy(regions, 0, entries, 0, regions.length);
        emptyIndex = regions.length;
        return this;
    }

    public DirtyRegionContainer deriveWithNewContainer(DirtyRegionContainer other) {
        if (other == null || other.maxSpace() == 0) {
            return this;
        }

        if (other.maxSpace() > maxSpace()) {
            initDirtyRegions(other.maxSpace());
        }

        entrycopy(other.entries, 0, entries, 0, other.emptyIndex);
        emptyIndex = other.emptyIndex;
        return this;
    }

    private void initDirtyRegions(int count) {
        entries = new Entry[count];
        for (int i = 0; i < count; i++) {
            entries[i] = new Entry();
        }
        emptyIndex = 0;
    }

    public DirtyRegionContainer copy() {
        var drc = new DirtyRegionContainer(maxSpace());
        entrycopy(entries, 0, drc.entries, 0, emptyIndex);
        drc.emptyIndex = emptyIndex;
        return drc;
    }

    public int maxSpace() {
        return entries.length;
    }

    public Entry getEntry(int index) {
        return entries[index];
    }

    /**
     * Gets the dirty region at given index.
     * @param index the index of requested dirty region
     * @return dirty region at given index
     */
    public RectBounds getDirtyRegion(int index) {
        return entries[index].core;
    }

    /**
     * Adds new dirty region with zero padding.
     */
    public void addDirtyRegion(final RectBounds region) {
        addDirtyRegion(region, 0, 0, 0, 0);
    }

    /**
     * Adds a new dirty region with the specified padding.
     */
    public void addDirtyRegion(RectBounds other, float padLeft, float padTop, float padRight, float padBottom) {
        if (other == null || other.isEmpty()) {
            return;
        }

        float otherMinX = other.getMinX();
        float otherMinY = other.getMinY();
        float otherMaxX = other.getMaxX();
        float otherMaxY = other.getMaxY();
        float currentPadL = Math.max(0, padLeft);
        float currentPadT = Math.max(0, padTop);
        float currentPadR = Math.max(0, padRight);
        float currentPadB = Math.max(0, padBottom);
        float currentEffMinX = otherMinX - currentPadL;
        float currentEffMinY = otherMinY - currentPadT;
        float currentEffMaxX = otherMaxX + currentPadR;
        float currentEffMaxY = otherMaxY + currentPadB;

        int tempIndex = 0;
        int regionCount = emptyIndex;

        // Eliminate overlapping regions by computing their union.
        for (int i = 0; i < regionCount; i++) {
            Entry entry = entries[tempIndex];
            float entryEffMinX = entry.extendedMinX();
            float entryEffMinY = entry.extendedMinY();
            float entryEffMaxX = entry.extendedMaxX();
            float entryEffMaxY = entry.extendedMaxY();

            if (intersects(currentEffMinX, currentEffMinY, currentEffMaxX, currentEffMaxY,
                           entryEffMinX, entryEffMinY, entryEffMaxX, entryEffMaxY)) {
                // Union of core regions
                otherMinX = Math.min(otherMinX, entry.core.getMinX());
                otherMinY = Math.min(otherMinY, entry.core.getMinY());
                otherMaxX = Math.max(otherMaxX, entry.core.getMaxX());
                otherMaxY = Math.max(otherMaxY, entry.core.getMaxY());

                // Union of effective regions
                currentEffMinX = Math.min(currentEffMinX, entryEffMinX);
                currentEffMinY = Math.min(currentEffMinY, entryEffMinY);
                currentEffMaxX = Math.max(currentEffMaxX, entryEffMaxX);
                currentEffMaxY = Math.max(currentEffMaxY, entryEffMaxY);

                // Recompute padding against the updated core bounds
                currentPadL = otherMinX - currentEffMinX;
                currentPadT = otherMinY - currentEffMinY;
                currentPadR = currentEffMaxX - otherMaxX;
                currentPadB = currentEffMaxY - otherMaxY;

                // Remove the entry (swap with last active).
                Entry tmp = entries[tempIndex];
                entries[tempIndex] = entries[emptyIndex - 1];
                entries[emptyIndex - 1] = tmp;
                emptyIndex--;
            } else {
                tempIndex++;
            }
        }

        if (hasSpace()) {
            Entry entry = entries[emptyIndex];
            entry.setBounds(
                otherMinX, otherMinY, otherMaxX, otherMaxY,
                currentPadL, currentPadT, currentPadR, currentPadB);
            emptyIndex++;
            return;
        }

        // No free slot: must merge or compress.
        if (entries.length == 1) {
            entries[0].setUnionWith(other, currentPadL, currentPadT, currentPadR, currentPadB);
        } else {
            compress(other, currentPadL, currentPadT, currentPadR, currentPadB);
        }
    }

    public void merge(DirtyRegionContainer other) {
        int otherSize = other.size();
        for (int i = 0; i < otherSize; i++) {
            Entry entry = other.entries[i];
            addDirtyRegion(entry.core, entry.leftPadding, entry.topPadding, entry.rightPadding, entry.bottomPadding);
        }
    }

    public void intersectWith(BaseBounds bounds) {
        for (int i = 0; i < emptyIndex; i++) {
            Entry entry = entries[i];
            entry.setIntersectionWith(bounds);

            if (entry.core.isEmpty()) {
                System.arraycopy(entries, i + 1, entries, i, emptyIndex - i - 1);
                --emptyIndex;
            }
        }
    }

    public void transform(BaseTransform tx) {
        if (!tryFastTransform(tx)) {
            generalTransform(tx);
        }
    }

    /**
     * Fast path: if we have a translation or scaling transform, we can use a simpler algorithm to
     * transform the content of this dirty region container. This transform will retain the number
     * of dirty regions in this container.
     *
     * @return {@code true} if the fast path was taken
     */
    private boolean tryFastTransform(BaseTransform tx) {
        if (!tx.is2D()) {
            return false;
        }

        double mxy = tx.getMxy();
        double myx = tx.getMyx();

        if (Math.abs(mxy) > EPSILON || Math.abs(myx) > EPSILON) {
            return false; // shear/rotation
        }

        double mxx = tx.getMxx();
        double myy = tx.getMyy();
        double mxt = tx.getMxt();
        double myt = tx.getMyt();

        if (Math.abs(mxx) < EPSILON || Math.abs(myy) < EPSILON) {
            return false; // degenerate scale
        }

        float sx = (float)Math.abs(mxx);
        float sy = (float)Math.abs(myy);
        boolean flipX = mxx < 0.0;
        boolean flipY = myy < 0.0;

        for (int i = 0; i < emptyIndex; i++) {
            Entry entry = entries[i];

            // Transform core bounds (handle possible reflection via min/max swap)
            float tx0 = (float)(mxx * entry.core.getMinX() + mxt);
            float tx1 = (float)(mxx * entry.core.getMaxX() + mxt);
            float ty0 = (float)(myy * entry.core.getMinY() + myt);
            float ty1 = (float)(myy * entry.core.getMaxY() + myt);

            // Transform padding: padding magnitudes scale by abs(scale), but sides swap under reflection
            float inL = entry.leftPadding * sx;
            float inR = entry.rightPadding * sx;
            float inT = entry.topPadding * sy;
            float inB = entry.bottomPadding * sy;

            entry.setBounds(
                Math.min(tx0, tx1), Math.min(ty0, ty1),
                Math.max(tx0, tx1), Math.max(ty0, ty1),
                flipX ? inR : inL, flipY ? inB : inT,
                flipX ? inL : inR, flipY ? inT : inB);
        }

        return true;
    }

    /**
     * This is the general path to transform the content of this dirty region container when we have a
     * rotation or shear transform. After applying the transformation, the dirty regions in the container
     * can overlap; this is addressed by a post-transform merging step to ensure that dirty regions do
     * not overlap.
     */
    private void generalTransform(BaseTransform tx) {
        if (tx == null) {
            return;
        }

        // Transform each entry's core and effective regions
        for (int i = 0; i < emptyIndex; ++i) {
            Entry entry = entries[i];

            if (entry.leftPadding > 0 || entry.topPadding > 0 || entry.rightPadding > 0 || entry.bottomPadding > 0) {
                RectBounds effRegion = entry.getExtendedRegion();
                tx.transform(effRegion, effRegion);
                tx.transform(entry.core, entry.core);
                entry.setPadding(
                    entry.core.getMinX() - effRegion.getMinX(),
                    entry.core.getMinY() - effRegion.getMinY(),
                    effRegion.getMaxX() - entry.core.getMaxX(),
                    effRegion.getMaxY() - entry.core.getMaxY());
            } else {
                tx.transform(entry.core, entry.core);
                entry.extendedValid = false;
            }
        }

        // Re-merge overlaps in effective space to restore non-overlapping invariant
        mergeOverlappingRegions();
    }

    private void mergeOverlappingRegions() {
        for (int i = 0; i < emptyIndex; ++i) {
            Entry a = entries[i];
            int j = i + 1;

            while (j < emptyIndex) {
                Entry b = entries[j];

                if (intersects(a.extendedMinX(), a.extendedMinY(), a.extendedMaxX(), a.extendedMaxY(),
                               b.extendedMinX(), b.extendedMinY(), b.extendedMaxX(), b.extendedMaxY())) {
                    a.setUnionWith(b.core, b.leftPadding, b.topPadding, b.rightPadding, b.bottomPadding);
                    Entry tmp = entries[j];
                    entries[j] = entries[emptyIndex - 1];
                    entries[emptyIndex - 1] = tmp;
                    --emptyIndex;
                } else {
                    ++j;
                }
            }
        }
    }

    public int size() {
        return emptyIndex;
    }

    @Override
    public void reset() {
        emptyIndex = 0;
    }

    private void compress(final RectBounds region,
                          float padLeft, float padTop, float padRight, float padBottom) {
        compress_heap();
        addDirtyRegion(region, padLeft, padTop, padRight, padBottom);
    }

    /**
     * If there are empty regions in the dirty regions array.
     * @return true if there is empty region in the array; false otherwise
     */
    private boolean hasSpace() {
        return emptyIndex < entries.length;
    }

    private void regioncopy(RectBounds[] src, int from, Entry[] dest, int to, int length) {
        RectBounds rb;
        for (int i = 0; i < length; i++) {
            rb = src[from++];
            if (rb == null) {
                dest[to++].clear();
            } else {
                dest[to++].setBounds(rb);
            }
        }
    }

    private void entrycopy(Entry[] src, int from, Entry[] dest, int to, int length) {
        for (int i = 0; i < length; i++) {
            dest[to++].copyFrom(src[from++]);
        }
    }

    public void grow(int horizontal, int vertical) {
        if (horizontal != 0 || vertical != 0) {
            for (int i = 0; i < emptyIndex; i++) {
                entries[i].core.grow(horizontal, vertical);
                entries[i].extendedValid = false;
            }

            mergeOverlappingRegions();
        }
    }

    public void roundOut() {
        for (int i = 0; i < emptyIndex; ++i) {
            entries[i].core.roundOut();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < emptyIndex; i++) {
            Entry e = entries[i];
            sb.append(e.core);
            if (e.leftPadding != 0 || e.topPadding != 0 || e.rightPadding != 0 || e.bottomPadding != 0) {
                sb.append(" pad(").append(e.leftPadding).append(", ").append(e.topPadding)
                        .append(", ").append(e.rightPadding).append(", ").append(e.bottomPadding).append(')');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static boolean intersects(float aMinX, float aMinY, float aMaxX, float aMaxY,
                                      float bMinX, float bMinY, float bMaxX, float bMaxY) {
        return aMaxX > bMinX && aMinX < bMaxX && aMaxY > bMinY && aMinY < bMaxY;
    }

    /***************************************************************************
     * Shared for all compressing algorithms
     ***************************************************************************/
    private int[][] heap; // heap used for compressing dirty regions
    private int heapSize;
    private long invalidMask;

    private void heapCompress() {
        invalidMask = 0L;
        int[] map = new int[entries.length];
        for (int i = 0; i < map.length; ++i) {
            map[i] = i;
        }

        int[] min;
        for (int i = 0; i < entries.length / 2; ++i) { // compress to 1/2
            min = takeMinWithMap(map);
            int idx0 = resolveMap(map, min[1]);
            int idx1 = resolveMap(map, min[2]);
            if (idx0 != idx1) {
                Entry other = entries[idx1];
                entries[idx0].setUnionWith(
                    other.core, other.leftPadding, other.topPadding, other.rightPadding, other.bottomPadding);
                map[idx1] = idx0;
                invalidMask |= (1L << idx0);
                invalidMask |= (1L << idx1);
            }
        }

        // Move the unused regions to the end.
        Entry tmp;
        for (int i = 0; i < emptyIndex; ++i) {
            if (map[i] != i) {
                while (map[emptyIndex - 1] != emptyIndex - 1) --emptyIndex;
                if (i < emptyIndex - 1) {
                    tmp = entries[emptyIndex - 1];
                    entries[emptyIndex - 1] = entries[i];
                    entries[i] = tmp;
                    map[i] = i; // indicate that this element is OK
                    --emptyIndex;
                }
            }
        }
    }

    private void heapify() {
        for (int i = heapSize / 2; i >= 0; --i) {
            siftDown(i);
        }
    }

    private void siftDown(int i) {
        int end = heapSize >> 1;
        int[] temp;
        while (i < end) {
            int child = (i << 1) + 1;
            int[] left = heap[child];
            if (child + 1 < heapSize && heap[child + 1][0] < left[0]) {
                child = child + 1;
            }
            if (heap[child][0] >= heap[i][0]) {
                break;
            }
            temp = heap[child];
            heap[child] = heap[i];
            heap[i] = temp;
            i = child;
        }
    }

    private int[] takeMinWithMap(int[] map) {
        int[] temp = heap[0];

        // If minimum element used a region that has been merged since it was computed,
        // recompute its cost and push it down.
        while ((((1L << temp[1]) | (1L << temp[2])) & invalidMask) != 0L) {
            temp[0] = unifiedRegionArea(resolveMap(map, temp[1]), resolveMap(map, temp[2]));
            siftDown(0);
            if (heap[0] == temp) {
                break;
            }
            temp = heap[0];
        }

        heap[heapSize - 1] = temp;
        siftDown(0);
        heapSize--;
        return temp;
    }

    private int resolveMap(int[] map, int idx) {
        while (map[idx] != idx) idx = map[idx];
        return idx;
    }

    private int unifiedRegionArea(int i0, int i1) {
        Entry e0 = entries[i0];
        Entry e1 = entries[i1];

        float minX = Math.min(e0.extendedMinX(), e1.extendedMinX());
        float minY = Math.min(e0.extendedMinY(), e1.extendedMinY());
        float maxX = Math.max(e0.extendedMaxX(), e1.extendedMaxX());
        float maxY = Math.max(e0.extendedMaxY(), e1.extendedMaxY());

        return (int) ((maxX - minX) * (maxY - minY));
    }

    /***************************************************************************
     * Heap-based compressing algorithm
     ***************************************************************************/

    private void compress_heap() {
        assert entries.length == emptyIndex; // call only when there is no space left
        if (heap == null) {
            int n = entries.length;
            heap = new int[n * (n - 1) / 2][3];
        }
        heapSize = heap.length;
        int k = 0;
        for (int i = 0; i < entries.length - 1; ++i) {
            for (int j = i + 1; j < entries.length; ++j) {
                heap[k][0] = unifiedRegionArea(i, j);
                heap[k][1] = i;
                heap[k++][2] = j;
            }
        }
        heapify();
        heapCompress();
    }

    /***************************************************************************
     * Simple Monte-Carlo variant of compressing algorithm
     ***************************************************************************/

//    private void compress_mc() {
//        assert dirtyRegions.length == emptyIndex; // call only when there is no space left
//        heapSize = dirtyRegions.length;
//        if (heap == null) {
//            heap = new int[heapSize][3];
//        }
//        for (int i = 0; i < heapSize; ++i) { //number of tries
//            int i0 = random(dirtyRegions.length);
//            int i1 = random(dirtyRegions.length);
//            if (i1 == i0) i1 = (i0 + random(dirtyRegions.length / 2) + 1) % dirtyRegions.length;
//            heap[i][0] = unifiedRegionArea(i0, i1);
//            heap[i][1] = i0;
//            heap[i][2] = i1;
//        }
//        heapify();
//        heapCompress();
//    }
//
//    private static long rnd = System.currentTimeMillis();
//    // XOR Random generator by George Marsaglia http://www.jstatsoft.org/v08/i14/
//    // The LCG algorithm of Random() has an upleasant trait that the numbers generated in
//    // pairs always have some (tight) mathematical relationship
//    private int random(int n) {
//        rnd ^= (rnd << 21);
//        rnd ^= (rnd >>> 35);
//        rnd ^= (rnd << 4);
//        return (int) ((rnd) % n + n) % n;     // the problem with this algorithm is that 0 is never produced.
//                                              // Given it's independed probability for every bit, we can safely do % n here
//    }

    public static final class Entry {

        private final RectBounds core = new RectBounds();
        private final RectBounds extended = new RectBounds();
        private boolean extendedValid;
        private float leftPadding;
        private float topPadding;
        private float rightPadding;
        private float bottomPadding;

        public RectBounds getCoreRegion() {
            return core;
        }

        public RectBounds getExtendedRegion() {
            if (!extendedValid) {
                extendedValid = true;
                extended.setBounds(
                    extendedMinX(), extendedMinY(),
                    extendedMaxX(), extendedMaxY());
            }

            return extended;
        }

        public boolean hasPadding() {
            return leftPadding > 0 || topPadding > 0 || rightPadding > 0 || bottomPadding > 0;
        }

        public float leftPadding() {
            return leftPadding;
        }

        public float topPadding() {
            return topPadding;
        }

        public float rightPadding() {
            return rightPadding;
        }

        public float bottomPadding() {
            return bottomPadding;
        }

        public float extendedMinX() {
            return core.getMinX() - leftPadding;
        }

        public float extendedMinY() {
            return core.getMinY() - topPadding;
        }

        public float extendedMaxX() {
            return core.getMaxX() + rightPadding;
        }

        public float extendedMaxY() {
            return core.getMaxY() + bottomPadding;
        }

        private void clear() {
            core.makeEmpty();
            leftPadding = topPadding = rightPadding = bottomPadding = 0;
            extendedValid = false;
        }

        private void setBounds(RectBounds core) {
            if (core == null) {
                clear();
            } else {
                this.core.setBounds(core);
                leftPadding = topPadding = rightPadding = bottomPadding = 0;
                extendedValid = false;
            }
        }

        private void setBounds(float minX, float minY, float maxX, float maxY,
                               float left, float top, float right, float bottom) {
            core.setBounds(minX, minY, maxX, maxY);
            setPadding(left, top, right, bottom);
        }

        private void setBounds(RectBounds core, float left, float top, float right, float bottom) {
            if (core == null) {
                clear();
            } else {
                this.core.setBounds(core);
                setPadding(left, top, right, bottom);
            }
        }

        private void setPadding(float left, float top, float right, float bottom) {
            leftPadding = Math.max(0, left);
            topPadding = Math.max(0, top);
            rightPadding = Math.max(0, right);
            bottomPadding = Math.max(0, bottom);
            extendedValid = false;
        }

        private void setUnionWith(RectBounds core, float left, float top, float right, float bottom) {
            if (core == null || core.isEmpty()) {
                return;
            }

            // If this entry is empty, just become the other entry.
            if (this.core.isEmpty()) {
                setBounds(core, left, top, right, bottom);
                return;
            }

            // Union of extended (padded) rectangles
            float effMinX = Math.min(extendedMinX(), core.getMinX() - left);
            float effMinY = Math.min(extendedMinY(), core.getMinY() - top);
            float effMaxX = Math.max(extendedMaxX(), core.getMaxX() + right);
            float effMaxY = Math.max(extendedMaxY(), core.getMaxY() + bottom);

            // Union of core rectangles
            this.core.deriveWithUnion(core);

            // Recompute padding as excess of extended union over core union
            setPadding(
                this.core.getMinX() - effMinX,
                this.core.getMinY() - effMinY,
                effMaxX - this.core.getMaxX(),
                effMaxY - this.core.getMaxY());
        }

        private void setIntersectionWith(BaseBounds region) {
            if (core.isEmpty()) {
                return;
            }

            if (region == null || region.isEmpty()) {
                clear();
                return;
            }

            float coreMinX = Math.max(core.getMinX(), region.getMinX());
            float coreMinY = Math.max(core.getMinY(), region.getMinY());
            float coreMaxX = Math.min(core.getMaxX(), region.getMaxX());
            float coreMaxY = Math.min(core.getMaxY(), region.getMaxY());

            if (coreMaxX <= coreMinX || coreMaxY <= coreMinY) {
                clear();
            } else {
                core.setBounds(coreMinX, coreMinY, coreMaxX, coreMaxY);
                extendedValid = false;
            }
        }

        private void copyFrom(Entry other) {
            if (other == null) {
                clear();
            } else {
                setBounds(other.core, other.leftPadding, other.topPadding, other.rightPadding, other.bottomPadding);
            }
        }
    }
}
