/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.demo;

import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.DeliveryStrategy;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.WorkerId;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static io.spine.message.delivery.demo.GreeterContext.NAME;

/**
 * Provides a GET HTTP request handler which picks up a delivery shard
 * using the {@link io.spine.message.delivery.client.SimpleDeliveryClient SimpleDeliveryClient}.
 */
@SuppressWarnings("serial")
@WebServlet(name = "LockShard", value = "/work-registry/pickUp")
public final class LockShard extends ContextAwareServlet {

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        _info().log("Picking up a shard!");
        WorkerId worker = WorkerId.newBuilder()
                .setNodeId(ServerEnvironment.instance()
                                   .nodeId())
                .setValue(NAME)
                .vBuild();
        ShardIndex shard = DeliveryStrategy.newIndex(1, 2);
        client.get()
              .pickUpShard(shard, worker);
    }
}
