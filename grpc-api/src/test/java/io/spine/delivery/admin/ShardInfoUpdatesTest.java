/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
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
        ShardInfoUpdate actual = ShardInfoUpdates.messagesCountChangedTo(SHARD, 2);

        ShardInfoUpdate expected = ShardInfoUpdate
                .newBuilder()
                .setIndex(SHARD)
                .setNewMessagesCount(2)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("create a `ShardInfoUpdate` with negative message count")
    void createUpdateWithNegativeCount() {
        ShardInfoUpdate actual = ShardInfoUpdates.messagesCountChangedTo(SHARD, -1);

        ShardInfoUpdate expected = ShardInfoUpdate
                .newBuilder()
                .setIndex(SHARD)
                .setNewMessagesCount(-1)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("create `ShardInfoUpdate` for a picked shard.")
    void createShardPickedUpdate() {
        Timestamp whenPicked = currentTime();
        ShardInfoUpdate actual = ShardInfoUpdates.shardPicked(SHARD, whenPicked);

        ShardInfoUpdate expected = ShardInfoUpdate
                .newBuilder()
                .setIndex(SHARD)
                .setNewStatus(PICKED)
                .setWhenLastPicked(whenPicked)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("create `ShardInfoUpdate` for an unpicked shard.")
    void createShardUnpickedUpdate() {
        ShardInfoUpdate actual = ShardInfoUpdates.shardUnpicked(SHARD);

        ShardInfoUpdate expected = ShardInfoUpdate
                .newBuilder()
                .setIndex(SHARD)
                .setNewStatus(NOT_PICKED)
                .build();

        assertThat(actual).isEqualTo(expected);
    }
}
