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

package test.com.sun.javafx.scene.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.javafx.scene.layout.ScaledMath;
import com.sun.javafx.scene.layout.SpaceDistributor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SpaceDistributorTest {

    private static final double[] SCALES = { 1, 1.25, 1.5, 1.75, 2, 4.0 / 3.0 };

    @Test
    void distributesLeftoverPixelsInArrayOrder() {
        double[] sizes = { 1, 1, 1 };
        double[] limits = { 10, 10, 10 };
        double remaining = SpaceDistributor.distribute(5, 1, sizes, limits, sizes.length);

        assertEquals(0, remaining);
        assertArrayEquals(new double[] { 2, 2, 1 }, sizes);
    }

    @Test
    void roundsTheFixedSpanLikeLayoutSpace() {
        double[] sizes = { 0 };
        double[] limits = { 10 };
        double remaining = SpaceDistributor.distribute(0.5, 1, sizes, limits, sizes.length);

        assertEquals(0, remaining);
        assertArrayEquals(new double[] { 1 }, sizes);
    }

    @Test
    void fitsFractionalPreferredSizesAtDifferentRenderScales() {
        for (double scale : SCALES) {
            double[] sizes = { 25.3, 25.3, 25.4 };
            double[] limits = { 0, 0, 0 };
            double remaining = SpaceDistributor.distribute(76, scale, sizes, limits, sizes.length);

            assertEquals(0, remaining, 0, "scale=" + scale);
            assertEquals(Math.rint(76 * scale), pixelSum(sizes, scale), 0, "scale=" + scale);
            for (double size : sizes) {
                assertEquals(Math.rint(size * scale), size * scale, 1e-12, "scale=" + scale);
            }
        }
    }

    @Test
    void respectsGrowthLimits() {
        double[] sizes = { 2, 5, 4 };
        double[] limits = { 3, 100, 6 };
        double remaining = SpaceDistributor.distribute(16, 1, sizes, limits, sizes.length);

        assertEquals(0, remaining);
        assertArrayEquals(new double[] { 3, 7, 6 }, sizes);
    }

    @Test
    void respectsShrinkLimits() {
        double[] sizes = { 8, 7, 6 };
        double[] limits = { 7, 1, 5 };
        double remaining = SpaceDistributor.distribute(15, 1, sizes, limits, sizes.length);

        assertEquals(0, remaining);
        assertArrayEquals(new double[] { 7, 3, 5 }, sizes);
    }

    @Test
    void returnsSpaceThatCannotBeDistributed() {
        double[] sizes = { 1, 2 };
        double[] limits = { 2, 4 };
        double remaining = SpaceDistributor.distribute(20, 1, sizes, limits, sizes.length);

        assertEquals(14, remaining);
        assertArrayEquals(new double[] { 2, 4 }, sizes);
    }

    @Test
    void infiniteSpanMovesAllocationsToDirectionalEndpoints() {
        double[] growSizes = { 1, 10, 5 };
        double[] growLimits = {2 , 9, 7 };
        double growRemaining = SpaceDistributor.distribute(
            Double.POSITIVE_INFINITY, 0, growSizes, growLimits, growSizes.length);

        assertEquals(Double.POSITIVE_INFINITY, growRemaining);
        assertArrayEquals(new double[] { 2, 10, 7 }, growSizes);

        double[] shrinkSizes = { 1, 10, 5 };
        double[] shrinkLimits = { 2, 9, 3 };
        double shrinkRemaining = SpaceDistributor.distribute(
            Double.NEGATIVE_INFINITY, 0, shrinkSizes, shrinkLimits, shrinkSizes.length);

        assertEquals(Double.NEGATIVE_INFINITY, shrinkRemaining);
        assertArrayEquals(new double[] { 1, 9, 3 }, shrinkSizes);
    }

    @Test
    void preservesFractionalValuesWhenSnappingIsDisabled() {
        double[] sizes = { 1.1, 2.2, 3.3 };
        double[] limits = { 10, 10, 10 };
        double remaining = SpaceDistributor.distribute(10, 0, sizes, limits, sizes.length);

        assertEquals(0, remaining, 1e-14);
        assertEquals(10, sum(sizes), 1e-14);
        assertTrue(sizes[0] % 1 != 0);
        assertTrue(sizes[1] % 1 != 0);
        assertTrue(sizes[2] % 1 != 0);
    }

    @Test
    void unsnappedArithmeticDoesNotCrossLimitsByOneUlp() {
        double third = 1.0 / 3.0;
        double[] growSizes = { 0, 0, 0 };
        double[] growLimits = {third, third, third};
        double growRemaining = SpaceDistributor.distribute(1, 0, growSizes, growLimits, growSizes.length);

        assertArrayEquals(growLimits, growSizes);
        assertTrue(growRemaining >= 0 && growRemaining <= Math.ulp(1));

        double[] shrinkSizes = {third, third, third};
        double[] shrinkLimits = { 0, 0, 0 };
        double shrinkRemaining = SpaceDistributor.distribute(0, 0, shrinkSizes, shrinkLimits, shrinkSizes.length);

        assertArrayEquals(shrinkLimits, shrinkSizes);
        assertTrue(shrinkRemaining <= 0 && shrinkRemaining >= -Math.ulp(1));
    }

    @Test
    void onlyUsesTheRequestedArrayPrefix() {
        double[] sizes = { 1, 1, 1, 123 };
        double[] limits = { 10, 10, 10, 456 };
        double remaining = SpaceDistributor.distribute(5, 1, sizes, limits, 3);

        assertEquals(0, remaining);
        assertArrayEquals(new double[] { 2, 2, 1, 123 }, sizes);
        assertEquals(456, limits[3]);
    }

    @Test
    void randomizedDistributionsFitInThePixelDomain() {
        Random random = new Random(0x5EED);

        for (double scale : SCALES) {
            for (int iteration = 0; iteration < 500; iteration++) {
                int length = 1 + random.nextInt(12);
                double[] sizes = new double[length];
                double[] limits = new double[length];
                boolean growing = random.nextBoolean();
                long currentPixels = 0;
                long limitPixels = 0;

                for (int i = 0; i < length; i++) {
                    double low = random.nextDouble(20);
                    double high = low + random.nextDouble(20);
                    sizes[i] = growing ? low : high;
                    limits[i] = growing ? high : low;
                    currentPixels += ceilPixelCount(sizes[i], scale);
                    limitPixels += ceilPixelCount(limits[i], scale);
                }

                long distance = Math.abs(limitPixels - currentPixels);
                long targetPixels = currentPixels;
                if (distance > 0) {
                    long offset = random.nextLong(distance + 1);
                    targetPixels += growing ? offset : -offset;
                }

                double remaining = SpaceDistributor.distribute(targetPixels / scale, scale, sizes, limits, length);

                assertEquals(0, remaining, 0, "scale=" + scale + ", iteration=" + iteration);
                assertEquals(targetPixels, pixelSum(sizes, scale), 0, "scale=" + scale + ", iteration=" + iteration);

                for (int i = 0; i < length; i++) {
                    double sizePixels = Math.rint(sizes[i] * scale);
                    double limit = limits[i] * scale;
                    assertEquals(sizePixels, sizes[i] * scale, 1e-12);
                    assertTrue(growing ? sizePixels <= limit + 1e-12 : sizePixels >= limit - 1e-12);
                }
            }
        }
    }

    private static long ceilPixelCount(double value, double scale) {
        return (long)Math.rint(ScaledMath.ceil(value, scale) * scale);
    }

    private static double pixelSum(double[] values, double scale) {
        double sum = 0;
        for (double value : values) {
            sum += Math.rint(value * scale);
        }
        return sum;
    }

    private static double sum(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum;
    }

    @Test
    void shouldGrowSpaceDeterministicallyAndExactlyWhenUnscaled() {
        assertGrow(
            1.0,
            "A.[.]B.....[......]C....[..]",
            new Expected(15, "A--B------C-----"),
            new Expected(16, "A--B------C-----"),
            new Expected(17, "A---B------C-----"),  // deterministic, A receives first new space
            new Expected(18, "A---B-------C-----"), // B receives second new space, etc.
            new Expected(19, "A---B-------C------"),
            new Expected(20, "A----B-------C------"),
            new Expected(21, "A----B--------C------"),
            new Expected(22, "A----B--------C-------"),
            new Expected(23, "A----B---------C-------"),
            new Expected(24, "A----B---------C--------"),
            new Expected(25, "A----B----------C--------"),
            new Expected(26, "A----B-----------C--------"),
            new Expected(27, "A----B------------C--------"),
            new Expected(28, "A----B-------------C--------"),
            new Expected(29, "A----B-------------C--------")
        );

        assertGrow(
            1.0,
            "A...]B.....[......]C....[..]",
            new Expected(13, "B------C-----"),
            new Expected(14, "AB------C-----")
        );
    }

    @Test
    void shouldShrinkSpaceDeterministicallyAndExactlyWhenUnscaled() {
        assertShrink(
            1.0,
            "A.[.]B.....[......]C....[..]",
            new Expected(29, "A----B-------------C--------"),
            new Expected(28, "A----B-------------C--------"),
            new Expected(27, "A---B-------------C--------"),  // A first
            new Expected(26, "A---B------------C--------"),   // then B
            new Expected(25, "A---B------------C-------"),    // then C
            new Expected(24, "A--B------------C-------"),     // then A again
            new Expected(23, "A--B-----------C-------"),
            new Expected(22, "A--B-----------C------"),
            new Expected(21, "A--B----------C------"),
            new Expected(20, "A--B----------C-----"),
            new Expected(19, "A--B---------C-----"),
            new Expected(18, "A--B--------C-----"),
            new Expected(17, "A--B-------C-----"),
            new Expected(16, "A--B------C-----"),
            new Expected(15, "A--B------C-----")
        );

        assertShrink(
            1.0,
            "A...]B.....[......]C....[..]",
            new Expected(16, "B---------C-----"),
            new Expected(17, "AB---------C-----")
        );
    }

    @Test
    void shouldGrowSpaceDeterministicallyAndExactlyWhenScaled200Percent() {
        assertGrow(
            2.0,
            "A.[.]B.....[......]C....[..]",
            new Expected(15.0, "A-----B-------------C-----------"),
            new Expected(15.5, "A-----B-------------C-----------"),
            new Expected(16.0, "A-----B-------------C-----------"),
            new Expected(16.5, "A------B-------------C-----------"), // deterministic, A receives first new space
            new Expected(17.0, "A------B--------------C-----------"), // B receives second new space, etc.
            new Expected(17.5, "A------B--------------C------------"),
            new Expected(18.0, "A-------B--------------C------------"),
            new Expected(18.5, "A-------B---------------C------------"),
            new Expected(19.0, "A-------B---------------C-------------"),
            new Expected(19.5, "A--------B---------------C-------------"),
            new Expected(20.0, "A--------B----------------C-------------"),
            new Expected(21.0, "A---------B----------------C--------------"),
            new Expected(22.0, "A---------B-----------------C---------------"),
            new Expected(23.0, "A---------B------------------C----------------"),
            new Expected(24.0, "A---------B-------------------C-----------------"),
            new Expected(25.0, "A---------B---------------------C-----------------"),
            new Expected(26.0, "A---------B-----------------------C-----------------"),
            new Expected(27.0, "A---------B-------------------------C-----------------"),
            new Expected(27.5, "A---------B--------------------------C-----------------"),
            new Expected(28.0, "A---------B---------------------------C-----------------"),
            new Expected(28.5, "A---------B---------------------------C-----------------"),
            new Expected(29.0, "A---------B---------------------------C-----------------")
        );

        assertGrow(
            2.0,
            "A...]B.....[......]C....[..]",
            new Expected(13.0, "B-------------C-----------"),
            new Expected(13.5, "AB-------------C-----------"),
            new Expected(14.0, "AB--------------C-----------")
        );
    }

    @Test
    void shouldShrinkSpaceDeterministicallyAndExactlyWhenScaled200Percent() {
        assertShrink(
            2.0,
            "A.[.]B.....[......]C....[..]",
            new Expected(29.0, "A---------B---------------------------C-----------------"),
            new Expected(28.5, "A---------B---------------------------C-----------------"),
            new Expected(28.0, "A---------B---------------------------C-----------------"),
            new Expected(27.5, "A--------B---------------------------C-----------------"),  // A shrinks first
            new Expected(27.0, "A--------B--------------------------C-----------------"),   // then B
            new Expected(26.5, "A--------B--------------------------C----------------"),    // then C
            new Expected(26.0, "A-------B--------------------------C----------------"),
            new Expected(25.5, "A-------B-------------------------C----------------"),
            new Expected(25.0, "A-------B-------------------------C---------------"),
            new Expected(24.5, "A------B-------------------------C---------------"),
            new Expected(24.0, "A------B------------------------C---------------"),
            new Expected(23.5, "A------B------------------------C--------------"),
            new Expected(23.0, "A-----B------------------------C--------------"),
            new Expected(22.5, "A-----B-----------------------C--------------"),
            new Expected(22.0, "A-----B-----------------------C-------------"),
            new Expected(21.5, "A-----B----------------------C-------------"),  // A reached minimum, B shrinks first
            new Expected(21.0, "A-----B----------------------C------------"),
            new Expected(20.0, "A-----B---------------------C-----------"),  // C reached minimum
            new Expected(19.0, "A-----B-------------------C-----------"),
            new Expected(18.0, "A-----B-----------------C-----------"),
            new Expected(17.0, "A-----B---------------C-----------"),
            new Expected(16.5, "A-----B--------------C-----------"),
            new Expected(16.0, "A-----B-------------C-----------"),  // B reached minimum
            new Expected(15.5, "A-----B-------------C-----------"),
            new Expected(15.0, "A-----B-------------C-----------")
        );

        assertShrink(
            2.0,
            "A...]B.....[......]C....[..]",
            new Expected(18.0, "A--B--------------------C-----------"),
            new Expected(16.0, "AB------------------C-----------"),
            new Expected(15.5, "B------------------C-----------"),  // A disappeared
            new Expected(15.0, "B-----------------C-----------")
        );
    }

    @Test
    void shouldGrowSpaceDeterministicallyAndExactlyWhenScaled() {
        assertGrow(
            1.5,
            "A.[.]B.....[......]C....[..]",
            new Expected( 0.00, "A----B----------C--------"),
            new Expected(15.33, "A----B----------C--------"),
            new Expected(16.00, "A----B----------C--------"),
            new Expected(16.66, "A----B----------C--------"), // minimum
            new Expected(17.33, "A-----B----------C--------"), // A first to grow
            new Expected(18.00, "A-----B-----------C--------"), // then B
            new Expected(18.66, "A-----B-----------C---------"), // then C, etc..
            new Expected(19.33, "A------B-----------C---------"), // A
            new Expected(20.00, "A------B------------C---------"), // B
            new Expected(20.66, "A------B------------C----------"), // C
            new Expected(21.33, "A-------B------------C----------"), // A
            new Expected(22.00, "A-------B-------------C----------"), // B
            new Expected(22.66, "A-------B-------------C-----------"), // C
            new Expected(23.33, "A-------B--------------C-----------"), // B because A reached maximum
            new Expected(24.00, "A-------B--------------C------------"), // C
            new Expected(24.66, "A-------B---------------C------------"), // B
            new Expected(25.33, "A-------B---------------C-------------"), // C
            new Expected(26.00, "A-------B----------------C-------------"), // B
            new Expected(26.66, "A-------B-----------------C-------------"), // B
            new Expected(27.33, "A-------B------------------C-------------"), // B
            new Expected(28.00, "A-------B-------------------C-------------"), // B
            new Expected(28.66, "A-------B--------------------C-------------"), // maximum
            new Expected(29.33, "A-------B--------------------C-------------"),
            new Expected(Double.MAX_VALUE, "A-------B--------------------C-------------"),
            new Expected(Double.POSITIVE_INFINITY, "A-------B--------------------C-------------")
        );
    }

    @Test
    void shouldShrinkSpaceDeterministicallyAndExactlyWhenScaled() {
        assertShrink(
            1.5,
            "A.[.]B.....[......]C....[..]",  // sizes specified in unscaled values: 28 preferred, 13 minimum
            // Preferred sizes : 5.0,  14.0, 9.0
            // Rounds to       : 5.33, 14.0, 9.33 -> to physical pixels * 1.5 -> 8, 21, 14 (43)
            // Minimum sizes   : 3.0,  7.0,  6.0
            // Rounds to       : 3.33, 7.33, 6.0  -> to physical pixels * 1.5 -> 5, 11, 9
            new Expected(Double.POSITIVE_INFINITY, "A-------B--------------------C-------------"),
            new Expected(Double.MAX_VALUE, "A-------B--------------------C-------------"),
            new Expected(29.33, "A-------B--------------------C-------------"),
            new Expected(28.66, "A-------B--------------------C-------------"), // maximum (8, 21, 14 pixels)
            new Expected(28.00, "A------B--------------------C-------------"),  // A shrinks first
            new Expected(27.33, "A------B-------------------C-------------"),   // then B
            new Expected(26.66, "A------B-------------------C------------"),    // then C
            new Expected(26.00, "A-----B-------------------C------------"),
            new Expected(25.33, "A-----B------------------C------------"),
            new Expected(24.66, "A-----B------------------C-----------"),
            new Expected(24.00, "A----B------------------C-----------"),
            new Expected(23.33, "A----B-----------------C-----------"),
            new Expected(22.66, "A----B-----------------C----------"),
            new Expected(22.00, "A----B----------------C----------"),  // A reached minimum, so B shrinks first
            new Expected(21.33, "A----B----------------C---------"),   // then C
            new Expected(20.66, "A----B---------------C---------"),
            new Expected(20.00, "A----B---------------C--------"),
            new Expected(19.33, "A----B--------------C--------"),  // C reached minimum
            new Expected(18.66, "A----B-------------C--------"),
            new Expected(18.00, "A----B------------C--------"),
            new Expected(17.33, "A----B-----------C--------"),
            new Expected(16.66, "A----B----------C--------"),  // minimum (5, 11, 9 pixels)
            new Expected(16.00, "A----B----------C--------"),
            new Expected(15.33, "A----B----------C--------")
        );
    }

    @Test
    void shouldDistributeSpaceScaled() {
        assertArrayEquals(
            new double[] { 10.000 },
            distribute(10, 1.5, new double[] { 0 }, new double[] { 100 }),
            0.001
        );

        assertArrayEquals(
            new double[] { 11.333 },
            distribute(11, 1.5, new double[] { 0 }, new double[] { 100 }),
            0.001
        );
    }

    @Test
    void shouldDistributeSpaceCorrectlyUnscaled() {
        // Cases with a single child:
        assertArrayEquals(
            new double[] { 10.0 },
            distribute(10, 0, new double[] { 0 }, new double[] { 100 }),
            0.001
        );

        assertArrayEquals(
            new double[] { 11.0 },
            distribute(11, 0, new double[] { 0 }, new double[] { 100 }),
            0.001
        );

        // Cases with two children:
        assertArrayEquals(
            new double[] { 5.0, 5.0 },
            distribute(10, 0, new double[] { 0, 0 }, new double[] {100 , 100 }),
            0.001
        );

        assertArrayEquals(
            new double[] { 5.5, 5.5 },
            distribute(11, 0, new double[] { 0, 0 }, new double[] {100 , 100 }),
            0.001
        );
    }

    private static final Pattern PATTERN = Pattern.compile(".*?[\\|\\]]");

    /**
     * Given an input which represents a number of children, verifies they
     * have matching final sizes for each element in the expected array.<p>
     *
     * The input string has a format that represents both the number of
     * children and their respective minimum and maximum sizes. A child is
     * represented by a range of characters terminated by either a pipe or
     * square closing bracket. Characters other than the square brackets or pipe
     * have no significance, and whatever is visually pleasing can be used.<p>
     *
     * A single child can be specified as follows:
     *
     * <table>
     * <tr><th>Input</th><th>Minimum size</th><th>Maximum size</th></tr>
     * <tr><td><pre>....]</pre></td><td align="center">0</td><td align="center">5</td></tr>
     * <tr><td><pre>......]</pre></td><td align="center">0</td><td align="center">7</td></tr>
     * <tr><td><pre>[.....]</pre></td><td align="center">1</td><td align="center">7</td></tr>
     * <tr><td><pre>.[....]</pre></td><td align="center">2</td><td align="center">7</td></tr>
     * <tr><td><pre>....[.]</pre></td><td align="center">5</td><td align="center">7</td></tr>
     * <tr><td><pre>......|</pre></td><td align="center">7</td><td align="center">7</td></tr>
     * <tr><td><pre>|</pre></td><td align="center">1</td><td align="center">1</td></tr>
     * <tr><td><pre>[]</pre></td><td align="center">1</td><td align="center">2</td></tr>
     * </table>
     *
     * To specify multiple children, simply concatenate them:
     *
     * <table>
     * <tr><th>Input</th><th>Number of children</th><th>Description</th></tr>
     * <tr><td><pre>....]....[.]</pre></td><td align="center">2</td><td>Child 1 has a maximum of 5, and Child 2 has a minimum of 5 and maximum of 7</td></tr>
     * <tr><td><pre>|||</pre></td><td align="center">3</td><td>Three children all with a minimum and maximum size of 1</td></tr>
     * </table>
     *
     * Note that the expected values must take the pixel scale into account. In other words,
     * if pixel scale is 2, the expected values are twice as long.
     *
     * @param pixelScale a size multiplier
     * @param input a string representing the number of children and their minimum and maximum sizes, cannot be {@code null}
     * @param grow whether to shrink or grow
     * @param expecteds expected sizes for all children for different available space sizes
     */
    private void assertSizes(double pixelScale, String input, boolean grow, Expected... expecteds) {
        Matcher matcher = PATTERN.matcher(input);
        List<Double> minimumSizes = new ArrayList<>();
        List<Double> maximumSizes = new ArrayList<>();

        while (matcher.find()) {
            String match = matcher.group();
            double max = match.length();
            double min = match.indexOf("[") + 1;
            boolean fixedSize = match.endsWith("|");

            minimumSizes.add(fixedSize ? max : min == -1 ? 0 : min);
            maximumSizes.add(max);
        }

        for (Expected expected : expecteds) {
            double[] sizes = (grow ? minimumSizes : maximumSizes).stream()
                .mapToDouble(Double::doubleValue)
                .toArray();

            double[] limits = (grow ? maximumSizes : minimumSizes).stream()
                .mapToDouble(Double::doubleValue)
                .toArray();

            SpaceDistributor.distribute(expected.space, pixelScale, sizes, limits, sizes.length);

            String result = "";
            char c = 'A';

            for (double space : sizes) {
                int count = (int) Math.round(space * pixelScale);

                if (count > 0) {
                    result += ("" + c) + "-".repeat(count - 1);
                }

                c++;
            }

            assertEquals(
                expected.expected,
                result, "for " + expected.space + "\n" + expected.expected + " <- expected\n" + result + " <- got\n");
        }
    }

    private static double[] distribute(double span, double snapScale, double[] sizes, double[] limits) {
        SpaceDistributor.distribute(span, snapScale, sizes, limits, sizes.length);
        return sizes;
    }

    private void assertGrow(double pixelScale, String input, Expected... expecteds) {
        assertSizes(pixelScale, input, true, expecteds);
    }

    private void assertShrink(double pixelScale, String input, Expected... expecteds) {
        assertSizes(pixelScale, input, false, expecteds);
    }

    record Expected(double space, String expected) {}
}
