/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.admin;

import com.google.protobuf.Empty;
import io.grpc.Context;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.sse.Event;
import io.micronaut.security.annotation.Secured;
import io.spine.delivery.admin.grpc.AdminServiceGrpc.AdminServiceBlockingStub;
import io.spine.delivery.admin.grpc.ShardInfoUpdate;
import io.spine.delivery.admin.grpc.SubscriptionResponse;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.Iterator;
import java.util.function.Function;

import static com.google.common.collect.Iterators.filter;
import static com.google.common.collect.Iterators.transform;
import static io.micronaut.security.rules.SecurityRule.IS_AUTHENTICATED;
import static io.spine.type.Json.toCompactJson;
import static java.util.UUID.randomUUID;

/**
 * Micronaut controller for the {@code /admin} URL path.
 */
@Controller("/admin")
@Secured(IS_AUTHENTICATED)
final class AdminController {

    /**
     * Empty request that is used to invoke gRPC methods of {@code AdminService}.
     *
     * <p>Requests don't need any parameters right now but {@code .proto} gRPC definition requires
     * some input parameters anyway.
     */
    private static final Empty REQUEST = Empty.getDefaultInstance();

    private final AdminServiceBlockingStub adminService;

    @Inject
    AdminController(AdminServiceBlockingStub adminService) {
        this.adminService = adminService;
    }

    @Get("/shardInfo")
    @Produces(MediaType.TEXT_JSON)
    String shardInfo() {
        return toCompactJson(adminService.getShardInfo(REQUEST));
    }

    @Get("/shardUpdates")
    @Produces(MediaType.TEXT_EVENT_STREAM)
    public Publisher<Event<String>> subscribeOnShardUpdates() throws Exception {
        return withGrpcContext((context) -> {
            var responses = adminService.subscribeToShardUpdates(REQUEST);
            var updates = updatesOnly(responses);
            return Flux.fromIterable(() -> transform(updates, AdminController::toEvent))
                       .doFinally(signal -> context.close());
        });
    }

    /**
     * Returns an {@code Iterator} containing only {@code ShardInfoUpdate}s, omitting
     * the acknowledgement responses.
     */
    private static Iterator<ShardInfoUpdate> updatesOnly(Iterator<SubscriptionResponse> responses) {
        return transform(
                filter(responses, SubscriptionResponse::hasUpdate),
                SubscriptionResponse::getUpdate
        );
    }

    /**
     * Creates a new {@code Event} converting the given {@code update} to compact JSON
     * and generating a new {@code UUID} for the event.
     */
    private static Event<String> toEvent(ShardInfoUpdate update) {
        var uuid = randomUUID().toString();
        return Event.of(toCompactJson(update))
                    .id(uuid);
    }

    /**
     * Calls the given {@code call} with the current gRPC {@link Context.CancellableContext}
     * and returns result of the call.
     *
     * <p>Doesn't close the context after the call execution, the caller must close the context
     * when it will be appropriate according to business logic of the {@code call}.
     */
    private static <T> T
    withGrpcContext(Function<Context.CancellableContext, T> call) throws Exception {
        @SuppressWarnings("resource") // Cannot close context before the call well be completed.
        var context = Context
                .current()
                .withCancellation();
        return context.call(() -> call.apply(context));
    }
}
