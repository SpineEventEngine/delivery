/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server.given;

import io.spine.delivery.server.event.AnotherTestEvent;
import io.spine.delivery.server.event.AnotherTestEventId;
import io.spine.delivery.server.event.TestEvent;
import io.spine.delivery.server.event.TestEventId;
import io.spine.server.ContextSpec;
import io.spine.server.storage.RecordSpec;

import static io.spine.server.ContextSpec.singleTenant;

/**
 * Utility class for creation some objects usefully for storage testing.
 */
public final class StoragesTestEnv {

    private StoragesTestEnv() {
    }

    /**
     * Creates a new {@code TestEvent} with the given {@code id}.
     */
    public static TestEvent testEventWith(TestEventId id) {
        return TestEvent.newBuilder()
                .setId(id)
                .build();
    }

    /**
     * Creates a new {@code AnotherTestEvent} with the given {@code id}.
     */
    public static AnotherTestEvent anotherTestEventWith(AnotherTestEventId id) {
        return AnotherTestEvent.newBuilder()
                .setId(id)
                .build();
    }

    public static ContextSpec newTestContext() {
        return singleTenant("Test");
    }

    /**
     * Creates a new spec with {@code TestEventId} ID and {@code TestEvent} record.
     */
    public static RecordSpec<TestEventId, TestEvent> specForTestEvent() {
        return new RecordSpec<>(TestEventId.class, TestEvent.class, TestEvent::getId);
    }

    /**
     * Creates a new spec with {@code TestEventId} ID and {@code TestEvent} record.
     */
    public static RecordSpec<AnotherTestEventId, AnotherTestEvent> specForAnotherTestEvent() {
        return new RecordSpec<>(
                AnotherTestEventId.class, AnotherTestEvent.class, AnotherTestEvent::getId
        );
    }
}
