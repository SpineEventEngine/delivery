/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.demo;

import io.spine.message.delivery.demo.command.SayHello;
import io.spine.message.delivery.demo.event.SaidHello;
import io.spine.server.aggregate.AggregateRepository;
import io.spine.server.route.CommandRouting;
import io.spine.server.route.EventRouting;

import java.util.Optional;
import java.util.Random;

import static com.google.common.base.Preconditions.checkNotNull;

final class GreeterRepo extends AggregateRepository<String, GreatGreeter> {

    static final String greeter = "GreatGreeter";

    private final Random random;

    /**
     * Creates a new repository with the {@code random} to be used by the {@link GreatGreeter}.
     */
    GreeterRepo(Random random) {
        super();
        this.random = checkNotNull(random);
    }

    @Override
    protected void setupCommandRouting(CommandRouting<String> routing) {
        routing.route(SayHello.class, (cmd, ctx) -> greeter);
    }

    @Override
    protected void setupEventRouting(EventRouting<String> routing) {
        routing.unicast(SaidHello.class, e -> greeter);
    }

    @Override
    public Optional<GreatGreeter> find(String id) throws IllegalStateException {
        Optional<GreatGreeter> result = super.find(id);
        result.ifPresent(greeter -> greeter.setRandom(random));
        return result;
    }

    @Override
    public GreatGreeter create(String id) {
        GreatGreeter result = super.create(id);
        result.setRandom(random);
        return result;
    }
}
