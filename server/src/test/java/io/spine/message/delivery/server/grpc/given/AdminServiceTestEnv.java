/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc.given;

import com.google.protobuf.Empty;
import io.spine.message.delivery.admin.grpc.ShardInfo;
import io.spine.message.delivery.admin.grpc.ShardStatus;
import io.spine.message.delivery.command.PickUpShard;
import io.spine.message.delivery.command.ReleaseShard;
import io.spine.message.delivery.command.WriteMessage;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.server.NodeId;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.WorkerId;

import java.util.UUID;

import static io.spine.server.delivery.InboxMessageMixin.generateIdWith;

/**
 * A utility with given values for {@link AdminServiceTest} testing class.
 */
public final class AdminServiceTestEnv {

    private AdminServiceTestEnv() {
    }

    /**
     * Creates a new {@code PickUpShard} command with the given shard {@code index} and randomly
     * generated {@code WorkerId}.
     */
    public static PickUpShard pickUpShard(ShardIndex index) {
        String uuid = UUID
                .randomUUID()
                .toString();
        NodeId nodeId = NodeId
                .newBuilder()
                .setValue(uuid)
                .vBuild();
        WorkerId worker = WorkerId
                .newBuilder()
                .setNodeId(nodeId)
                .setValue(uuid)
                .vBuild();
        return PickUpShard.newBuilder()
                .setShard(index)
                .setWorker(worker)
                .vBuild();
    }

    /**
     * Creates a new {@code WriteMessage} command with the given {@code message}.
     */
    public static WriteMessage writeMessage(InboxMessage message) {
        return WriteMessage.newBuilder()
                .setMessage(message)
                .vBuild();
    }

    /**
     * Copies the given {@code message} replacing the shard index to the given {@code index}.
     */
    public static InboxMessage copyWithNewShard(InboxMessage message, ShardIndex index) {
        return message.toBuilder()
                .setId(generateIdWith(index))
                .vBuild();
    }

    /**
     * Creates a new {@code ReleaseShard} command to release the {@code pickedUp} shard.
     */
    public static ReleaseShard releasePickedUp(ShardPickedUp pickedUp){
        return ReleaseShard.newBuilder()
                .setShard(pickedUp.getShard())
                .setWorker(pickedUp.getWorker())
                .vBuild();
    }

    /**
     * Creates a new {@code Empty} request.
     */
    public static Empty request() {
        return Empty.getDefaultInstance();
    }

    /**
     * Creates a new shard info with the given parameters.
     *
     * @param index
     *         index of the shard
     * @param status
     *         whether the shard is picked for processing or not
     * @param messages
     *         number of messages in the shard
     */
    public static ShardInfo shardInfo(ShardIndex index, ShardStatus status, int messages) {
        return ShardInfo.newBuilder()
                .setIndex(index)
                .setMessages(messages)
                .setStatus(status)
                .vBuild();
    }
}
