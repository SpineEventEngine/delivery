/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.demo;

import com.google.common.collect.ImmutableSet;
import io.spine.client.Subscription;
import io.spine.json.Json;
import io.spine.message.delivery.demo.command.GreetAnArmy;
import io.spine.message.delivery.demo.event.SaidHello;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * Propagates HTTP requests into a command to greet an army.
 */
@WebServlet(name = "Army Greeter", value = "/greet-army")
@SuppressWarnings("serial")
public final class GreetArmyServlet extends ContextAwareServlet {

    @Override
    @SuppressWarnings("UnstableApiUsage")  /* Using Guava's type which hasn't changed since 2012. */
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        _debug().log("Starting to greet an army.");

        @SuppressWarnings("MagicNumber") int howManySoldiers = 1000;
        _info().log("Today's army count is %d.", howManySoldiers);

        GreetAnArmy greetAnArmy = GreetAnArmy.newBuilder()
                .setHowManySoldiers(howManySoldiers)
                .vBuild();

        long startMillis = System.currentTimeMillis();
        CountDownLatch greeted = new CountDownLatch(howManySoldiers);
        // todo: We should subscribe to `SaidHello` event separately.
        //  Subscribing as done below doesn't work.
        ImmutableSet<Subscription> subscriptions = spineClient
                .asGuest()
                .command(greetAnArmy)
                .observe(SaidHello.class, e -> {
                    String greeting = e.getGreeting();
                    _info().log("One of the soldiers was greeted: `%s`", greeting);
                    greeted.countDown();
                    if (greeted.getCount() == 0) {
                        logPerformance(howManySoldiers, startMillis);
                    }
                })
                .onServerError((msg, error) -> {
                    _trace().log(
                            "Server was not able to `%s`: %s",
                            msg.getClass(), error
                    );
                    throw newIllegalStateException(
                            "Something went terribly wrong: %s.", Json.toCompactJson(error)
                    );
                })
                .post();
        try {
            _debug().log("Waiting the army to be greeted.");
            greeted.await(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw newIllegalStateException(
                    e, "Hanged while waiting for the army to be greeted greeting :-("
            );
        }
        _debug().log("Unsubscribing from the greeting updates.");
        subscriptions.forEach(spineClient.subscriptions()::cancel);
    }

    private void logPerformance(int howManySoldiers, long startMillis) {
        long endMillis = System.currentTimeMillis();
        long durationMillis = endMillis - startMillis;
        long perSoldier = durationMillis / howManySoldiers;
        _info().log("Army was greeted for %d ms. That's about %d ms per soldier.",
                    durationMillis, perSoldier);
    }
}
