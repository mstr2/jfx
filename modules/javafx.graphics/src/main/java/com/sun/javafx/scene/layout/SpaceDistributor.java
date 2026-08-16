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

package com.sun.javafx.scene.layout;

public final class SpaceDistributor {

    private SpaceDistributor() {}

    /**
     * Adjusts {@code sizes} towards {@code limits} so that the sum of the first {@code length} entries is as close
     * as possible to {@code span}. Allocations whose limit is not in the required direction are left unchanged.
     * <p>
     * When {@code snapScale} is positive, all calculations are performed in physical pixels. Starting sizes and
     * limits are ceiled to pixel boundaries, while the fixed span is rounded to the nearest pixel. Any pixels left
     * after the even distribution are assigned in array order. A value of zero disables snapping.
     * <p>
     * Both arrays are used as working storage and contain the quantized logical values when this method returns.
     * <p>
     * An infinite {@code span} moves every allocation whose limit lies in the direction of that infinity all the
     * way to its limit. The infinite span is returned as the undistributed remainder.
     *
     * @param span the fixed span available to the allocations; an infinite value
     *             selects the reachable endpoint in that direction
     * @param snapScale the render scale, a non-positive value disables snapping
     * @param sizes current allocations, modified in place
     * @param limits the limit for each allocation, modified in place
     * @param length the number of entries to use
     * @return the part of {@code span} that could not be distributed; zero when
     *         the allocations fit exactly
     */
    public static double distribute(double span, double snapScale, double[] sizes, double[] limits, int length) {
        return snapScale > 0
            ? distributeSnapped(span, snapScale, sizes, limits, length)
            : distributeUnsnapped(span, sizes, limits, length);
    }

    private static double distributeSnapped(double span, double snapScale, double[] sizes, double[] limits, int length) {
        double total = 0;

        // Store physical-pixel counts in the caller-provided arrays while the distribution is in progress.
        // In the normal layout path these counts are well below the integer-precision limit of a double.
        for (int i = 0; i < length; i++) {
            sizes[i] = ceilToPixelCount(sizes[i], snapScale);
            limits[i] = ceilToPixelCount(limits[i], snapScale);
            total += sizes[i];
        }

        if (Double.isInfinite(span)) {
            moveToLimits(sizes, limits, length, span > 0);

            for (int i = 0; i < length; i++) {
                sizes[i] /= snapScale;
                limits[i] /= snapScale;
            }

            return span;
        }

        double remaining = Math.round(span * snapScale) - total;
        if (remaining != 0) {
            boolean growing = remaining > 0;
            int resizable = countResizable(sizes, limits, length, growing);

            while (remaining != 0 && resizable > 0) {
                double idealChange = remaining / resizable;
                boolean constrained = false;

                // Remove allocations that cannot accept an even share. Each iteration either reaches at
                // least one limit or proceeds to the final, bounded distribution below.
                for (int i = 0; i < length; i++) {
                    if (!isResizable(sizes[i], limits[i], growing)) {
                        continue;
                    }

                    double capacity = limits[i] - sizes[i];
                    if (growing ? capacity < idealChange : capacity > idealChange) {
                        sizes[i] = limits[i];
                        remaining -= capacity;
                        resizable--;
                        constrained = true;
                    }
                }

                if (constrained) {
                    continue;
                }

                // First distribute the whole-pixel part of the even share.
                double wholePixelChange = growing
                    ? Math.floor(idealChange)
                    : Math.ceil(idealChange);

                if (wholePixelChange != 0) {
                    for (int i = 0; i < length; i++) {
                        if (isResizable(sizes[i], limits[i], growing)) {
                            sizes[i] += wholePixelChange;
                            remaining -= wholePixelChange;
                        }
                    }
                }

                // The magnitude of the remainder is now smaller than the number of resizable allocations,
                // assign those pixels in a deterministic order.
                double pixel = growing ? 1 : -1;
                for (int i = 0; remaining != 0 && i < length; i++) {
                    if (isResizable(sizes[i], limits[i], growing)) {
                        sizes[i] += pixel;
                        remaining -= pixel;
                    }
                }

                break;
            }
        }

        for (int i = 0; i < length; i++) {
            sizes[i] /= snapScale;
            limits[i] /= snapScale;
        }

        return remaining == 0 ? 0 : remaining / snapScale;
    }

    private static double distributeUnsnapped(double span, double[] sizes, double[] limits, int length) {
        if (Double.isInfinite(span)) {
            moveToLimits(sizes, limits, length, span > 0);
            return span;
        }

        double total = 0;
        for (int i = 0; i < length; i++) {
            total += sizes[i];
        }

        double remaining = span - total;
        if (remaining == 0) {
            return 0;
        }

        boolean growing = remaining > 0;
        int resizable = countResizable(sizes, limits, length, growing);

        while (remaining != 0 && resizable > 0) {
            double idealChange = remaining / resizable;
            boolean constrained = false;

            for (int i = 0; i < length; i++) {
                if (!isResizable(sizes[i], limits[i], growing)) {
                    continue;
                }

                double capacity = limits[i] - sizes[i];
                if (growing ? capacity < idealChange : capacity > idealChange) {
                    sizes[i] = limits[i];
                    remaining -= capacity;
                    resizable--;
                    constrained = true;
                }
            }

            if (constrained) {
                continue;
            }

            // Divide the live remainder successively. Clamp every sum because unsnapped addition can round
            // one ulp past a limit. Any residual that no allocation can accept is returned to the caller.
            int remainingResizable = resizable;
            for (int i = 0; i < length; i++) {
                if (isResizable(sizes[i], limits[i], growing)) {
                    double change = remaining / remainingResizable--;
                    double oldSize = sizes[i];
                    double newSize = oldSize + change;
                    sizes[i] = growing
                        ? Math.min(newSize, limits[i])
                        : Math.max(newSize, limits[i]);

                    remaining -= sizes[i] - oldSize;
                }
            }

            break;
        }

        return remaining;
    }

    private static void moveToLimits(double[] sizes, double[] limits, int length, boolean growing) {
        for (int i = 0; i < length; i++) {
            if (isResizable(sizes[i], limits[i], growing)) {
                sizes[i] = limits[i];
            }
        }
    }

    private static int countResizable(double[] sizes, double[] limits, int length, boolean growing) {
        int count = 0;

        for (int i = 0; i < length; i++) {
            if (isResizable(sizes[i], limits[i], growing)) {
                count++;
            }
        }

        return count;
    }

    private static boolean isResizable(double size, double limit, boolean growing) {
        return growing ? size < limit : size > limit;
    }

    private static double ceilToPixelCount(double value, double snapScale) {
        double scaled = ScaledMath.ceil(value, snapScale) * snapScale;
        if (Double.isInfinite(scaled)) {
            return scaled;
        }

        return Math.rint(scaled);
    }
}
