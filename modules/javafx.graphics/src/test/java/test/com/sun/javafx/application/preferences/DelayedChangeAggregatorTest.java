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

import static com.sun.javafx.application.preferences.DelayedChangeAggregator.DELAY;
import static org.junit.jupiter.api.Assertions.*;

public class DelayedChangeAggregatorTest {

    @Test
    void changeSetWithoutExpectedChanges_isAppliedImmediately() {
        var consumer = new HashMap<String, Object>();
        var aggregator = new DelayedChangeAggregator(consumer::putAll, () -> 0, Runnable::run);
        aggregator.update(Map.of("testKey", "testValue"), false);
        assertEquals(Map.of("testKey", "testValue"), consumer);
    }

    @Test
    void subsequentChangeSetsWithoutExpectedChanges_areAppliedImmediately() {
        var consumer = new HashMap<String, Object>();
        var aggregator = new DelayedChangeAggregator(consumer::putAll, () -> 0, Runnable::run);
        aggregator.update(Map.of("testKey1", "testValue1"), false);
        assertEquals(Map.of("testKey1", "testValue1"), consumer);
        aggregator.update(Map.of("testKey2", "testValue2"), false);
        assertEquals(Map.of("testKey1", "testValue1", "testKey2", "testValue2"), consumer);
    }

    @Test
    void changeSetWithExpectedChanges_isAppliedWithDelay() {
        var executor = new ExecutorImpl();
        var consumer = new HashMap<String, Object>();
        var aggregator = new DelayedChangeAggregator(consumer::putAll, executor, executor);

        aggregator.update(Map.of("testKey", "testValue"), true);
        assertEquals(Map.of(), consumer);

        // Advance the time half-way through the delay period.
        executor.setTime(DELAY.divide(2));
        assertEquals(Map.of(), consumer);

        // Advance the time to a millisecond before the end of the delay period.
        executor.setTime(DELAY.subtract(Duration.millis(1)));
        assertEquals(Map.of(), consumer);

        // When the delay period has elapsed, the change is applied.
        executor.setTime(DELAY);
        assertEquals(Map.of("testKey", "testValue"), consumer);
    }

    @Test
    void changeSetWithoutExpectedChange_isAppliedWithDelay_ifLastChangeSetWasAppliedWithDelay() {
        var executor = new ExecutorImpl();
        var consumer = new HashMap<String, Object>();
        var aggregator = new DelayedChangeAggregator(consumer::putAll, executor, executor);

        aggregator.update(Map.of("testKey1", "testValue1"), true);
        assertEquals(Map.of(), consumer);

        // Advance the time half-way through the delay period.
        executor.setTime(DELAY.divide(2));
        assertEquals(Map.of(), consumer);

        // The new changeset is applied with delay (even though we expect no more changes) because
        // the first changeset was applied with delay.
        aggregator.update(Map.of("testKey2", "testValue2"), false);
        assertEquals(Map.of(), consumer);

        // Advance to the end of the first delay period. No change is applied because the delay
        // period was extended by the second changeset.
        executor.setTime(DELAY);
        assertEquals(Map.of(), consumer);

        // When the second delay period has elapsed, the changes are applied.
        executor.setTime(DELAY.add(DELAY.divide(2)));
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
