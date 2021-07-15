/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.spine.client.Client;
import io.spine.message.delivery.SessionsCleaner;
import io.spine.message.delivery.SessionsCleanerId;
import io.spine.message.delivery.command.ReleaseExpiredSessions;
import io.spine.server.procman.ProcessManagerRepository;
import io.spine.server.route.CommandRouting;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

public final class SessionsCleanerProcessRepo
        extends ProcessManagerRepository<SessionsCleanerId, SessionsCleanerProcess, SessionsCleaner> {

    /**
     * The only cleaner in the context.
     */
    static final SessionsCleanerId cleaner = SessionsCleanerId.newBuilder()
            .setValue("Mr. Proper")
            .vBuild();

    private final Supplier<Client> clientSupplier;
    private @MonotonicNonNull Client client;

    public SessionsCleanerProcessRepo(Supplier<Client> clientSupplier) {
        this.clientSupplier = checkNotNull(clientSupplier);
    }

    @Override
    protected void setupCommandRouting(CommandRouting<SessionsCleanerId> routing) {
        routing.route(ReleaseExpiredSessions.class, (cmd, ctx) -> cleaner);
    }

    @Override
    protected void configure(SessionsCleanerProcess processManager) {
        processManager.setClient(client());
    }

    private Client client() {
        if (client == null) {
            client = clientSupplier.get();
        }
        return client;
    }
}
