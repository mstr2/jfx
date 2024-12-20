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

package com.sun.javafx.application.preferences;

import javafx.util.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Aggregates multiple subsequent sets of changes into a single changeset, and notifies a consumer.
 * Due to its delayed nature, the consumer may not be notified immediately when a changeset arrives.
 */
public final class DelayedChangeAggregator {

    public static final Duration DELAY = Duration.millis(1000);

    private final Executor delayedExecutor;
    private final LongSupplier nanoTimeSupplier;
    private final Consumer<Map<String, Object>> changeConsumer;
    private final Map<String, Object> currentChangeSet;
    private long followUpNanos;
    private int serial;

    public DelayedChangeAggregator(Consumer<Map<String, Object>> changeConsumer,
                                   LongSupplier nanoTimeSupplier,
                                   Executor delayedExecutor) {
        this.changeConsumer = changeConsumer;
        this.nanoTimeSupplier = nanoTimeSupplier;
        this.delayedExecutor = delayedExecutor;
        this.currentChangeSet = new HashMap<>();
    }

    /**
     * Aggregates a new set of changes into the current changeset.
     * <p>
     * If the current changeset is empty and {@code expectMoreChanges} is {@code false}, the new
     * changeset is applied immediately. Otherwise, a follow-up operation is scheduled with the
     * delayed executor.
     *
     * @param changeset the changed mappings
     * @param expectMoreChanges indicates whether the caller expects more changes to come soon
     */
    public synchronized void update(Map<String, Object> changeset, boolean expectMoreChanges) {
        if (expectMoreChanges || !currentChangeSet.isEmpty()) {
            int currentSerial = ++serial;
            currentChangeSet.putAll(changeset);
            followUpNanos = nanoTimeSupplier.getAsLong() + (long)(DELAY.toMillis() * 1000000);
            delayedExecutor.execute(() -> update(currentSerial));
        } else {
            changeConsumer.accept(changeset);
        }
    }

    private synchronized void update(int expectedSerial) {
        if (expectedSerial == serial) {
            if (nanoTimeSupplier.getAsLong() < followUpNanos) {
                delayedExecutor.execute(() -> update(expectedSerial));
            } else {
                changeConsumer.accept(currentChangeSet);
                currentChangeSet.clear();
            }
        }
    }
}
