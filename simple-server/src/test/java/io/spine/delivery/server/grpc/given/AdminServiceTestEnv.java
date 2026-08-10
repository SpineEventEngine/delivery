/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server.grpc.given;

import com.google.protobuf.Any;
import com.google.protobuf.Empty;
import io.spine.base.Time;
import io.spine.client.EntityId;
import io.spine.core.Event;
import io.spine.core.EventContext;
import io.spine.core.EventId;
import io.spine.core.Version;
import io.spine.delivery.admin.grpc.ShardInfo;
import io.spine.delivery.admin.grpc.ShardInfoUpdate;
import io.spine.delivery.admin.grpc.ShardStatus;
import io.spine.delivery.command.PickUpShard;
import io.spine.delivery.command.ReleaseShard;
import io.spine.delivery.command.RemoveMessage;
import io.spine.delivery.command.RemoveMessages;
import io.spine.delivery.command.WriteMessage;
import io.spine.delivery.command.WriteMessages;
import io.spine.delivery.event.ShardPickedUp;
import io.spine.delivery.server.event.TestEvent;
import io.spine.delivery.server.event.TestEventId;
import io.spine.protobuf.AnyPacker;
import io.spine.server.NodeId;
import io.spine.server.delivery.InboxId;
import io.spine.server.delivery.InboxLabel;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.InboxMessageId;
import io.spine.server.delivery.InboxMessageStatus;
import io.spine.server.delivery.InboxSignalId;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.WorkerId;

import java.util.List;
import java.util.UUID;

import static io.spine.delivery.admin.grpc.ShardStatus.PICKED;
import static io.spine.server.delivery.InboxMessageMixin.generateIdWith;

/**
 * A utility with given values for {@link AdminServiceTest} testing class.
 */
public final class AdminServiceTestEnv {

    private AdminServiceTestEnv() {
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
                .build();
    }

    /**
     * Creates a new {@code WriteMessage} command with the given {@code shardIndex}.
     *
     * <p>All other necessary parameters are predefined.
     */
    public static WriteMessage testMessage(ShardIndex shardIndex) {
        var randomTestString = UUID
                .randomUUID()
                .toString();
        var testEventId = testEventId(randomTestString);
        var inboxId = InboxId
                .newBuilder()
                .setEntityId(entityId(testEventId))
                .setTypeUrl("type.spine.io/spine.delivery.TestEvent")
                .build();
        var inboxMessage = InboxMessage
                .newBuilder()
                .setId(inboxMessageId(shardIndex))
                .setInboxId(inboxId)
                .setSignalId(inboxSignalId(randomTestString))
                .setEvent(event(testEventId))
                .setLabel(InboxLabel.REACT_UPON_EVENT)
                .setStatus(InboxMessageStatus.TO_DELIVER)
                .setWhenReceived(Time.currentTime())
                .build();
        return WriteMessage
                .newBuilder()
                .setMessage(inboxMessage)
                .build();
    }

    /**
     * Creates a new {@code ReleaseShard} request for the {@code pickedUp} shard.
     */
    public static ReleaseShard releaseShard(ShardPickedUp pickedUp) {
        return ReleaseShard
                .newBuilder()
                .setShard(pickedUp.getShard())
                .setWorker(pickedUp.getWorker())
                .build();
    }

    /**
     * Creates a new {@code ReleaseShard} command acquiring the shard index and worker
     * from the given {@code pickUpCommand}.
     */
    public static ReleaseShard releaseShard(PickUpShard pickUpCommand) {
        return ReleaseShard
                .newBuilder()
                .setShard(pickUpCommand.getShard())
                .setWorker(pickUpCommand.getWorker())
                .build();
    }

    /**
     * Creates a new {@code PickUpShard} request for the given {@code index}.
     */
    public static PickUpShard pickUpShard(ShardIndex index) {
        var uuid = UUID
                .randomUUID()
                .toString();
        var nodeId = NodeId
                .newBuilder()
                .setValue(uuid)
                .build();
        var workerId = WorkerId
                .newBuilder()
                .setValue(uuid)
                .setNodeId(nodeId)
                .build();
        return PickUpShard
                .newBuilder()
                .setShard(index)
                .setWorker(workerId)
                .build();
    }

