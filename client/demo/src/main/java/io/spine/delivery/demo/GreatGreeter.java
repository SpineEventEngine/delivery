/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.demo;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ProtocolStringList;
import io.spine.delivery.demo.command.PersonAlreadyGreeted;
import io.spine.delivery.demo.command.SayHello;
import io.spine.delivery.demo.event.SaidHello;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.command.Assign;

import java.security.SecureRandom;
import java.util.Random;

/**
 * The one who greets people, but only once.
 */
final class GreatGreeter extends Aggregate<String, Greeter, Greeter.Builder> {

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

    private static final Random random = new SecureRandom();

    @Assign
    SaidHello handle(SayHello c) throws PersonAlreadyGreeted {
        var personName = c.getName();
        ProtocolStringList alreadyGreetedPeople = state().getNameList();
        if (alreadyGreetedPeople.contains(personName)) {
            throw PersonAlreadyGreeted.newBuilder()
                    .setName(personName)
                    .build();
        }
        builder().addName(personName);
        return SaidHello.newBuilder()
                .setName(personName)
                .setGreeting(randomGreeting(personName))
                .build();
    }

    private static String randomGreeting(String name) {
        var greeting = greetings.get(random.nextInt(greetings.size()));
        return String.format(greeting, name);
    }
}
