/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.admin;

import com.google.protobuf.Empty;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.spine.message.delivery.admin.grpc.AdminServiceGrpc.AdminServiceFutureStub;
import io.spine.message.delivery.admin.grpc.ShardInfoList;
import jakarta.inject.Inject;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static io.micronaut.security.rules.SecurityRule.IS_AUTHENTICATED;
import static io.spine.json.Json.toCompactJson;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Micronaut controller for the {@code /admin} URL path.
 */
@Controller("/admin")
@Secured(IS_AUTHENTICATED)
final class AdminController {

    private final AdminServiceFutureStub adminService;

    @Inject
    AdminController(AdminServiceFutureStub service) {
        adminService = service;
    }

    @Get("/shardInfo")
    @Produces(MediaType.TEXT_JSON)
    String getShardInfo() throws ExecutionException, InterruptedException, TimeoutException {
        ShardInfoList shardInfoList = adminService
                .getShardInfo(Empty.getDefaultInstance())
                .get(10, SECONDS);
        return toCompactJson(shardInfoList);
    }
}
