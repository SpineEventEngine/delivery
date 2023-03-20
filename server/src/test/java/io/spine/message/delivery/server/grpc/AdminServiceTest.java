/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

import com.google.common.truth.Truth8;
import com.google.common.truth.extensions.proto.IterableOfProtosFluentAssertion;
import com.google.protobuf.Empty;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import io.spine.base.CommandMessage;
import io.spine.base.Time;
import io.spine.grpc.MemoizingObserver;
import io.spine.message.delivery.admin.grpc.ShardInfoUpdate;
import io.spine.message.delivery.command.PickUpShard;
import io.spine.message.delivery.server.WithApp;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.ShardIndex;
import io.spine.test.message.delivery.server.Something;
import io.spine.time.testing.FrozenMadHatterParty;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static io.spine.base.Identifier.newUuid;
import static io.spine.message.delivery.admin.ShardInfoUpdates.messagesCountChangedTo;
import static io.spine.message.delivery.admin.ShardInfoUpdates.shardPicked;
import static io.spine.message.delivery.admin.ShardInfoUpdates.shardUnpicked;
import static io.spine.message.delivery.admin.grpc.ShardStatus.NOT_PICKED;
import static io.spine.message.delivery.admin.grpc.ShardStatus.PICKED;
import static io.spine.message.delivery.server.given.TestInboxMessages.toDeliver;
import static io.spine.message.delivery.server.grpc.given.AdminServiceTestEnv.copyWithNewShard;
import static io.spine.message.delivery.server.grpc.given.AdminServiceTestEnv.pickUpShard;
import static io.spine.message.delivery.server.grpc.given.AdminServiceTestEnv.releaseShard;
import static io.spine.message.delivery.server.grpc.given.AdminServiceTestEnv.removeMessage;
import static io.spine.message.delivery.server.grpc.given.AdminServiceTestEnv.removeMessages;
import static io.spine.message.delivery.server.grpc.given.AdminServiceTestEnv.request;
import static io.spine.message.delivery.server.grpc.given.AdminServiceTestEnv.shardInfo;
import static io.spine.message.delivery.server.grpc.given.AdminServiceTestEnv.writeMessage;
import static io.spine.message.delivery.server.grpc.given.AdminServiceTestEnv.writeMessages;
import static io.spine.server.delivery.DeliveryStrategy.newIndex;
import static java.time.Duration.ofSeconds;

@DisplayName("`AdminService` should")
final class AdminServiceTest extends WithApp {

    private static final int SLEEP_SECONDS = 2;

    @Test
    @DisplayName("get current information about shards")
    void getShardInfo() {
        ShardIndex shard1 = newIndex(1, 5);
        ShardIndex shard2 = newIndex(2, 5);
        ShardIndex shard3 = newIndex(3, 5);
        ShardIndex shard4 = newIndex(4, 5);

        InboxMessage message = toDeliver(newUuid(), TypeUrl.of(Something.class));

        postToClient(writeMessage(copyWithNewShard(message, shard1)));
        postToClient(writeMessage(copyWithNewShard(message, shard2)));

        postToClient(pickUpShard(shard2));
        PickUpShard pickUpShard3 = pickUpShard(shard3);
        postToClient(pickUpShard3);
        postToClient(pickUpShard(shard4));
        postToClient(releaseShard(pickUpShard3));

        sleepUninterruptibly(ofSeconds(SLEEP_SECONDS));
        var actual = adminServiceBlocking()
                .getShardInfo(request())
                .getShardsList();

        assertThat(actual)
                .comparingExpectedFieldsOnly()
                .containsExactly(
                        shardInfo(shard1, NOT_PICKED, 1),
                        shardInfo(shard2, PICKED, 1),
                        shardInfo(shard3, NOT_PICKED, 0),
                        shardInfo(shard4, PICKED, 0)
                );
    }

    @Test
    @DisplayName("notify if shard picked")
    void notifyPicked() {
        Timestamp time = Time.currentTime();
        Time.setProvider(new FrozenMadHatterParty(time));

        ShardIndex index = newIndex(1, 5);
        var observer = subscribeToUpdates();

        postToClient(pickUpShard(index));

        ShardInfoUpdate expected = shardPicked(index, time);

        sleepUninterruptibly(ofSeconds(SLEEP_SECONDS));
        assertHasNoError(observer);
        assertUpdatesIn(observer).containsExactly(expected);
    }

    @Test
    @DisplayName("notify if shard is released")
    void notifyUnpicked() {
        Timestamp time = Time.currentTime();
        Time.setProvider(new FrozenMadHatterParty(time));

        ShardIndex index = newIndex(1, 5);
        var observer = subscribeToUpdates();

        PickUpShard pickUpShard = pickUpShard(index);
        postToClient(pickUpShard);
        postToClient(releaseShard(pickUpShard));

        ShardInfoUpdate pickedUpdate = shardPicked(index, time);
        ShardInfoUpdate unpickedUpdate = shardUnpicked(index);

        sleepUninterruptibly(ofSeconds(SLEEP_SECONDS));
        assertHasNoError(observer);
        assertUpdatesIn(observer).containsExactly(pickedUpdate, unpickedUpdate);
    }

