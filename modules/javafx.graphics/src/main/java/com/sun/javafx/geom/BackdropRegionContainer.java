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

package com.sun.javafx.geom;

import com.sun.javafx.geom.transform.BaseTransform;
import com.sun.javafx.sg.prism.NGNode;
import java.util.Arrays;
import java.util.Objects;

/**
 * Tracks render-order dependencies introduced by backdrop effects and supports computing the minimal
 * repaint region needed to produce correct backdrop inputs.
 * <p>
 * In the NG render graph, dirty region tracking tries to redraw as little as possible by repainting
 * only pixels that have changed. Backdrop effects complicate this because they do not depend solely
 * on a node's own content:
 * <ol>
 *     <li>A backdrop-effect node reads ("samples") pixels from the render target that have already
 *         been rendered behind it.
 *     <li>It applies an effect (e.g. tint, blur) to those sampled pixels, producing new background
 *         pixels for the node's output area.
 *     <li>It then draws the node's own content on top.
 * </ol>
 *
 * While some effects only sample pixels directly under the node, others can also sample pixels outside
 * the node's own bounds. For example, a blur effect works by applying a convolution operation to a
 * region, which folds in pixels that are outside of that region. Therefore, when repainting any region
 * that includes pixels produced by a backdrop-effect node, the renderer may need to repaint a larger
 * region behind it to ensure the effect's input pixels are up-to-date.
 * <p>
 * {@code BackdropRegionContainer} is used during dirty-region accumulation in {@link NGNode}.
 * For each node that has a backdrop effect, an entry is added to this tracker that consists of two
 * axis-aligned rectangles:
 * <ul>
 *   <li><b>Output bounds</b>: the pixels that the node writes to the render target.
 *       This is the region that matters for overlap tests.
 *   <li><b>Input bounds</b>: the pixels that the node may read as backdrop input in order
 *       to produce its output. For a blur effect, this is typically the output bounds inflated
 *       by the blur radius.
 * </ul>
 *
 * Entries are added in render order. That ordering is essential: a node can only sample pixels that
 * have already been rendered, i.e. pixels produced by nodes with a lower insertion index.
 * <p>
 * Entries are "consumed" during a query by marking them inactive (rather than physically removing
 * them from lists) so that subsequent queries in the same frame do not re-process the same backdrop
 * nodes.
 */
public final class BackdropRegionContainer implements Container {

    private float[] outputBounds = new float[0];
    private float[] inputBounds = new float[0];
    private boolean[] local = new boolean[0];
    private long[] active = new long[0];
    private int entryCount;
    private int lastActiveIndex = -1;

    /**
     * Clears this container.
     * <p>
     * All previously recorded entries are discarded and the active bitset is cleared.
     * Internal storage is retained for reuse to avoid allocations.
     */
    @Override
    public void reset() {
        int usedWords = (entryCount + 63) >>> 6;
        if (usedWords > 0 && active.length > 0) {
            Arrays.fill(active, 0, Math.min(usedWords, active.length), 0L);
        }

        entryCount = 0;
        lastActiveIndex = -1;
    }

    /**
     * Returns the number of entries currently stored in this container.
     * This is the next insertion index, and includes inactive (consumed) entries.
     */
    public int entryCount() {
        return entryCount;
    }

    /**
     * Adds a backdrop entry to this container.
     * <p>
     * The {@code output} bounds describe the pixels written by the node and are used to test whether the
     * node contributes to a repaint region. The {@code input} bounds describe the pixels the node may read
     * as backdrop input in order to produce its output; they are used to expand repaint regions when needed.
     * <p>
     * The {@code local} flag indicates whether the backdrop entry has a locally bounded dependency, such
     * that changing a small input sub-region can only affect nearby output pixels. For these backdrops,
     * repaint expansion is based on the overlapping portion of the output. For non-local effects, any
     * input pixel may influence any output pixel, so any overlap causes the full input bounds to be
     * included.
     *
     * @param output the area the node writes to, not {@code null}
     * @param input the area the node may read as backdrop input, not {@code null}
     * @param local whether the input dependency is locally bounded
     */
    public void add(RectBounds output, RectBounds input, boolean local) {
        int idx = entryCount;
        ensureCapacity(idx + 1);

        this.local[idx] = local;

        int base = idx << 2;
        outputBounds[base] = output.getMinX();
        outputBounds[base + 1] = output.getMinY();
        outputBounds[base + 2] = output.getMaxX();
        outputBounds[base + 3] = output.getMaxY();
        inputBounds[base] = input.getMinX();
        inputBounds[base + 1] = input.getMinY();
        inputBounds[base + 2] = input.getMaxX();
        inputBounds[base + 3] = input.getMaxY();

        int word = idx >>> 6;
        int bit  = idx & 63;
        active[word] |= (1L << bit);
        entryCount++;
        lastActiveIndex = idx;
    }

