/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.admin

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.ServerBuilder
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.spine.delivery.admin.given.LocalAdminService
import io.spine.delivery.admin.grpc.AdminServiceGrpc
import io.spine.delivery.admin.grpc.ShardInfoList
import io.spine.delivery.admin.grpc.ShardInfoUpdate
import io.spine.delivery.admin.grpc.SubscriptionResponse
import io.spine.type.toCompactJson
import java.time.Duration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux

@DisplayName("`AdminController` should")
internal class AdminControllerSpec {

    private lateinit var server: Server
    private lateinit var channel: ManagedChannel

    @AfterEach
    fun stopServer() {
        channel.shutdownNow()
        server.shutdownNow()
        server.awaitTermination()
    }

    /**
     * Creates a controller connected to the given service running
     * on a gRPC server with an ephemeral port.
     */
    private fun controllerBackedBy(service: LocalAdminService): AdminController {
        server = ServerBuilder.forPort(0)
            .addService(service)
            .build()
            .start()
        channel = ManagedChannelBuilder.forAddress(LOCALHOST, server.port)
            .usePlaintext()
            .build()
        return AdminController(AdminServiceGrpc.newBlockingStub(channel))
    }

    @Test
    fun `return shard info as compact JSON`() {
        val shardInfo = ShardInfoList.getDefaultInstance()
        val controller = controllerBackedBy(
            LocalAdminService.onGetShardInfo {
                it.onNext(shardInfo)
                it.onCompleted()
            }
        )

        controller.shardInfo() shouldBe shardInfo.toCompactJson()
    }

    @Test
    fun `stream shard updates as events, skipping the subscription acknowledgement`() {
        val payload = ShardInfoUpdate.getDefaultInstance()
        val update = SubscriptionResponse.newBuilder()
            .setUpdate(payload)
            .build()
        val acknowledgement = SubscriptionResponse.getDefaultInstance()
        val controller = controllerBackedBy(
            LocalAdminService.onSubscribeToShardUpdates {
                it.onNext(acknowledgement)
                it.onNext(update)
                it.onNext(update)
                it.onCompleted()
            }
        )

        val events = Flux.from(controller.subscribeOnShardUpdates())
            .collectList()
            .block(TIMEOUT)!!

        events shouldHaveSize 2
        events.map { it.data } shouldContainExactly
                listOf(payload.toCompactJson(), payload.toCompactJson())
        events.map { it.id }.toSet() shouldHaveSize 2
    }

    private companion object {
        const val LOCALHOST = "127.0.0.1"
        val TIMEOUT: Duration = Duration.ofSeconds(20)
    }
}
