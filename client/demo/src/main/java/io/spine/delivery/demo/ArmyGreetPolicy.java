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
final class ArmyGreetPolicy extends AbstractCommander {

    @Command
    Iterable<SayHello> handle(GreetAnArmy command) {
        var howManySoldiers = command.getHowManySoldiers();
        var armyId = Identifier.newUuid();
        logger().atInfo().log(() -> format(
                "Greeting %d soldiers of the army `%s`", howManySoldiers, armyId));
        ImmutableList.Builder<SayHello> builder = ImmutableList.builder();
        for (var soldierIndex = 0; soldierIndex < howManySoldiers; soldierIndex++) {
            var sayHello = newCommand(armyId, soldierIndex);
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
