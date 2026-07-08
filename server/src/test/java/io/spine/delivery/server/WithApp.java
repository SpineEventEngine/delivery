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

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base for test classes that need a running {@link App}.
 */
public abstract class WithApp {

    /**
     * A free port picked per test instance, so that concurrently running servers — such as the
     * {@code simple-server} app during a parallel build — never clash on a shared fixed port.
     */
    private final int port = freePort();

    private final App app = new App(port);

    private ShardSessionRegistryServiceBlockingStub sessionRegistry;

    private AdminServiceBlockingStub adminServiceBlocking;

    private AdminServiceStub adminService;

    private ManagedChannel channel;

    private Client client;

    @BeforeEach
    void startApp() {
        app.start();
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
    protected ManagedChannel localChannel() {
        return ManagedChannelBuilder
                .forAddress(App.HOST, port)
                .usePlaintext()
                .build();
    }

    /**
     * Returns the port at which the {@link App} under test is exposed.
     */
    protected final int port() {
        return port;
    }

    /**
     * Reserves a free ephemeral port from the operating system.
     */
    private static int freePort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to reserve a free port for the test gRPC server.", e);
        }
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
