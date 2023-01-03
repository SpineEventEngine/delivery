/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.demo;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.MediaType;
import io.spine.client.Subscription;
import io.spine.message.delivery.demo.command.SayHello;
import io.spine.message.delivery.demo.event.SaidHello;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.spine.util.Exceptions.newIllegalStateException;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;

/**
 * Provides a GET HTTP request handler greets the caller.
 *
 * <p>The caller can only be greeted once. For all the subsequent calls returns an error.
 */
@SuppressWarnings("serial")
@WebServlet(name = "Greeter", value = "/greet")
public final class GreeterServlet extends ContextAwareServlet {

    @Override
    @SuppressWarnings("UnstableApiUsage" /* `MediaType` is available for around 10 years now. */)
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        _debug().log("Handling a greeting.");
        String personName = req.getParameter("name");
        if (Strings.isNullOrEmpty(personName)) {
            _info().log("No person name specified.");
            resp.sendError(
                    SC_BAD_REQUEST,
                    "The person name must be specified in the `name` parameter."
            );
            return;
        }
        _info().log("Greeting person `%s`.", personName);
        SayHello sayHello = SayHello.newBuilder()
                .setName(personName)
                .vBuild();
        CountDownLatch greeted = new CountDownLatch(1);
        long startTime = System.currentTimeMillis();
        ImmutableSet<Subscription> subscriptions = spineClient
                .asGuest()
                .command(sayHello)
                .observe(SaidHello.class, e -> {
                    long endTime = System.currentTimeMillis();
                    String greeting = e.getGreeting();
                    _info().log("Said `%s` to `%s` in %d ms.",
                                greeting, personName, (endTime - startTime));
                    try {
                        resp.setContentType(MediaType.PLAIN_TEXT_UTF_8.type());
                        resp.getWriter()
                            .println(greeting);
                        resp.setStatus(SC_OK);
                    } catch (IOException ex) {
                        throw newIllegalStateException(
                                ex, "Unable to write response to the caller."
                        );
                    } finally {
                        greeted.countDown();
                    }
                })
                .onServerError((msg, error) -> {
                    _trace().log(
                            "Server was not able to handle the command `%s`: %s",
                            msg.getClass(), error
                    );
                    greeted.countDown();
                })
                .post();
        try {
            _debug().log("Waiting for the command to path through.");
            greeted.await(30, TimeUnit.SECONDS);
            _trace().log("Greeted latch count is %d.", greeted.getCount());
        } catch (InterruptedException e) {
            throw newIllegalStateException(e, "Hanged while waiting for a greeting :-(");
        }
        _debug().log("Unsubscribing from updates.");
        subscriptions.forEach(spineClient.subscriptions()::cancel);
    }
}
