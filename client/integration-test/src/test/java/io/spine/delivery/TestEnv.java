/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery;

import io.spine.delivery.client.SimpleDeliveryClient;
import io.spine.server.NodeId;
import io.spine.server.delivery.DeliveryStrategy;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.WorkerId;
import io.spine.test.delivery.client.Something;
import io.spine.type.TypeUrl;

import java.util.List;
import java.util.stream.IntStream;

import static io.spine.base.Identifier.newUuid;
import static io.spine.delivery.client.given.TestInboxMessages.toDeliver;
import static java.util.stream.Collectors.toList;

/**
 * Test environment that contains predefined contents and methods to be used in tests.
 */
final class TestEnv {

    private TestEnv() {
    }

    /**
     * Predefined {@code ShardIndex} of shard with {@code indexValue} {@code 1} and {@code ofTotal}
     * {@code 2}.
     */
    public static final ShardIndex SHARD = DeliveryStrategy.newIndex(1, 2);

    /**
     * Predefined {@code NodeId} with the {@code SimpleDeliveryClient} full class name used
     * as its value.
     */
    public static final NodeId NODE = NodeId
            .newBuilder()
            .setValue(SimpleDeliveryClient.class.getName())
            .vBuild();

    /**
     * Predefined {@code WorkerId} that uses predefined {@code NODE} and
     * {@code SimpleDeliveryClient} full class name as its values.
     */
    public static final WorkerId WORKER = WorkerId
            .newBuilder()
            .setNodeId(NODE)
            .setValue(SimpleDeliveryClient.class.getName())
            .vBuild();

    /**
     * Creates a new {@code InboxMessage} with the {@linkplain Something} target type and a random
     * {@code UUID}.
     */
    public static InboxMessage newMessage() {
        return toDeliver(newUuid(), TypeUrl.from(Something.getDescriptor()));
    }

    /**
     * Generates the given {@code number} of {@code InboxMessage}s with the {@linkplain Something}
     * target type and a random {@code UUID}.
     */
    public static List<InboxMessage> generate(int number) {
        return IntStream
                .range(0, number)
                .mapToObj(i -> toDeliver(newUuid(), TypeUrl.from(Something.getDescriptor())))
                .collect(toList());
    }
}
