/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.demo;

import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.DeliveryStrategy;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.WorkerId;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static io.spine.delivery.demo.GreeterContext.NAME;

/**
 * Provides a GET HTTP request handler which releases a delivery shard
 * using the {@link io.spine.delivery.client.SimpleDeliveryClient DeliveryClient}.
 */
@SuppressWarnings("serial")
@WebServlet(name = "ReleaseShard", value = "/work-registry/release")
public final class ReleaseShard extends ContextAwareServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        _info().log("Releasing a shard!");
        WorkerId worker = WorkerId.newBuilder()
                .setNodeId(ServerEnvironment.instance().nodeId())
                .setValue(NAME)
                .vBuild();
        ShardIndex shard = DeliveryStrategy.newIndex(1, 2);
        client.get()
              .releaseShard(shard, worker);
    }
}
