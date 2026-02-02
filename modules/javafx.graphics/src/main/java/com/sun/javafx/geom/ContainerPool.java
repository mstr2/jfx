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

import java.util.Deque;
import java.util.LinkedList;
import java.util.Objects;
import java.util.function.Supplier;

public sealed class ContainerPool<T extends Container> permits DirtyRegionPool, BackdropRegionPool {

    private static final class PoolItem<T extends Container> {
        T container;
        long timeStamp;

        public PoolItem(T container, long timeStamp) {
            this.container = container;
            this.timeStamp = timeStamp;
        }
    }

    private static final int POOL_SIZE_MIN = 4;
    private static final int EXPIRATION_TIME = 3000;
    private static final int COUNT_BETWEEN_EXPIRATION_CHECK = 30 * EXPIRATION_TIME / 1000;

    private int clearCounter = COUNT_BETWEEN_EXPIRATION_CHECK;
    private final Deque<T> fixed;
    private final Deque<PoolItem<T>> unlocked;
    private final Deque<PoolItem<T>> locked;
    private final Supplier<T> itemFactory;

    public ContainerPool(Supplier<T> itemFactory) {
        this.itemFactory = Objects.requireNonNull(itemFactory, "itemFactory must not be null");
        fixed = new LinkedList<>();
        unlocked = new LinkedList<>();
        locked = new LinkedList<>();
        for (int i = 0; i < POOL_SIZE_MIN; ++i) {
            fixed.add(itemFactory.get());
        }
    }

    public T checkOut() {
        clearExpired();

        if (!fixed.isEmpty()) {
            return fixed.pop();
        }

        if (!unlocked.isEmpty()) {
            PoolItem<T> item = unlocked.pop();
            locked.push(item);
            return item.container;
        }

        locked.push(new PoolItem<>(null, -1));
        return itemFactory.get();
    }

    public void checkIn(T drc) {
        drc.reset();

        if (locked.isEmpty()) {
            fixed.push(drc);
        } else {
            PoolItem<T> item = locked.pop();
            item.container = drc;
            item.timeStamp = System.currentTimeMillis();
            unlocked.push(item);
        }
    }

    private void clearExpired() {
        if (unlocked.isEmpty()) {
            return;
        }

        if (clearCounter-- == 0) {
            clearCounter = COUNT_BETWEEN_EXPIRATION_CHECK;
            PoolItem<T> i = unlocked.peekLast();
            long now = System.currentTimeMillis();

            while (i != null && i.timeStamp + EXPIRATION_TIME < now) {
                unlocked.removeLast();
                i = unlocked.peekLast();
            }
        }
    }
}
