/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.admin;

import com.google.common.testing.NullPointerTester;
import com.google.protobuf.Timestamp;
import io.spine.server.delivery.ShardIndex;
import io.spine.testing.UtilityClassTest;
import org.junit.jupiter.api.DisplayName;

import static io.spine.base.Time.currentTime;

@DisplayName("ShardInfoUpdates utility should")
final class ShardInfoUpdatesTest extends UtilityClassTest<ShardInfoUpdates> {

    ShardInfoUpdatesTest() {
        super(ShardInfoUpdates.class);
    }

    @Override
    protected void configure(NullPointerTester tester) {
        var index = ShardIndex
                .newBuilder()
                .setIndex(1)
                .setOfTotal(5)
                .vBuild();
        tester.setDefault(ShardIndex.class, index);
        tester.setDefault(Timestamp.class, currentTime());
    }
}
