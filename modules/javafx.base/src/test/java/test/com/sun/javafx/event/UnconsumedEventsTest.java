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

package test.com.sun.javafx.event;

import com.sun.javafx.event.EventHandlerManager;
import com.sun.javafx.event.EventUtil;
import javafx.event.Event;
import javafx.event.EventDispatchChain;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UnconsumedEventsTest {

    private List<String> trace;
    private EventTargetImpl target0;
    private EventTargetImpl target1;
    private EventTargetImpl target2;

    @BeforeEach
    void setup() {
        trace = new ArrayList<>();
        target0 = new EventTargetImpl("target0", null, trace);
        target1 = new EventTargetImpl("target1", target0, trace);
        target2 = new EventTargetImpl("target2", target1, trace);
    }

    @Test
    void unconsumedEventHandlerIsCalledAtEndOfDelivery() {
        target2.handlerManager.addEventFilter(EmptyEvent.ANY, e -> {
            e.ifUnconsumed(_ -> trace.add("unconsumed:" + e.getSource()));
        });

        EventUtil.fireEvent(target2, new EmptyEvent());

        assertEquals(
            List.of(
                "filter:target0",
                "filter:target1",
                "filter:target2",
                "handler:target2",
                "handler:target1",
                "handler:target0",
                "unconsumed:target2"),
            trace);
    }

    @Test
    void multipleUnconsumedEventHandlersAreCalledInSequence() {
        EventHandler<Event> unconsumedHandler = e -> trace.add("unconsumed:" + e.getSource());
        target0.handlerManager.addEventFilter(EmptyEvent.ANY, e -> e.ifUnconsumed(unconsumedHandler));
        target1.handlerManager.addEventFilter(EmptyEvent.ANY, e -> e.ifUnconsumed(unconsumedHandler));
        target2.handlerManager.addEventFilter(EmptyEvent.ANY, e -> e.ifUnconsumed(unconsumedHandler));

        EventUtil.fireEvent(target2, new EmptyEvent());

        assertEquals(
            List.of(
                "filter:target0",
                "filter:target1",
                "filter:target2",
                "handler:target2",
                "handler:target1",
                "handler:target0",
                "unconsumed:target0",
                "unconsumed:target1",
                "unconsumed:target2"),
            trace);
    }

    @Test
    void consumingAnUnconsumedEventStopsFurtherPropagation() {
        EventHandler<Event> unconsumedHandler = e -> trace.add("unconsumed:" + e.getSource());

        // The first unconsumed event handler in the chain is called.
        target0.handlerManager.addEventFilter(EmptyEvent.ANY, e -> e.ifUnconsumed(unconsumedHandler));

        // The next unconsumed handler in the chain consumes the event.
        target1.handlerManager.addEventFilter(EmptyEvent.ANY, e -> e.ifUnconsumed(Event::consume));

        // The last unconsumed event handler will not be called, as the event was consumed.
        target2.handlerManager.addEventFilter(EmptyEvent.ANY, e -> e.ifUnconsumed(unconsumedHandler));

        EventUtil.fireEvent(target2, new EmptyEvent());

        assertEquals(
            List.of(
                "filter:target0",
                "filter:target1",
                "filter:target2",
                "handler:target2",
                "handler:target1",
                "handler:target0",
                "unconsumed:target0"
                // "unconsumed:target1" <-- no trace output
                // "unconsumed:target2" <-- not called
            ), trace);
    }

    @Test
    void cannotAddUnconsumedHandlerAfterDeliveryIsComplete() {
        target2.handlerManager.addEventFilter(EmptyEvent.ANY, e -> {
            e.ifUnconsumed(e2 -> {
                // This call will fail with IllegalStateException:
                e2.ifUnconsumed(_ -> {});
            });
        });

        assertThrows(IllegalStateException.class, () -> EventUtil.fireEvent(target2, new EmptyEvent()));
    }

    @Test
    void discardUnconsumedHandlers() {
        EventHandler<Event> unconsumedHandler = e -> trace.add("unconsumed:" + e.getSource());

        // Register an unconsumed event handler.
        target0.handlerManager.addEventFilter(EmptyEvent.ANY, e -> e.ifUnconsumed(unconsumedHandler));

        // After the unconsumed event handler was registered, the next handler in the chain discards it.
        target1.handlerManager.addEventFilter(EmptyEvent.ANY, Event::discardUnconsumedEventHandlers);

        EventUtil.fireEvent(target2, new EmptyEvent());

        assertEquals(
            List.of(
                "filter:target0",
                "filter:target1",
                "filter:target2",
                "handler:target2",
                "handler:target1",
                "handler:target0"),
            trace);
    }

    @Test
    void addUnconsumedEventHandlerAfterDiscarding() {
        EventHandler<Event> unconsumedHandler = e -> trace.add("unconsumed:" + e.getSource());

        // Register an unconsumed event handler.
        target2.handlerManager.addEventHandler(EmptyEvent.ANY, e -> e.ifUnconsumed(unconsumedHandler));

        // After the unconsumed event handler was registered, the next handler in the chain discards it
        // and adds a new unconsumed event handler. The new handler is now the only handler.
        target1.handlerManager.addEventHandler(EmptyEvent.ANY, e -> {
            e.discardUnconsumedEventHandlers();
            e.ifUnconsumed(unconsumedHandler);
        });

        EventUtil.fireEvent(target2, new EmptyEvent());

        assertEquals(
            List.of(
                "filter:target0",
                "filter:target1",
                "filter:target2",
                "handler:target2",
                "handler:target1",
                "handler:target0",
                "unconsumed:target1"),
            trace);
    }

    private static class EventTargetImpl implements EventTarget {
        final String name;
        final EventTargetImpl parentTarget;
        final EventHandlerManager handlerManager = new EventHandlerManager(this);

        EventTargetImpl(String name, EventTargetImpl parentTarget, List<String> trace) {
            this.name = name;
            this.parentTarget = parentTarget;

            handlerManager.addEventFilter(EmptyEvent.ANY, e -> trace.add("filter:" + e.getSource()));
            handlerManager.addEventHandler(EmptyEvent.ANY, e -> trace.add("handler:" + e.getSource()));
        }

        @Override
        public EventDispatchChain buildEventDispatchChain(EventDispatchChain tail) {
            EventTargetImpl eventTarget = this;
            while (eventTarget != null) {
                tail = tail.prepend(eventTarget.handlerManager);
                eventTarget = eventTarget.parentTarget;
            }

            return tail;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
