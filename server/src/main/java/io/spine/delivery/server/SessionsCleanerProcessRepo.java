/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server;

import io.spine.client.Client;
import io.spine.delivery.SessionsCleaner;
import io.spine.delivery.SessionsCleanerId;
import io.spine.delivery.command.ReleaseExpiredSessions;
import io.spine.server.procman.ProcessManagerRepository;
import io.spine.server.route.CommandRouting;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A repository of {@link SessionsCleanerProcess}.
 */
final class SessionsCleanerProcessRepo
        extends ProcessManagerRepository<SessionsCleanerId, SessionsCleanerProcess, SessionsCleaner> {

    /**
     * The only cleaner in the context.
     */
    private static final SessionsCleanerId cleaner = SessionsCleanerId.newBuilder()
            .setValue("Mr. Proper")
            .build();

    private final Supplier<Client> clientSupplier;
    private @MonotonicNonNull Client client;

    /**
     * Creates a new repository with the {@code clientSupplier} to be used by the cleaner process.
     */
    SessionsCleanerProcessRepo(Supplier<Client> clientSupplier) {
        super();
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
