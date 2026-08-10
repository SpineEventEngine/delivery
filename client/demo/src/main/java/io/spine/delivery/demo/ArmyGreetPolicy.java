/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.demo;

import com.google.common.collect.ImmutableList;
import io.spine.base.Identifier;
import io.spine.logging.WithLogging;
import io.spine.delivery.demo.command.GreetAnArmy;
import io.spine.delivery.demo.command.SayHello;
import io.spine.server.command.AbstractCommander;
import io.spine.server.command.Command;

import static java.lang.String.format;

/**
 * A policy determining how one greets an army.
 *
 * <p>Splits the incoming command into a number of {@link SayHello} commands, one for each soldier.
 */
final class ArmyGreetPolicy extends AbstractCommander implements WithLogging {

    @Command
    Iterable<SayHello> handle(GreetAnArmy command) {
        int howManySoldiers = command.getHowManySoldiers();
        String armyId = Identifier.newUuid();
        logger().atInfo().log(() -> format(
                "Greeting %d soldiers of the army `%s`", howManySoldiers, armyId));
        ImmutableList.Builder<SayHello> builder = ImmutableList.builder();
        for (int soldierIndex = 0; soldierIndex < howManySoldiers; soldierIndex++) {
            SayHello sayHello = newCommand(armyId, soldierIndex);
            builder.add(sayHello);
        }
        return builder.build();
    }

    private static SayHello newCommand(String armyId, int soliderIndex) {
        return SayHello.newBuilder()
                .setName(soliderName(armyId, soliderIndex))
                .build();
    }

    private static String soliderName(String armyId, int soldierIndex) {
        return "[Army `" + armyId + "`] Soldier #" + soldierIndex;
    }
}
