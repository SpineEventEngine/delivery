/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.demo;

import io.spine.delivery.demo.command.SayHello;
import io.spine.delivery.demo.event.SaidHello;
import io.spine.server.aggregate.AggregateRepository;
import io.spine.server.route.CommandRouting;
import io.spine.server.route.EventRouting;

/**
 * Manages {@link GreatGreeter} aggregates.
 */
final class GreeterRepo extends AggregateRepository<String, GreatGreeter, Greeter> {

    @Override
    protected void setupCommandRouting(CommandRouting<String> routing) {
        routing.route(SayHello.class, (cmd, ctx) -> cmd.getName());
    }

    @Override
    protected void setupEventRouting(EventRouting<String> routing) {
        routing.unicast(SaidHello.class, SaidHello::getName);
    }
}
