/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.demo;

import com.google.common.collect.Iterables;
import com.google.common.net.MediaType;
import com.google.protobuf.Duration;
import com.google.protobuf.util.Durations;
import io.spine.server.delivery.ShardIndex;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;
import static javax.servlet.http.HttpServletResponse.SC_OK;

/**
 * Provides a GET HTTP request handler which releases expired shards.
 */
@SuppressWarnings("serial")
@WebServlet(name = "Release Expired Shard", value = "/work-registry/release-expired")
public final class ReleaseExpiredShards extends ContextAwareServlet {

    @Override
    @SuppressWarnings("UnstableApiUsage" /* `MediaType` is available for around 10 years now. */)
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String inactivityMins = req.getParameter("inactivityMins");
        if (isNullOrEmpty(inactivityMins)) {
            inactivityMins = "10";
        }
        Duration inactivityPeriod = Durations.fromMinutes(Long.parseLong(inactivityMins));
        _info().log("Releasing expired shards inactive for `%s` minutes.", inactivityMins);
        Iterable<ShardIndex> releasedShards =
                workRegistry.releaseExpiredSessions(inactivityPeriod);
        int numberOfShards = Iterables.size(releasedShards);
        _info().log("Released `%d` expired shards.", numberOfShards);
        resp.setContentType(MediaType.PLAIN_TEXT_UTF_8.type());
        resp.getWriter()
            .println(format(
                    "Released `%d` expired shards which are inactive for at least `%s` minutes.",
                    numberOfShards, inactivityMins
            ));
        resp.setStatus(SC_OK);
    }
}