    /**
     * Copies the given {@code message} replacing the shard index to the given {@code index}.
     */
    public static InboxMessage copyWithNewShard(InboxMessage message, ShardIndex index) {
        return message.toBuilder()
                .setId(generateIdWith(index))
                .build();
    }

    /**
     * Creates a new {@code WriteMessage} command with the given {@code message}.
     */
    public static WriteMessage writeMessage(InboxMessage message) {
        return WriteMessage.newBuilder()
                .setMessage(message)
                .build();
    }

    /**
     * Creates a new {@code RemoveMessage} command with the given {@code message}.
     */
    public static RemoveMessage removeMessage(InboxMessage message) {
        return RemoveMessage.newBuilder()
                .setMessage(message)
                .build();
    }

    /**
     * Creates a new {@code WriteMessages} command with the given messages that will be written to
     * the given {@code shard}.
     */
    public static WriteMessages
    writeMessages(ShardIndex shard, InboxMessage first, InboxMessage second, InboxMessage... rest) {
        return WriteMessages.newBuilder()
                .setShard(shard)
                .addMessage(first)
                .addMessage(second)
                .addAllMessage(List.of(rest))
                .build();
    }

    /**
     * Creates a new {@code RemoveMessages} command with the given messages that will be removed
     * from the given {@code shard}.
     */
    public static RemoveMessages removeMessages(
            ShardIndex shard,
            InboxMessage first,
            InboxMessage second,
            InboxMessage... rest) {
        return RemoveMessages.newBuilder()
                .setShard(shard)
                .addMessage(first)
                .addMessage(second)
                .addAllMessage(List.of(rest))
                .build();
    }

    /**
     * Creates a new {@code Empty} request.
     */
    public static Empty request() {
        return Empty.getDefaultInstance();
    }

    /**
     * Create a new {@code ShardInfoUpdate} indicating that the shard with the given {@code index}
     * is picked.
     *
     * <p>Does not set the {@code whenLastPicked} field.
     *
     * @implNote Even though the {@code whenLastPicked} field is not required by definition
     *         and {@code vBuild()} would work as well, we use {@code buildPartial()} to indicate
     *         that the object create with such parameters is not complete.
     */
    public static ShardInfoUpdate shardPickedWithoutTime(ShardIndex index) {
        return ShardInfoUpdate
                .newBuilder()
                .setIndex(index)
                .setNewStatus(PICKED)
                .buildPartial();
    }

    /**
     * Creates a new {@code Event} with the given {@code id}.
     */
    private static Event event(TestEventId id) {
        var eventMessage = TestEvent
                .newBuilder()
                .setId(id)
                .build();
        var eventId = EventId
                .newBuilder()
                .setValue(id.getUuid())
                .build();
        var version = Version
                .newBuilder()
                .setNumber(1)
                .setTimestamp(Time.currentTime())
                .build();
        var eventContext = EventContext
                .newBuilder()
                .setTimestamp(version.getTimestamp())
                .setVersion(version)
                .setProducerId(AnyPacker.pack(id))
                .build();
        return Event
                .newBuilder()
                .setId(eventId)
                .setMessage(AnyPacker.pack(eventMessage))
                .setContext(eventContext)
                .build();
    }

    /**
     * Creates new {@code InboxSignalId} with the given {@code value}.
     */
    private static InboxSignalId inboxSignalId(String value) {
        return InboxSignalId
                .newBuilder()
                .setValue(value)
                .build();
    }

    /**
     * Creates a new {@code InboxMessageId} with the given {@code index} and a new random UUID.
     */
    private static InboxMessageId inboxMessageId(ShardIndex index) {
        var uuid = UUID
                .randomUUID()
                .toString();
        return InboxMessageId
                .newBuilder()
                .setUuid(uuid)
                .setIndex(index)
                .build();
    }

    /**
     * Creates new {@code EntityId} with the given {@code eventId}.
     */
    private static EntityId entityId(TestEventId eventId) {
        return EntityId
                .newBuilder()
                .setId(Any.pack(eventId))
                .build();
    }

    /**
     * Creates new {@code TestEventId} with the given {@code value}.
     */
    private static TestEventId testEventId(String value) {
        return TestEventId
                .newBuilder()
                .setUuid(value)
                .build();
    }
}
