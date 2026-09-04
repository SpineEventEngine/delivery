/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
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