    /**
     * Merges the state of another {@code BackdropRegionContainer} into this container.
     * <p>
     * In order to work correctly, this method must be used with the following protocol:
     * <ol>
     *     <li>Record {@code baseIndex = this.entryCount()}.
     *     <li>Obtain a container {@code other} and initialize it with a copy of this container's
     *         current entries {@code [0, baseIndex)}, typically via {@code other.merge(this, 0)}.
     *     <li>Now the other container can be manipulated, for example by transforming it into a different
     *         coordinate space, or by adding or consuming entries. If the other container was transformed
     *         into a different coordinate space, it must be transformed back before the next step.
     *     <li>Call {@code this.merge(other, baseIndex)} to merge the new entries of {@code other} into
     *         this container, and remove the entries of this container that were removed in the other
     *         container.
     * </ol>
     *
     * Important: This method is not a general-purpose merge. Calling this method with an unrelated container,
     * or with a {@code startIndex} that does not represent the boundary between the shared and newly added
     * entries, can lead to incorrect results.
     *
     * @param other the container to merge back, not {@code null}
     * @param startIndex first index in {@code other} that represents newly added entries; indices
     *                   {@code [0, startIndex)} must correspond to this container's existing entries
     *                   in the same render order
     */
    public void merge(BackdropRegionContainer other, int startIndex) {
        int count = other.entryCount - startIndex;
        if (count <= 0) {
            return;
        }

        int destStart = entryCount;
        ensureCapacity(destStart + count);

        int words = (startIndex + 63) >>> 6;
        for (int w = 0; w < words; w++) {
            active[w] &= other.active[w];
        }

        int srcBase = startIndex << 2;
        int dstBase = destStart << 2;
        int length = count << 2;

        System.arraycopy(other.outputBounds, srcBase, outputBounds, dstBase, length);
        System.arraycopy(other.inputBounds,  srcBase, inputBounds,  dstBase, length);
        System.arraycopy(other.local, startIndex, local, destStart, count);

        for (int i = 0; i < count; i++) {
            int srcIdx = startIndex + i;
            int srcWord = srcIdx >>> 6;
            int srcBit  = srcIdx & 63;

            if ((other.active[srcWord] & (1L << srcBit)) != 0L) {
                int dstIdx = destStart + i;
                int dstWord = dstIdx >>> 6;
                int dstBit  = dstIdx & 63;
                active[dstWord] |= (1L << dstBit);
            }
        }

        entryCount += count;
        lastActiveIndex = findLastActive(entryCount - 1);
    }

    /**
     * Transforms the stored bounds for entries starting at {@code startIndex}.
     * <p>
     * The transform is applied to both output and input bounds. If {@code startIndex} is
     * greater than or equal to {@link #entryCount()}, this method is a no-op.
     *
     * @param tx the transform to apply, not {@code null}
     * @param startIndex the first entry index (inclusive) to transform
     */
    public void transform(BaseTransform tx, int startIndex) {
        if (entryCount > 0 && startIndex < entryCount) {
            int offset = startIndex * 4;
            int count = (entryCount - startIndex) * 4;
            tx.transform(outputBounds, offset, outputBounds, offset, count);
            tx.transform(inputBounds, offset, inputBounds, offset, count);
        }
    }

