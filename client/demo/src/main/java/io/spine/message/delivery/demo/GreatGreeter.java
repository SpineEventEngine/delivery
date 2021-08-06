/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.demo;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ProtocolStringList;
import io.spine.logging.Logging;
import io.spine.message.delivery.demo.command.PersonAlreadyGreeted;
import io.spine.message.delivery.demo.command.SayHello;
import io.spine.message.delivery.demo.event.SaidHello;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.Random;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The one who greets people, but only once.
 */
final class GreatGreeter extends Aggregate<String, Greeter, Greeter.Builder> implements Logging {

    private static final ImmutableList<String> greetings = ImmutableList.<String>builder()
            .add("Hello %s.")
            .add("It's nice to meet you %s.")
            .add("Hi %s.")
            .add("It’s a pleasure to meet you %s.")
            .add("It’s good to see you %s.")
            .add("What’s up %s?")
            .add("How's it going %s?")
            .add("Yo %s!")
            .add("Hey. ‘Sup %s?")
            .build();

    private @MonotonicNonNull Random random;

    @Assign
    SaidHello handle(SayHello c) throws PersonAlreadyGreeted {
        String personName = c.getName();
        ProtocolStringList alreadyGreetedPeople = state().getNameList();
        if (alreadyGreetedPeople.contains(personName)) {
            throw PersonAlreadyGreeted.newBuilder()
                    .setName(personName)
                    .build();
        }
        return SaidHello.newBuilder()
                .setName(personName)
                .setGreeting(randomGreeting(personName))
                .vBuild();
    }

    @Apply
    private void on(SaidHello e) {
        builder().addName(e.getName());
    }

    private String randomGreeting(String name) {
        String greeting = greetings.get(random.nextInt(greetings.size()));
        return String.format(greeting, name);
    }

    /**
     * Sets {@link #random} to be used during handling of signals.
     *
     * @implNote the method is intended to be used as part of the entity configuration
     *         done through the repository
     */
    void setRandom(Random random) {
        this.random = checkNotNull(random);
    }
}
