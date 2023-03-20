/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.spine.message.delivery.server.event.AnotherTestEvent;
import io.spine.message.delivery.server.event.AnotherTestEventId;
import io.spine.message.delivery.server.event.TestEvent;
import io.spine.message.delivery.server.event.TestEventId;
import io.spine.message.delivery.server.given.MemoizingStorageSubscriber;
import io.spine.message.delivery.server.given.MemoizingStorageSubscriber.SingleWrite;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.message.delivery.server.given.StoragesTestEnv.anotherTestEventWith;
import static io.spine.message.delivery.server.given.StoragesTestEnv.newTestContext;
import static io.spine.message.delivery.server.given.StoragesTestEnv.specForAnotherTestEvent;
import static io.spine.message.delivery.server.given.StoragesTestEnv.specForTestEvent;
import static io.spine.message.delivery.server.given.StoragesTestEnv.testEventWith;

@DisplayName("`ReportingStorageFactory` should")
class ReportingStorageFactoryTest {

    private ReportingStorageFactory factory;

    @BeforeEach
    void setup() {
        factory = new ReportingStorageFactory(InMemoryStorageFactory.newInstance());
    }

    @Test
    @DisplayName("subscribe to updates before storage creation")
    void subscribeBeforeCreation() {
        var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();

        factory.subscribe(TestEventId.class, TestEvent.class, sub);
        var storage = factory.createRecordStorage(newTestContext(), specForTestEvent());

        TestEventId id = TestEventId.generate();
        TestEvent record = testEventWith(id);

        storage.write(id, record);

        assertThat(sub.writes())
                .containsExactly(SingleWrite.of(id, record));
    }

    @Test
    @DisplayName("subscribe to updates from already created storages")
    void subscribeToAlreadyCreated() {
        var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();

        var storage = factory.createRecordStorage(newTestContext(), specForTestEvent());
        factory.subscribe(TestEventId.class, TestEvent.class, sub);

        TestEventId id = TestEventId.generate();
        TestEvent record = testEventWith(id);

        storage.write(id, record);

        assertThat(sub.writes())
                .containsExactly(SingleWrite.of(id, record));
    }

    @Test
    @DisplayName("subscribe to a storage of a particular type")
    void subscribeByParticularStorageType() {
        var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();

        var storage = factory.createRecordStorage(newTestContext(), specForAnotherTestEvent());
        // Subscription to another storage type.
        factory.subscribe(TestEventId.class, TestEvent.class, sub);

        AnotherTestEventId id = AnotherTestEventId.generate();
        AnotherTestEvent record = anotherTestEventWith(id);

        storage.write(id, record);

        assertThat(sub.writes()).isEmpty();
    }

    @Test
    @DisplayName("handle unsubscribe correctly")
    void unsubscribe() {
        var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();

        var storage = factory.createRecordStorage(newTestContext(), specForTestEvent());
        var subscription = factory.subscribe(TestEventId.class, TestEvent.class, sub);

        TestEventId id = TestEventId.generate();
        TestEvent record = testEventWith(id);

        storage.write(id, record);

        subscription.cancel();

        storage.write(id, record);

        assertThat(sub.writes())
                .containsExactly(SingleWrite.of(id, record));
    }

    @Test
    @DisplayName("does not subscribe to future storages if already unsubscribed")
    void unsubscribeFromFuture() {
        var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();

        factory.createRecordStorage(newTestContext(), specForTestEvent());
        var subscription = factory.subscribe(TestEventId.class, TestEvent.class, sub);

        TestEventId id = TestEventId.generate();
        TestEvent record = testEventWith(id);

        subscription.cancel();

        var storage = factory.createRecordStorage(newTestContext(), specForTestEvent());
        storage.write(id, record);

        assertThat(sub.writes())
                .isEmpty();
    }

    @Test
    @DisplayName("handle multiple subscribers")
    void handleMultipleSubscribers() {
        var firstSub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
        var secondSub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();

        var storage = factory.createRecordStorage(newTestContext(), specForTestEvent());
        factory.subscribe(TestEventId.class, TestEvent.class, firstSub);
        factory.subscribe(TestEventId.class, TestEvent.class, secondSub);

        TestEventId id = TestEventId.generate();
        TestEvent record = testEventWith(id);

        storage.write(id, record);

        assertThat(firstSub.writes())
                .containsExactly(SingleWrite.of(id, record));
        assertThat(secondSub.writes())
                .containsExactly(SingleWrite.of(id, record));
    }

    @Test
    @DisplayName("handle unsubscribe of each sub individually")
    void unsubscribeIndividually() {
        var firstSub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
        var secondSub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();

        var storage = factory.createRecordStorage(newTestContext(), specForTestEvent());
        factory.subscribe(TestEventId.class, TestEvent.class, firstSub);
        var secondSubscription = factory.subscribe(TestEventId.class, TestEvent.class, secondSub);

        TestEventId id = TestEventId.generate();
        TestEvent record = testEventWith(id);

        storage.write(id, record);
        secondSubscription.cancel();
        storage.write(id, record);

        assertThat(firstSub.writes())
                .containsExactly(SingleWrite.of(id, record), SingleWrite.of(id, record));
        assertThat(secondSub.writes())
                .containsExactly(SingleWrite.of(id, record));
    }

    @Test
    @DisplayName("subscribe to multiple storages")
    void subscribeToMultipleStorages() {
        var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();

        factory.subscribe(TestEventId.class, TestEvent.class, sub);
        var storage1 = factory.createRecordStorage(newTestContext(), specForTestEvent());
        var storage2 = factory.createRecordStorage(newTestContext(), specForTestEvent());

        TestEventId id = TestEventId.generate();
        TestEvent record = testEventWith(id);

        storage1.write(id, record);
        storage2.write(id, record);

        assertThat(sub.writes())
                .containsExactly(SingleWrite.of(id, record), SingleWrite.of(id, record));
    }

    @Test
    @DisplayName("unsubscribe from all storages")
    void unsubscribeFromAllStorages() {
        var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();

        var subscription = factory.subscribe(TestEventId.class, TestEvent.class, sub);
        var storage1 = factory.createRecordStorage(newTestContext(), specForTestEvent());
        var storage2 = factory.createRecordStorage(newTestContext(), specForTestEvent());

        TestEventId id = TestEventId.generate();
        TestEvent record = testEventWith(id);

        storage1.write(id, record);
        storage2.write(id, record);
        subscription.cancel();
        storage1.write(id, record);
        storage2.write(id, record);

        assertThat(sub.writes())
                .containsExactly(SingleWrite.of(id, record), SingleWrite.of(id, record));
    }
}
