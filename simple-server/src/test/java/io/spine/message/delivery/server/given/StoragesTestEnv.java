/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.given;

import io.spine.message.delivery.server.event.AnotherTestEvent;
import io.spine.message.delivery.server.event.AnotherTestEventId;
import io.spine.message.delivery.server.event.TestEvent;
import io.spine.message.delivery.server.event.TestEventId;
import io.spine.server.ContextSpec;
import io.spine.server.storage.MessageRecordSpec;
import io.spine.server.storage.RecordSpec;

import java.util.UUID;

import static io.spine.server.ContextSpec.singleTenant;

/**
 * Utility class for creation some objects usefully for storage testing.
 */
public final class StoragesTestEnv {

    private StoragesTestEnv() {
    }

    /**
     * Creates a new {@code TestEventId} with a random {@code UUID} as its value.
     */
    public static TestEventId randomId() {
        UUID uuid = UUID.randomUUID();
        return TestEventId
                .newBuilder()
                .setValue(uuid.toString())
                .vBuild();
    }

    /**
     * Creates a new {@code TestEvent} with the given {@code id}.
     */
    public static TestEvent testEventWith(TestEventId id) {
        return TestEvent
                .newBuilder()
                .setId(id)
                .vBuild();
    }

    /**
     * Creates a new {@code AnotherTestEventID} with a random {@code UUID} as its value.
     */
    public static AnotherTestEventId anotherRandomId() {
        UUID uuid = UUID.randomUUID();
        return AnotherTestEventId
                .newBuilder()
                .setValue(uuid.toString())
                .vBuild();
    }

    /**
     * Creates a new {@code AnotherTestEvent} with the given {@code id}.
     */
    public static AnotherTestEvent anotherTestEventWith(AnotherTestEventId id) {
        return AnotherTestEvent
                .newBuilder()
                .setId(id)
                .vBuild();
    }

    public static ContextSpec newTestContext() {
        return singleTenant("Test");
    }

    /**
     * Creates a new spec with {@code TestEventId} ID and {@code TestEvent} record.
     */
    public static RecordSpec<TestEventId, TestEvent, ?> specForTestEvent() {
        return new MessageRecordSpec<>(TestEventId.class, TestEvent.class, TestEvent::getId);
    }

    /**
     * Creates a new spec with {@code TestEventId} ID and {@code TestEvent} record.
     */
    public static RecordSpec<AnotherTestEventId, AnotherTestEvent, ?> specForAnotherTestEvent() {
        return new MessageRecordSpec<>(
                AnotherTestEventId.class, AnotherTestEvent.class, AnotherTestEvent::getId
        );
    }
}
