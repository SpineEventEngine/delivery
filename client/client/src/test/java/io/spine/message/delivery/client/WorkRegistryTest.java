/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.common.base.Suppliers;
import com.google.common.testing.NullPointerTester;
import com.google.protobuf.Duration;
import io.spine.base.Time;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.protobuf.Durations2;
import io.spine.server.NodeId;
import io.spine.server.delivery.DeliveryStrategy;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.ShardProcessingSession;
import io.spine.server.delivery.ShardedWorkRegistry;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

@DisplayName("`WorkRegistry` should")
final class WorkRegistryTest {

    private static final ShardIndex shard = DeliveryStrategy.newIndex(1, 2);
    private static final NodeId worker = NodeId.newBuilder()
            .setValue("test")
            .vBuild();

    @Test
    @DisplayName("be `NPE`-safe")
    void beNpeSafe() {
        NullPointerTester tester = new NullPointerTester();
        tester.setDefault(ShardIndex.class, shard);
        tester.setDefault(NodeId.class, worker);
        tester.setDefault(Duration.class, Durations2.fromMinutes(1));
        tester.testAllPublicConstructors(WorkRegistry.class);
        tester.testAllPublicInstanceMethods(new WorkRegistry(NoOpClient::new));
    }

    @Nested
    @DisplayName("try to pick up a shard")
    final class PickUp {

        @Test
        @DisplayName("returning `Optional.empty()` when it is not possible to pick up one")
        void empty() {
            ShardedWorkRegistry registry = new WorkRegistry(NoOpClient::new);
            Optional<ShardProcessingSession> result = registry.pickUp(shard, worker);
            assertThat(result)
                    .isEmpty();
        }

        @Test
        @DisplayName("returning a `ShardProcessingSession`")
        void session() {
            ShardPickedUp shardPickedUp = ShardPickedUp.newBuilder()
                    .setShard(shard)
                    .setPickedBy(worker)
                    .setWhenPicked(Time.currentTime())
                    .vBuild();
            ShardedWorkRegistry registry =
                    new WorkRegistry(Suppliers.ofInstance(new NoOpClient(shardPickedUp)));
            Optional<ShardProcessingSession> result = registry.pickUp(shard, worker);
            assertThat(result)
                    .isPresent();
            ShardProcessingSession session = result.get();
            assertThat(session.shardIndex())
                    .isEqualTo(shard);
        }
    }

    private static class NoOpClient implements SessionRegistryClient {

        private final @Nullable ShardPickedUp event;

        private NoOpClient() {
            this(null);
        }

        private NoOpClient(@Nullable ShardPickedUp event) {
            this.event = event;
        }

        @Override
        public Optional<ShardPickedUp> pickUpShard(ShardIndex shard, NodeId worker) {
            return Optional.ofNullable(event);
        }

        @Override
        public void releaseShard(ShardIndex shard, NodeId worker) {
            // do nothing
        }
    }
}
