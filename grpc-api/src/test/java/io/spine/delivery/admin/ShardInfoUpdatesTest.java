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

package io.spine.delivery.admin;

import com.google.common.testing.NullPointerTester;
import com.google.protobuf.Timestamp;
import io.spine.delivery.admin.grpc.ShardInfoUpdate;
import io.spine.server.delivery.ShardIndex;
import io.spine.testing.UtilityClassTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static io.spine.base.Time.currentTime;
import static io.spine.delivery.admin.grpc.ShardStatus.NOT_PICKED;
import static io.spine.delivery.admin.grpc.ShardStatus.PICKED;
import static io.spine.server.delivery.DeliveryStrategy.newIndex;

@DisplayName("`ShardInfoUpdates` utility should")
final class ShardInfoUpdatesTest extends UtilityClassTest<ShardInfoUpdates> {

    private static final ShardIndex SHARD = newIndex(1,10);

    ShardInfoUpdatesTest() {
        super(ShardInfoUpdates.class);
    }

    @Override
    protected void configure(NullPointerTester tester) {
        tester.setDefault(ShardIndex.class, newIndex(1, 5));
        tester.setDefault(Timestamp.class, currentTime());
    }

    @Test
    @DisplayName("create a `ShardInfoUpdate` with positive message count")
    void createUpdateWithPositiveCount() {
        var actual = ShardInfoUpdates.messagesCountChangedTo(SHARD, 2);

        var expected = ShardInfoUpdate.newBuilder()
                .setIndex(SHARD)
                .setNewMessagesCount(2)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("create a `ShardInfoUpdate` with negative message count")
    void createUpdateWithNegativeCount() {
        var actual = ShardInfoUpdates.messagesCountChangedTo(SHARD, -1);

        var expected = ShardInfoUpdate.newBuilder()
                .setIndex(SHARD)
                .setNewMessagesCount(-1)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("create `ShardInfoUpdate` for a picked shard.")
    void createShardPickedUpdate() {
        var whenPicked = currentTime();
        var actual = ShardInfoUpdates.shardPicked(SHARD, whenPicked);

        var expected = ShardInfoUpdate.newBuilder()
                .setIndex(SHARD)
                .setNewStatus(PICKED)
                .setWhenLastPicked(whenPicked)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("create `ShardInfoUpdate` for an unpicked shard.")
    void createShardUnpickedUpdate() {
        var actual = ShardInfoUpdates.shardUnpicked(SHARD);

        var expected = ShardInfoUpdate.newBuilder()
                .setIndex(SHARD)
                .setNewStatus(NOT_PICKED)
                .build();

        assertThat(actual).isEqualTo(expected);
    }
}
