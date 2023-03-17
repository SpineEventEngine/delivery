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
import static io.spine.server.delivery.DeliveryStrategy.newIndex;

@DisplayName("`ShardInfoUpdates` utility should")
final class ShardInfoUpdatesTest extends UtilityClassTest<ShardInfoUpdates> {

    ShardInfoUpdatesTest() {
        super(ShardInfoUpdates.class);
    }

    @Override
    protected void configure(NullPointerTester tester) {
        tester.setDefault(ShardIndex.class, newIndex(1,5));
        tester.setDefault(Timestamp.class, currentTime());
    }
}
