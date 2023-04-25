/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.common.base.Suppliers;
import com.google.common.testing.NullPointerTester;
import com.google.common.truth.extensions.proto.ProtoTruth;
import com.google.protobuf.Duration;
import io.spine.message.delivery.event.ExpiredSessionsReleased;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.protobuf.Durations2;
import io.spine.server.NodeId;
import io.spine.server.delivery.DeliveryStrategy;
import io.spine.server.delivery.PickUpOutcome;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.ShardSessionRecord;
import io.spine.server.delivery.ShardedWorkRegistry;
import io.spine.server.delivery.WorkerId;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth8.assertThat;
import static io.spine.base.Time.currentTime;
import static io.spine.message.delivery.client.ShardSessionRecords.fromEvent;
import static io.spine.server.delivery.PickUpOutcomeMixin.pickedUp;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`WorkRegistry` should")
final class WorkRegistryTest {

    private static final ShardIndex shard = DeliveryStrategy.newIndex(1, 2);
    private static final NodeId node = NodeId.newBuilder()
            .setValue("test-node")
            .vBuild();
    private static final WorkerId worker = WorkerId.newBuilder()
            .setNodeId(node)
            .setValue("test-worker")
            .vBuild();

    @Test
    @DisplayName("be `NPE`-safe")
    void beNpeSafe() {
        NullPointerTester tester = new NullPointerTester();
        tester.setDefault(ShardIndex.class, shard);
        tester.setDefault(NodeId.class, node);
        tester.setDefault(Duration.class, Durations2.fromMinutes(1));
        tester.testAllPublicConstructors(WorkRegistry.class);
        tester.testAllPublicInstanceMethods(new WorkRegistry(NoOpClient::new));
    }

    @Nested
    @DisplayName("try to pick up a shard")
    final class PickUp {

        @Test
        @DisplayName("throw exception thrown by the client when it is not possible to pick up one")
        void empty() {
            ShardedWorkRegistry registry = new WorkRegistry(NoOpClient::new);
            assertThrows(IllegalStateException.class,
                         () -> registry.pickUp(shard, node));
        }

        @Test
        @DisplayName("returning a `ShardProcessingSession`")
        void session() {
            ShardPickedUp shardPickedUp = ShardPickedUp.newBuilder()
                    .setShard(shard)
                    .setWorker(worker)
                    .setWhenPicked(currentTime())
                    .vBuild();
            ShardedWorkRegistry registry =
                    new WorkRegistry(Suppliers.ofInstance(new NoOpClient(shardPickedUp)));
            PickUpOutcome result = registry.pickUp(shard, node);
            assertThat(result.session())
                    .isPresent();
            ShardSessionRecord session = result.getSession();
            ProtoTruth.assertThat(session.getIndex())
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
        public PickUpOutcome pickUpShard(ShardIndex shard, WorkerId worker) {
            if (event == null) {
                throw new IllegalStateException("Unable to pick up shard.");
            }
            return pickedUp(fromEvent(event));
        }

        @Override
        public void releaseShard(ShardIndex shard, WorkerId worker) {
            // do nothing
        }

        @Override
        public ExpiredSessionsReleased releaseExpiredSessions(Duration inactivityPeriod) {
            return ExpiredSessionsReleased.getDefaultInstance();
        }
    }
}