    /**
     * Given a {@code region} whose pixels will be repainted, compute the largest region that must be repainted
     * so that every backdrop-effect node contributing pixels within {@code region} sees correct, up-to-date
     * input pixels.
     * <p>
     * The computation works as a backwards (render-order) dependency expansion:
     * <ol>
     *     <li>Start with {@code R = region}.
     *     <li>Find the <em>latest</em> (highest-index) recorded entry whose output bounds intersect {@code R}.
     *         If none exists, stop.
     *     <li>Union {@code R} with that entry's input bounds (because repainting its output requires its
     *         input to be correct).
     *     <li>Continue searching only among entries with <b>lower indices</b>, since only earlier rendered
     *         content can influence the backdrop input of the entry.
     *     <li>Repeat until {@code R} no longer grows.
     * </ol>
     *
     * The resulting region {@code R} is the minimal conservative repaint region needed to repaint the
     * initial {@code region}. Entries that contribute to the repaint region will be removed because the
     * caller now knows the size of the repaint region; giving the same answer in a subsequent call will
     * not improve this information.
     *
     * @param region repaint region, not {@code null}
     * @param expansion dependency expansion of the {@code region}, not {@code null}
     */
    public void computeRepaintRegion(RectBounds region, RectBounds expansion) {
        Objects.requireNonNull(region, "region must not be null");
        Objects.requireNonNull(expansion, "expansion must not be null");

        expansion.setBounds(region);

        if (entryCount == 0 || lastActiveIndex < 0 || region.isEmpty()) {
            return;
        }

        float exMinX = region.getMinX();
        float exMinY = region.getMinY();
        float exMaxX = region.getMaxX();
        float exMaxY = region.getMaxY();
        boolean changed = false;
        int from = Math.min(lastActiveIndex, entryCount - 1);
        int word = from >>> 6;
        long mask = active[word] & prefixMask(from & 63);

        while (true) {
            while (mask != 0L) {
                int bit = 63 - Long.numberOfLeadingZeros(mask);
                mask &= ~(1L << bit);

                int idx = (word << 6) + bit;
                if (idx >= entryCount) {
                    continue;
                }

                // Read output bounds
                int base = idx << 2;
                float oMinX = outputBounds[base];
                float oMinY = outputBounds[base + 1];
                float oMaxX = outputBounds[base + 2];
                float oMaxY = outputBounds[base + 3];

                // Overlap test against the current repaint region.
                if (oMaxX < exMinX || oMaxY < exMinY || oMinX > exMaxX || oMinY > exMaxY) {
                    continue;
                }

                // If the output bounds overlap with the repaint region, compute the required input region.
                float iMinX = inputBounds[base];
                float iMinY = inputBounds[base + 1];
                float iMaxX = inputBounds[base + 2];
                float iMaxY = inputBounds[base + 3];
                float reqMinX, reqMinY, reqMaxX, reqMaxY;

                if (local[idx]) {
                    // For locally-bounded backdrops, input pixels can only affect nearby output pixels.
                    // Compute the subset of the output we are repainting:
                    float aMinX = Math.max(exMinX, oMinX);
                    float aMinY = Math.max(exMinY, oMinY);
                    float aMaxX = Math.min(exMaxX, oMaxX);
                    float aMaxY = Math.min(exMaxY, oMaxY);

                    // Per-edge influence extents are implied by input-vs-output bounds.
                    float left = Math.max(0f, oMinX - iMinX);
                    float top = Math.max(0f, oMinY - iMinY);
                    float right = Math.max(0f, iMaxX - oMaxX);
                    float bottom = Math.max(0f, iMaxY - oMaxY);

                    // The required input region is the overlap sub-region expanded by these extents.
                    reqMinX = aMinX - left;
                    reqMinY = aMinY - top;
                    reqMaxX = aMaxX + right;
                    reqMaxY = aMaxY + bottom;

                    if (reqMinX < iMinX) reqMinX = iMinX;
                    if (reqMinY < iMinY) reqMinY = iMinY;
                    if (reqMaxX > iMaxX) reqMaxX = iMaxX;
                    if (reqMaxY > iMaxY) reqMaxY = iMaxY;
                } else {
                    // If the backdrop is not locally bounded, any input pixel can affect every output pixel.
                    reqMinX = iMinX;
                    reqMinY = iMinY;
                    reqMaxX = iMaxX;
                    reqMaxY = iMaxY;
                }

                // Union the required input region into the repaint region.
                if (reqMinX < exMinX) exMinX = reqMinX;
                if (reqMinY < exMinY) exMinY = reqMinY;
                if (reqMaxX > exMaxX) exMaxX = reqMaxX;
                if (reqMaxY > exMaxY) exMaxY = reqMaxY;

                // Consume the backdrop region if its input is not locally bounded, as it will contribute its
                // entire area to the repaint region and will therefore not improve the caller's information
                // on subsequent queries.
                boolean consume = !local[idx];

                // If the backdrop region's input is locally bounded, but the current repaint region fully
                // covers the output, then the required input becomes the full input bounds. This backdrop
                // region can therefore not contribute anything new later, so we can consume it.
                if (!consume) {
                    consume = (exMinX <= oMinX && exMinY <= oMinY && exMaxX >= oMaxX && exMaxY >= oMaxY);
                }

                if (consume) {
                    active[word] &= ~(1L << bit);
                }

                changed = true;
            }

            if (--word < 0) {
                break;
            }

            mask = active[word];
        }

        if (changed) {
            expansion.setBounds(exMinX, exMinY, exMaxX, exMaxY);
        }

        lastActiveIndex = findLastActive(entryCount - 1);
    }

    private void ensureCapacity(int desiredEntries) {
        final int curEntries = outputBounds.length >>> 2;
        if (desiredEntries <= curEntries) {
            return;
        }

        int newEntries = Math.max(desiredEntries, Math.max(16, curEntries << 1));
        outputBounds = Arrays.copyOf(outputBounds, newEntries << 2);
        inputBounds = Arrays.copyOf(inputBounds,  newEntries << 2);
        local = Arrays.copyOf(local, newEntries);

        int neededWords = (newEntries + 63) >>> 6;
        if (active.length < neededWords) {
            active = Arrays.copyOf(active, neededWords);
        }
    }

    private static long prefixMask(int inclusiveBitIndex) {
        return (inclusiveBitIndex == 63) ? -1L : ((1L << (inclusiveBitIndex + 1)) - 1L);
    }

    private int findLastActive(int fromIndex) {
        if (entryCount == 0) {
            return -1;
        }

        int idx = Math.min(fromIndex, entryCount - 1);
        if (idx < 0) {
            return -1;
        }

        int word = idx >>> 6;
        long w = active[word] & prefixMask(idx & 63);

        while (true) {
            if (w != 0L) {
                int bit = 63 - Long.numberOfLeadingZeros(w);
                return (word << 6) + bit;
            }

            if (--word < 0) {
                return -1;
            }

            w = active[word];
        }
    }
}
