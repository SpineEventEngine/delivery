/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.delivery.server;

import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.spine.client.Client;
import io.spine.environment.Environment;
import io.spine.delivery.admin.given.BlockingMemoizingObserver;
import io.spine.delivery.admin.given.WithAckObserver;
import io.spine.delivery.admin.grpc.AdminServiceGrpc;
import io.spine.delivery.admin.grpc.AdminServiceGrpc.AdminServiceBlockingStub;
import io.spine.delivery.admin.grpc.AdminServiceGrpc.AdminServiceStub;
import io.spine.delivery.admin.grpc.ShardInfoUpdate;
import io.spine.delivery.grpc.ShardSessionRegistryServiceGrpc;
import io.spine.delivery.grpc.ShardSessionRegistryServiceGrpc.ShardSessionRegistryServiceBlockingStub;
import io.spine.server.ServerEnvironment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;

/**
 * Abstract base for test classes that need a running {@link App}.
 */
public abstract class WithApp {

    private final App app = new App();

    private ShardSessionRegistryServiceBlockingStub sessionRegistry;

    private AdminServiceBlockingStub adminServiceBlocking;

    private AdminServiceStub adminService;

    private ManagedChannel channel;

    private Client client;

    /**
     * Captures any exception thrown by {@link App#initAndStart()} on the background thread.
     *
     * <p>Without this, a failed server start is swallowed by the thread's default handler and
     * the tests proceed against a half-started (or absent) server, surfacing only as a
     * downstream {@code UNIMPLEMENTED: Method not found} when a client RPC is made. Capturing
     * the throwable here lets {@link #startApp()} fail with the actual root cause instead.
     */
    private final AtomicReference<Throwable> startupFailure = new AtomicReference<>();

    @BeforeEach
    void startApp() {
        var appThread = new Thread(app::initAndStart);
        appThread.setUncaughtExceptionHandler((thread, throwable) -> startupFailure.set(throwable));
        appThread.start();
        sleepUninterruptibly(Duration.ofSeconds(3)); // allow the server to start.
        var failure = startupFailure.get();
        if (failure != null) {
            throw new IllegalStateException(
                    "`App` failed to start; the gRPC server is not ready. See the cause.",
                    failure);
        }
    }

    @BeforeEach
    void setupClients() {
        channel = localChannel();
        sessionRegistry = ShardSessionRegistryServiceGrpc.newBlockingStub(channel);
        adminServiceBlocking = AdminServiceGrpc.newBlockingStub(channel);
        adminService = AdminServiceGrpc.newStub(channel);
        client = Client.usingChannel(channel)
                       .build();
    }

    @AfterEach
    void shutdownApp() throws InterruptedException {
        channel.shutdownNow();
        if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Cannot animate the channel!");
        }
        app.shutdown();
    }

    @AfterAll
    static void resetEnvs() {
        Environment.instance()
                .reset();
        ServerEnvironment.instance()
                .reset();
    }

    /**
     * Creates new {@code ManagedChannel} connected to the server running locally.
     */
    protected static ManagedChannel localChannel() {
        return ManagedChannelBuilder
                .forAddress(App.HOST, App.PORT)
                .usePlaintext()
                .build();
    }

    /**
     * Returns the {@code ShardSessionRegistryServiceBlockingStub} connected to the local server.
     */
    protected ShardSessionRegistryServiceBlockingStub
    sessionRegistry() {
        return sessionRegistry;
    }

    /**
     * Returns the {@code AdminServiceBlockingStub} connected to the local server.
     */
    protected AdminServiceBlockingStub adminServiceBlocking() {
        return adminServiceBlocking;
    }

    /**
     * Returns the {@code AdminServiceStub} connected to the local server.
     */
    protected AdminServiceStub adminService() {
        return adminService;
    }

    /**
     * Returns a new {@code Client} connected to the local server.
     */
    public Client client() {
        return client;
    }

    /**
     * Subscribes to the shard updates on the {@code AdminService} and returns an observer that
     * collects all updates for further assertions.
     *
     * <p>Blocks the current thread and waits for the subscription to be acknowledged before
     * returning the observer.
     */
    protected BlockingMemoizingObserver<ShardInfoUpdate> subscribeToUpdates() {
        var observer = new BlockingMemoizingObserver<ShardInfoUpdate>();
        var ackObserver = new WithAckObserver(observer);
        adminService().subscribeToShardUpdates(Empty.getDefaultInstance(), ackObserver);
        ackObserver.waitForAcknowledgment();
        return observer;
    }
}
