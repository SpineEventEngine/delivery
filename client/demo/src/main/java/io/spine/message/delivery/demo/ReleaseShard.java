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
 * Provides a GET HTTP request handler which releases a delivery shard
 * using the {@link io.spine.message.delivery.client.SimpleDeliveryClient DeliveryClient}.
 */
@SuppressWarnings("serial")
@WebServlet(name = "ReleaseShard", value = "/work-registry/release")
public final class ReleaseShard extends ContextAwareServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        _info().log("Releasing a shard!");
        NodeId node = ServerEnvironment.instance().nodeId();
        // TODO: 2022-02-28:dmitry.kashcheiev: current solution is incorrect we should release
        //  the shard that was actually picked in LockSahrd controller, probably ? we can use
        //  only node ID here.
        String threadId = String.valueOf(Thread.currentThread().getId());
        WorkerId worker = WorkerId.newBuilder()
                .setNodeId(node)
                .setValue(threadId)
                .vBuild();
        ShardIndex shard = DeliveryStrategy.newIndex(1, 2);
        client.get()
              .releaseShard(shard, worker);
    }
}
