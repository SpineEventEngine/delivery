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
