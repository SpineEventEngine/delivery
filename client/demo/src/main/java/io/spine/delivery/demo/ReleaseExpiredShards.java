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

import com.google.common.collect.Iterables;
import com.google.common.net.MediaType;
import com.google.protobuf.util.Durations;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;
import static javax.servlet.http.HttpServletResponse.SC_OK;

/**
 * Provides a GET HTTP request handler that releases expired shards.
 */
@SuppressWarnings("serial")
@WebServlet(name = "Release Expired Shard", value = "/work-registry/release-expired")
public final class ReleaseExpiredShards extends ContextAwareServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        var requestedMins = req.getParameter("inactivityMins");
        var inactivityMins = isNullOrEmpty(requestedMins) ? "10" : requestedMins;
        var inactivityPeriod = Durations.fromMinutes(Long.parseLong(inactivityMins));
        logger().atInfo().log(() -> format(
                "Releasing expired shards inactive for `%s` minutes.", inactivityMins));
        var releasedShards =
                workRegistry.releaseExpiredSessions(inactivityPeriod);
        var numberOfShards = Iterables.size(releasedShards);
        logger().atInfo().log(() -> format("Released `%d` expired shards.", numberOfShards));
        resp.setContentType(MediaType.PLAIN_TEXT_UTF_8.type());
        resp.getWriter()
            .println(format(
                    "Released `%d` expired shards which are inactive for at least `%s` minutes.",
                    numberOfShards, inactivityMins
            ));
        resp.setStatus(SC_OK);
    }
}
