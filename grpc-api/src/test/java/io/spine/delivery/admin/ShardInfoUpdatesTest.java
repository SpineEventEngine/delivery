/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.delivery.admin;

import com.google.common.testing.NullPointerTester;
import io.spine.delivery.admin.grpc.ShardInfoUpdate;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.ShardSessionRecord;
import io.spine.server.delivery.WorkerId;
import io.spine.server.NodeId;
import io.spine.testing.UtilityClassTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static io.spine.base.Time.currentTime;
import static io.spine.delivery.admin.grpc.ShardStatus.NOT_PICKED;
import static io.spine.delivery.admin.grpc.ShardStatus.PICKED;
import static io.spine.server.delivery.DeliveryStrategy.newIndex;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`ShardInfoUpdates` utility should")
final class ShardInfoUpdatesTest extends UtilityClassTest<ShardInfoUpdates> {

    private static final ShardIndex SHARD = newIndex(1, 10);

    ShardInfoUpdatesTest() {
        super(ShardInfoUpdates.class);
    }

    @Override
    protected void configure(NullPointerTester tester) {
        tester.setDefault(ShardIndex.class, newIndex(1, 5));
    }

    @Test
    @DisplayName("create an update of a picked shard")
    void picked() {
        var whenPicked = currentTime();
        var session = ShardSessionRecord.newBuilder()
                .setIndex(SHARD)
                .setWorker(worker())
                .setWhenLastPicked(whenPicked)
                .build();

        var actual = ShardInfoUpdates.currentState(SHARD, session, 2);

        var expected = ShardInfoUpdate.newBuilder()
                .setIndex(SHARD)
                .setNewStatus(PICKED)
                .setWhenLastPicked(whenPicked)
                .setNewMessagesCount(2)
                .build();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("create an update of a released shard")
    void released() {
        var whenPicked = currentTime();
        var session = ShardSessionRecord.newBuilder()
                .setIndex(SHARD)
                .setWhenLastPicked(whenPicked)
                .build();

        var actual = ShardInfoUpdates.currentState(SHARD, session, 0);

        var expected = ShardInfoUpdate.newBuilder()
                .setIndex(SHARD)
                .setNewStatus(NOT_PICKED)
                .setWhenLastPicked(whenPicked)
                .setNewMessagesCount(0)
                .build();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("create an update of a shard without a session")
    void withoutSession() {
        var actual = ShardInfoUpdates.currentState(SHARD, null, 3);

        var expected = ShardInfoUpdate.newBuilder()
                .setIndex(SHARD)
                .setNewStatus(NOT_PICKED)
                .setNewMessagesCount(3)
                .build();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("reject a negative number of messages")
    void negativeCount() {
        assertThrows(IllegalArgumentException.class,
                     () -> ShardInfoUpdates.currentState(SHARD, null, -1));
    }

    private static WorkerId worker() {
        return WorkerId.newBuilder()
                .setNodeId(NodeId.newBuilder().setValue("node"))
                .setValue("worker")
                .build();
    }
}
