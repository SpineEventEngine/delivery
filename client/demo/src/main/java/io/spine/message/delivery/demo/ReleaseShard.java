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

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Provides a GET HTTP request handler which releases a delivery shard
 * using the {@link io.spine.message.delivery.client.DeliveryClient DeliveryClient}.
 */
@SuppressWarnings("serial")
@WebServlet(name = "ReleaseShard", value = "/work-registry/release")
final class ReleaseShard extends DemoServlet {

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        _info().log("Releasing a shard!");
        NodeId worker = ServerEnvironment.instance().nodeId();
        ShardIndex shard = DeliveryStrategy.newIndex(1, 2);
        client.get()
              .releaseShard(shard, worker);
    }
}
