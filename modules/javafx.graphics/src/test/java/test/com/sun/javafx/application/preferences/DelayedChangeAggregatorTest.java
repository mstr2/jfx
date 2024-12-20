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

package test.com.sun.javafx.application.preferences;

import com.sun.javafx.application.preferences.DelayedChangeAggregator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import javafx.util.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DelayedChangeAggregatorTest {

    static final Duration SHORT_DELAY = Duration.millis(100);
    static final Duration LONG_DELAY = Duration.millis(1000);

    @Test
    void changeSetIsAppliedWithShortDelay() {
        var executor = new ExecutorImpl();
        var consumer = new HashMap<String, Object>();
        var aggregator = new DelayedChangeAggregator(consumer::putAll, executor, executor);

        aggregator.update(Map.of("testKey", "testValue"), (int)SHORT_DELAY.toMillis());
        assertEquals(Map.of(), consumer);

        executor.setTime(SHORT_DELAY);
        assertEquals(Map.of("testKey", "testValue"), consumer);
    }

    @Test
    void subsequentChangeSetsAreAppliedWithShortDelay() {
        var executor = new ExecutorImpl();
        var consumer = new HashMap<String, Object>();
        var aggregator = new DelayedChangeAggregator(consumer::putAll, executor, executor);

        aggregator.update(Map.of("testKey1", "testValue1"), (int)SHORT_DELAY.toMillis());
        assertEquals(Map.of(), consumer);

        executor.setTime(SHORT_DELAY.multiply(0.5));
        aggregator.update(Map.of("testKey2", "testValue2"), (int)SHORT_DELAY.toMillis());
        assertEquals(Map.of(), consumer);

        executor.setTime(SHORT_DELAY.multiply(1.5));
        assertEquals(Map.of("testKey1", "testValue1", "testKey2", "testValue2"), consumer);
    }

    @Test
    void changeSetIsAppliedWithLongDelay() {
        var executor = new ExecutorImpl();
        var consumer = new HashMap<String, Object>();
        var aggregator = new DelayedChangeAggregator(consumer::putAll, executor, executor);

        aggregator.update(Map.of("testKey", "testValue"), (int)LONG_DELAY.toMillis());
        assertEquals(Map.of(), consumer);

        // Advance the time half-way through the delay period.
        executor.setTime(LONG_DELAY.divide(2));
        assertEquals(Map.of(), consumer);

        // Advance the time to a millisecond before the end of the delay period.
        executor.setTime(LONG_DELAY.subtract(Duration.millis(1)));
        assertEquals(Map.of(), consumer);

        // When the delay period has elapsed, the change is applied.
        executor.setTime(LONG_DELAY);
        assertEquals(Map.of("testKey", "testValue"), consumer);
    }

    @Test
    void changeSetWithShortDelayWaitsForLastChangeSetWithLongDelay() {
        var executor = new ExecutorImpl();
        var consumer = new HashMap<String, Object>();
        var aggregator = new DelayedChangeAggregator(consumer::putAll, executor, executor);

        aggregator.update(Map.of("testKey1", "testValue1"), (int)LONG_DELAY.toMillis());
        assertEquals(Map.of(), consumer);

        // Advance the time half-way through the delay period.
        executor.setTime(LONG_DELAY.divide(2));
        assertEquals(Map.of(), consumer);

        // The new changeset waits for the current changeset's delay period to elapse.
        aggregator.update(Map.of("testKey2", "testValue2"), (int)SHORT_DELAY.toMillis());
        assertEquals(Map.of(), consumer);

        // Advance to the end of the first delay period. Both changesets are applied.
        executor.setTime(LONG_DELAY);
        assertEquals(Map.of("testKey1", "testValue1", "testKey2", "testValue2"), consumer);
    }

    private static class ExecutorImpl implements Executor, LongSupplier {
        final List<Runnable> commands = new ArrayList<>();
        long nanos;

        @Override
        public void execute(Runnable command) {
            commands.add(command);
        }

        void run() {
            var copy = List.copyOf(commands);
            commands.clear();
            copy.forEach(Runnable::run);
        }

        @Override
        public long getAsLong() {
            return nanos;
        }

        void setTime(Duration time) {
            nanos = (long)(time.toMillis() * 1000000);
            run();
        }
    }
}
