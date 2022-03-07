/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.demo;

import io.spine.server.NodeId;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.DeliveryStrategy;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.WorkerId;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Provides a GET HTTP request handler which picks up a delivery shard
 * using the {@link io.spine.message.delivery.client.SimpleDeliveryClient DeliveryClient}.
 */
@SuppressWarnings("serial")
@WebServlet(name = "LockShard", value = "/work-registry/pickUp")
public final class LockShard extends ContextAwareServlet {

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        _info().log("Picking up a shard!");
        NodeId node = ServerEnvironment.instance()
                .nodeId();
        String threadId = String.valueOf(Thread.currentThread().getId());
        WorkerId worker = WorkerId.newBuilder()
                .setNodeId(node)
                .setValue(threadId)
                .vBuild();
        ShardIndex shard = DeliveryStrategy.newIndex(1, 2);
        client.get()
              .pickUpShard(shard, worker);
    }
}
