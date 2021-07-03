/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client.given;

import com.google.protobuf.Timestamp;
import io.spine.base.Identifier;
import io.spine.base.Time;
import io.spine.client.EntityId;
import io.spine.core.Command;
import io.spine.server.delivery.DeliveryStrategy;
import io.spine.server.delivery.InboxId;
import io.spine.server.delivery.InboxLabel;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.InboxMessageId;
import io.spine.server.delivery.InboxMessageMixin;
import io.spine.server.delivery.InboxMessageStatus;
import io.spine.server.delivery.InboxSignalId;
import io.spine.server.delivery.ShardIndex;
import io.spine.test.message.delivery.server.DoSmth;
import io.spine.test.message.delivery.server.Something;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.type.TypeUrl;

import static io.spine.server.delivery.InboxMessageStatus.DELIVERED;
import static io.spine.server.delivery.InboxMessageStatus.TO_CATCH_UP;
import static io.spine.server.delivery.InboxMessageStatus.TO_DELIVER;

/**
 * Provides the instances of {@link InboxMessage}s to use as a data in tests.
 */
public final class TestInboxMessages {

    private static final TestActorRequestFactory factory =
            new TestActorRequestFactory(TestInboxMessages.class);

    /**
     * Does not allow to instantiate this utility class.
     */
    private TestInboxMessages() {
    }

    /**
     * Copies the original {@code InboxMessage} but generates a new {@link InboxMessageId}
     * for the copy.
     */
    public static InboxMessage copyWithNewId(InboxMessage original) {
        return original.toBuilder()
                .setId(InboxMessageMixin.generateIdWith(original.shardIndex()))
                .vBuild();
    }

    /**
     * Copies the original {@code InboxMessage} but sets the specified status to the copy.
     */
    public static InboxMessage copyWithStatus(InboxMessage original, InboxMessageStatus newStatus) {
        return original.toBuilder()
                .setStatus(newStatus)
                .vBuild();
    }

    /**
     * Generates a new {@code InboxMessage} in
     * {@link InboxMessageStatus#TO_CATCH_UP TO_CATCH_UP} status.
     *
     * @param targetId
     *         the ID of the target for the generated message
     * @param targetType
     *         the type URL of the target for the generated message
     * @return an instance of the generated message
     */
    public static InboxMessage catchingUp(Object targetId, TypeUrl targetType) {
        return newMessage(targetId, targetType, TO_CATCH_UP);
    }

    /**
     * Generates a new {@code InboxMessage} in
     * {@link InboxMessageStatus#TO_CATCH_UP TO_CATCH_UP} status
     * and the receiving time specified.
     *
     * @param targetId
     *         the ID of the target for the generated message
     * @param targetType
     *         the type URL of the target for the generated message
     * @param whenReceived
     *         the message receiving time to set
     * @return an instance of the generated message
     */
    public static InboxMessage catchingUp(Object targetId,
                                          TypeUrl targetType,
                                          Timestamp whenReceived) {
        return newMessage(targetId, targetType, TO_CATCH_UP)
                .toBuilder()
                .setWhenReceived(whenReceived)
                .vBuild();
    }

    /**
     * Generates a new {@code InboxMessage} in
     * {@link InboxMessageStatus#TO_DELIVER TO_DELIVER} status.
     *
     * @param targetId
     *         the ID of the target for the generated message
     * @param targetType
     *         the type URL of the target for the generated message
     * @return an instance of the generated message
     */
    public static InboxMessage toDeliver(Object targetId, TypeUrl targetType) {
        return newMessage(targetId, targetType, TO_DELIVER);
    }

    /**
     * Generates a new {@code InboxMessage} in
     * {@link InboxMessageStatus#TO_DELIVER TO_DELIVER} status and
     * the receiving time specified.
     *
     * @param targetId
     *         the ID of the target for the generated message
     * @param targetType
     *         the type URL of the target for the generated message
     * @param whenReceived
     *         the message receiving time to set
     * @return an instance of the generated message
     */
    public static InboxMessage toDeliver(Object targetId,
                                         TypeUrl targetType,
                                         Timestamp whenReceived) {
        return newMessage(targetId, targetType, TO_DELIVER)
                .toBuilder()
                .setWhenReceived(whenReceived)
                .vBuild();
    }

    /**
     * Generates a new {@code InboxMessage} in
     * {@link InboxMessageStatus#TO_DELIVER TO_DELIVER} status and
     * the receiving time specified in the specified {@code shard}.
     *
     * @param whenReceived
     *         the message receiving time to set
     * @param shard
     *         the shard the message belongs to
     * @return an instance of the generated message
     */
    public static InboxMessage toDeliver(Timestamp whenReceived, ShardIndex shard) {
        InboxMessage message =
                newMessage(Identifier.newUuid(), TypeUrl.of(Something.class), TO_DELIVER);
        return message
                .toBuilder()
                .setWhenReceived(whenReceived)
                .setId(message.getId().toBuilder().setIndex(shard))
                .vBuild();
    }

    /**
     * Generates a new {@code InboxMessage} in
     * {@link InboxMessageStatus#DELIVERED DELIVERED} status.
     *
     * @param targetId
     *         the ID of the target for the generated message
     * @param targetType
     *         the type URL of the target for the generated message
     * @return an instance of the generated message
     */
    public static InboxMessage delivered(Object targetId, TypeUrl targetType) {
        return newMessage(targetId, targetType, DELIVERED);
    }

    /**
     * Generates a new {@code InboxMessage} in
     * {@link InboxMessageStatus#TO_DELIVER TO_DELIVER} status on top
     * of the passed in command and sets the receiving time according to the passed value.
     */
    public static InboxMessage toDeliver(Command source, Timestamp whenReceived) {
        InboxId inboxId = newInboxId("some-target", TypeUrl.of(Something.class));
        InboxMessage message = messageReceivedAt(source, TO_DELIVER, inboxId, whenReceived);
        return message;
    }

    private static InboxMessage newMessage(Object target, TypeUrl type, InboxMessageStatus status) {
        Command command = generateCommand(target);
        InboxId inboxId = newInboxId(target, type);
        InboxMessage message = messageReceivedAt(command, status, inboxId, Time.currentTime());
        return message;
    }

    private static InboxMessage messageReceivedAt(Command command,
                                                  InboxMessageStatus status,
                                                  InboxId inboxId,
                                                  Timestamp whenReceived) {
        ShardIndex index = DeliveryStrategy.newIndex(0, 1);
        InboxMessageId id = InboxMessageMixin.generateIdWith(index);
        InboxSignalId.Builder signalId = InboxSignalId.newBuilder()
                .setValue(command.getId()
                                 .value());
        InboxMessage result = InboxMessage.newBuilder()
                .setId(id)
                .setStatus(status)
                .setCommand(command)
                .setInboxId(inboxId)
                .setSignalId(signalId)
                .setLabel(InboxLabel.HANDLE_COMMAND)
                .setWhenReceived(whenReceived)
                .vBuild();
        return result;
    }

    private static InboxId newInboxId(Object targetId, TypeUrl targetType) {
        return InboxId.newBuilder()
                .setEntityId(
                        EntityId.newBuilder()
                                .setId(Identifier.pack(targetId))
                                .vBuild()
                )
                .setTypeUrl(targetType.value())
                .vBuild();
    }

    private static Command generateCommand(Object targetId) {
        DoSmth commandMessage = DoSmth.newBuilder()
                .setId("some-id-" + targetId)
                .setWhatToDo("Something!")
                .vBuild();
        return factory.createCommand(commandMessage);
    }
}
