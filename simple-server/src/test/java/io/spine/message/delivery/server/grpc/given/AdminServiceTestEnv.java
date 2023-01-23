/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc.given;

import com.google.protobuf.Any;
import com.google.protobuf.Empty;
import io.spine.base.Time;
import io.spine.client.EntityId;
import io.spine.core.Event;
import io.spine.core.EventContext;
import io.spine.core.EventId;
import io.spine.core.Version;
import io.spine.message.delivery.admin.grpc.ShardInfo;
import io.spine.message.delivery.admin.grpc.ShardStatus;
import io.spine.message.delivery.command.PickUpShard;
import io.spine.message.delivery.command.ReleaseShard;
import io.spine.message.delivery.command.WriteMessage;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.server.event.TestEvent;
import io.spine.message.delivery.server.event.TestEventId;
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

import java.util.UUID;

/**
 * A utility with given values for {@link AdminServiceTest} testing class.
 */
public class AdminServiceTestEnv {

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
                .vBuild();
    }

    public static WriteMessage testMessage(ShardIndex shardIndex) {
        String randomTestString = UUID
                .randomUUID()
                .toString();
        TestEventId id = TestEventId
                .newBuilder()
                .setValue(randomTestString)
                .vBuild();
        TestEvent eventMessage = TestEvent
                .newBuilder()
                .setId(id)
                .vBuild();
        String uuid = UUID
                .randomUUID()
                .toString();
        InboxMessageId inboxMessageId = InboxMessageId
                .newBuilder()
                .setUuid(uuid)
                .setIndex(shardIndex)
                .vBuild();
        EntityId entityId = EntityId
                .newBuilder()
                .setId(Any.pack(id))
                .vBuild();
        InboxId inboxId = InboxId
                .newBuilder()
                .setEntityId(entityId)
                .setTypeUrl("type.spine.io/spine.message.delivery.TestEvent")
                .vBuild();
        EventId eventId = EventId
                .newBuilder()
                .setValue(randomTestString)
                .vBuild();
        Version version = Version
                .newBuilder()
                .setNumber(1)
                .setTimestamp(Time.currentTime())
                .vBuild();
        EventContext eventContext = EventContext
                .newBuilder()
                .setTimestamp(version.getTimestamp())
                .setVersion(version)
                .setProducerId(AnyPacker.pack(id))
                .vBuild();
        Event event = Event
                .newBuilder()
                .setId(eventId)
                .setMessage(AnyPacker.pack(eventMessage))
                .setContext(eventContext)
                .vBuild();
        InboxSignalId inboxSignalId = InboxSignalId
                .newBuilder()
                .setValue(randomTestString)
                .vBuild();
        InboxMessage inboxMessage = InboxMessage
                .newBuilder()
                .setId(inboxMessageId)
                .setInboxId(inboxId)
                .setSignalId(inboxSignalId)
                .setEvent(event)
                .setLabel(InboxLabel.REACT_UPON_EVENT)
                .setStatus(InboxMessageStatus.TO_DELIVER)
                .setWhenReceived(Time.currentTime())
                .vBuild();
        return WriteMessage
                .newBuilder()
                .setMessage(inboxMessage)
                .vBuild();
    }

    /**
     * Creates a new {@code ReleaseShard} request for the {@code pickedUp} shard.
     */
    public static ReleaseShard releaseShard(ShardPickedUp pickedUp) {
        return ReleaseShard
                .newBuilder()
                .setShard(pickedUp.getShard())
                .setWorker(pickedUp.getWorker())
                .vBuild();
    }

    /**
     * Creates a new {@code PickUpShard} request for the given {@code index}.
     */
    public static PickUpShard pickUpShard(ShardIndex index) {
        String uuid = UUID
                .randomUUID()
                .toString();
        NodeId nodeId = NodeId
                .newBuilder()
                .setValue(uuid)
                .vBuild();
        WorkerId workerId = WorkerId
                .newBuilder()
                .setValue(uuid)
                .setNodeId(nodeId)
                .vBuild();
        return PickUpShard
                .newBuilder()
                .setShard(index)
                .setWorker(workerId)
                .vBuild();
    }

    public static Empty request() {
        return Empty
                .newBuilder()
                .build();
    }

}
