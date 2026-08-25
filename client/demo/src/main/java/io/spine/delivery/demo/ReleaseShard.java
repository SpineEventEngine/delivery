/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.demo;

import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.DeliveryStrategy;
import io.spine.server.delivery.WorkerId;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.Serial;

import static io.spine.delivery.demo.GreeterContext.NAME;

/**
 * Provides a GET HTTP request handler that releases a delivery shard
 * using the {@link io.spine.delivery.client.DeliveryClient DeliveryClient}.
 */
@SuppressWarnings(
        "DuplicateStringLiteralInspection" /* `ReleaseShard` is also a Protobuf command type
         with the generated string literal. */
)
@WebServlet(name = "ReleaseShard", value = "/work-registry/release")
public final class ReleaseShard extends ContextAwareServlet {

    @Serial
    private static final long serialVersionUID = 0L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        logger().atInfo().log(() -> "Releasing a shard!");
        var worker = WorkerId.newBuilder()
                .setNodeId(ServerEnvironment.instance().nodeId())
                .setValue(NAME)
                .build();
        var shard = DeliveryStrategy.newIndex(1, 2);
        client.get()
              .releaseShard(shard, worker);
    }
}
