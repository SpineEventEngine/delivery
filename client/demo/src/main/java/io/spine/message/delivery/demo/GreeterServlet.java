/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.demo;

import com.google.common.base.Strings;
import io.spine.message.delivery.demo.command.SayHello;
import io.spine.message.delivery.demo.event.SaidHello;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

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
final class GreeterServlet extends DemoServlet {

    @Override
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
        spineClient
                .asGuest()
                .command(sayHello)
                .observe(SaidHello.class, e -> {
                    String greeting = e.getGreeting();
                    try {
                        resp.setContentType("text/plain");
                        resp.getWriter()
                            .println(greeting);
                        resp.setStatus(SC_OK);
                    } catch (IOException ex) {
                        throw newIllegalStateException(
                                ex, "Unable to write response to the caller."
                        );
                    }
                });
    }
}