    @Test
    @DisplayName("notify when message is written")
    void notifyMessageWritten() {
        ShardIndex index = newIndex(1, 5);
        var observer = subscribeToUpdates();

        var message = copyWithNewShard(toDeliver(newUuid(), TypeUrl.of(Something.class)), index);
        postToClient(writeMessage(message));

        ShardInfoUpdate messageWritten = messagesCountChangedTo(index, 1);

        sleepUninterruptibly(ofSeconds(SLEEP_SECONDS));
        assertHasNoError(observer);
        assertUpdatesIn(observer).containsExactly(messageWritten);
    }

    @Test
    @DisplayName("notify when message is removed")
    void notifyMessageRemoved() {
        ShardIndex index = newIndex(1, 5);
        var observer = subscribeToUpdates();

        var message = copyWithNewShard(toDeliver(newUuid(), TypeUrl.of(Something.class)), index);
        postToClient(writeMessage(message));
        postToClient(removeMessage(message));

        ShardInfoUpdate messageWritten = messagesCountChangedTo(index, 1);
        ShardInfoUpdate messageRemoved = messagesCountChangedTo(index, 0);

        sleepUninterruptibly(ofSeconds(SLEEP_SECONDS));
        assertHasNoError(observer);
        assertUpdatesIn(observer).containsExactly(messageWritten, messageRemoved);
    }

    @Test
    @DisplayName("notify when multiple messages are written")
    void notifyMessagesWritten() {
        ShardIndex index = newIndex(1, 5);
        var observer = subscribeToUpdates();

        var message1 = copyWithNewShard(toDeliver(newUuid(), TypeUrl.of(Something.class)), index);
        var message2 = copyWithNewShard(toDeliver(newUuid(), TypeUrl.of(Something.class)), index);

        postToClient(writeMessages(index, message1, message2));

        ShardInfoUpdate message1Written = messagesCountChangedTo(index, 1);
        ShardInfoUpdate message2Written = messagesCountChangedTo(index, 2);

        sleepUninterruptibly(ofSeconds(SLEEP_SECONDS));
        assertHasNoError(observer);
        assertUpdatesIn(observer).containsExactly(message1Written, message2Written);
    }

    @Test
    @DisplayName("notify when multiple messages are removed")
    void notifyMessagesRemoved() {
        ShardIndex index = newIndex(1, 5);
        var observer = subscribeToUpdates();

        var message1 = copyWithNewShard(toDeliver(newUuid(), TypeUrl.of(Something.class)), index);
        var message2 = copyWithNewShard(toDeliver(newUuid(), TypeUrl.of(Something.class)), index);

        postToClient(writeMessages(index, message1, message2));
        postToClient(removeMessages(index, message1, message2));

        ShardInfoUpdate message1Written = messagesCountChangedTo(index, 1);
        ShardInfoUpdate message2Written = messagesCountChangedTo(index, 2);
        ShardInfoUpdate message1Removed = messagesCountChangedTo(index, 1);
        ShardInfoUpdate message2Removed = messagesCountChangedTo(index, 0);

        sleepUninterruptibly(ofSeconds(SLEEP_SECONDS));
        assertHasNoError(observer);
        assertUpdatesIn(observer).containsExactly(
                message1Written,
                message2Written,
                message1Removed,
                message2Removed
        );
    }

    /**
     * Asserts that the given observer has no error.
     */
    private static <T> void assertHasNoError(MemoizingObserver<T> observer) {
        Truth8.assertThat(Optional.ofNullable(observer.getError()))
              .isEmpty();
    }

    /**
     * Starts an assertion chain for updates list stored in the given {@code observer}.
     */
    private static <T extends Message>
    IterableOfProtosFluentAssertion<T> assertUpdatesIn(MemoizingObserver<T> observer) {
        return assertThat(observer.responses()).comparingExpectedFieldsOnly();
    }

    /**
     * Subscribes to the shard updates on the {@code AdminService} and returns an observer that
     * collects all updates for further assertions.
     */
    private MemoizingObserver<ShardInfoUpdate> subscribeToUpdates() {
        var observer = new MemoizingObserver<ShardInfoUpdate>();
        adminService().subscribeToShardUpdates(Empty.getDefaultInstance(), observer);
        return observer;
    }

    /**
     * Posts the given {@code command} to the client.
     */
    @SuppressWarnings("resource") // No need to close client in test method.
    private void postToClient(CommandMessage command) {
        client().asGuest()
                .command(command)
                .postAndForget();
    }
}
