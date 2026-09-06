/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
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
