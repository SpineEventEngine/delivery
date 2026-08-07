/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.demo;

import com.google.common.net.MediaType;
import io.spine.client.Subscription;
import io.spine.json.Json;
import io.spine.delivery.demo.command.GreetAnArmy;
import io.spine.delivery.demo.event.SaidHello;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.spine.util.Exceptions.newIllegalStateException;
import static java.lang.String.format;
import static javax.servlet.http.HttpServletResponse.SC_OK;

/**
 * Propagates HTTP requests into a command to greet an army.
 */
@WebServlet(name = "Army Greeter", value = "/greet-army")
@SuppressWarnings("serial")
public final class GreetArmyServlet extends ContextAwareServlet {

    @Override
    @SuppressWarnings("UnstableApiUsage")  /* Using Guava's type which hasn't changed since 2012. */
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        _debug().log("Starting to greet an army.");

        @SuppressWarnings("MagicNumber") int howManySoldiers = 1000;
        _info().log("Today's army count is %d.", howManySoldiers);

        GreetAnArmy greetAnArmy = GreetAnArmy.newBuilder()
                .setHowManySoldiers(howManySoldiers)
                .vBuild();

        long startMillis = System.currentTimeMillis();
        CountDownLatch greeted = new CountDownLatch(howManySoldiers);
        Subscription subscription = spineClient
                .asGuest()
                .onServerError((msg, error) -> {
                    _trace().log(
                            "Server was not able to `%s`: %s",
                            msg.getClass(), error
                    );
                    throw newIllegalStateException(
                            "Something went terribly wrong: %s.", Json.toCompactJson(error)
                    );
                })
                .onStreamingError(error -> _trace()
                        .withCause(error)
                        .log("gRPC streaming error occurred while observing greetings."))
                .subscribeToEvent(SaidHello.class)
                .observe(e -> {
                    String greeting = e.getGreeting();
                    _info().log("One of the soldiers was greeted: `%s`", greeting);
                    greeted.countDown();
                    if (greeted.getCount() == 0) {
                        logPerformance(howManySoldiers, startMillis);
                    }
                })
                .post();
        _debug().log("Subscribed on `SaidHello` events.");
        spineClient
                .asGuest()
                .command(greetAnArmy)
                .postAndForget();
        _debug().log("Army greeting requested.");
        try {
            _debug().log("Waiting the army to be greeted.");
            greeted.await(5, TimeUnit.MINUTES);
            _trace().log("Greeted army latch count is %d.", greeted.getCount());
        } catch (InterruptedException e) {
            throw newIllegalStateException(
                    e, "Hanged while waiting for the army to be greeted greeting :-("
            );
        }
        long durationMillis = System.currentTimeMillis() - startMillis;
        _debug().log("Unsubscribing from the greeting updates.");
        spineClient.subscriptions()
                   .cancel(subscription);
        resp.setContentType(MediaType.PLAIN_TEXT_UTF_8.type());
        resp.getWriter()
            .println(format("Army greeting took %d millis.", durationMillis));
        resp.setStatus(SC_OK);
    }

    private void logPerformance(int howManySoldiers, long startMillis) {
        long endMillis = System.currentTimeMillis();
        long durationMillis = endMillis - startMillis;
        double perSoldier = durationMillis / (double) howManySoldiers;
        _info().log("Army was greeted for %d ms. That's about %f ms per soldier.",
                    durationMillis, perSoldier);
    }
}
