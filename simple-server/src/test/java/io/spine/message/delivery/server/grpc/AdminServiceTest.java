/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

import com.google.protobuf.Empty;
import io.spine.json.Json;
import io.spine.logging.Logging;
import io.spine.message.delivery.admin.given.FutureMemoizingObserver;
import io.spine.message.delivery.admin.given.WithAckObserver;
import io.spine.message.delivery.admin.grpc.ShardInfoUpdate;
import io.spine.message.delivery.command.PickUpShard;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.server.WithApp;
import io.spine.server.delivery.ShardIndex;
import io.spine.test.message.delivery.server.Something;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Future;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.message.delivery.admin.ShardInfoUpdates.messagesCountChangedTo;
import static io.spine.message.delivery.admin.ShardInfoUpdates.shardUnpicked;
import static io.spine.message.delivery.admin.given.SubscriptionAssertions.assertContains;
import static io.spine.message.delivery.admin.given.SubscriptionAssertions.assertHasNoError;
import static io.spine.message.delivery.admin.given.SubscriptionAssertions.assertUpdatesIn;
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
import static io.spine.message.delivery.server.grpc.given.AdminServiceTestEnv.shardPickedWithoutTime;
import static io.spine.message.delivery.server.grpc.given.AdminServiceTestEnv.testMessage;
import static io.spine.message.delivery.server.grpc.given.AdminServiceTestEnv.writeMessage;
import static io.spine.message.delivery.server.grpc.given.AdminServiceTestEnv.writeMessages;
import static io.spine.server.delivery.DeliveryStrategy.newIndex;

@Tag("admin")
@DisplayName("`AdminService` should")
final class AdminServiceTest extends WithApp implements Logging {

    @Test
    @DisplayName("get current information about shards")
    void getShardInfo() {
        ShardIndex shard1 = newIndex(1, 5);
        ShardIndex shard2 = newIndex(2, 5);
        ShardIndex shard3 = newIndex(3, 5);
        ShardIndex shard4 = newIndex(4, 5);

        syncInboxService().writeOne(testMessage(shard1));
        syncInboxService().writeOne(testMessage(shard2));

        syncShardService().pickShard(pickUpShard(shard2));
        ShardPickedUp picked = syncShardService().pickShard(pickUpShard(shard3));
        syncShardService().pickShard(pickUpShard(shard4));
        syncShardService().releaseSession(releaseShard(picked));

        var actual = syncAdminService()
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
        ShardIndex index = newIndex(1, 5);
        var observer = subscribeToUpdates();

        Future<ShardInfoUpdate> future = observer.nextOnNext();
        System.out.printf("+ + Picking shard... `%s` \n", Json.toCompactJson(index));
        syncShardService().pickShard(pickUpShard(index));
        System.out.printf("+ + Shard picked! `%s` \n", Json.toCompactJson(index));

        ShardInfoUpdate expected = shardPickedWithoutTime(index);

        assertContains(future, expected);
        assertHasNoError(observer);
        assertUpdatesIn(observer).containsExactly(expected);
    }

    @Test
    @DisplayName("notify if shard is released")
    void notifyUnpicked() {

        ShardIndex index = newIndex(1, 5);
        var observer = subscribeToUpdates();

        var pickedFuture =
                observer.nextOnNextMatching(update -> update.getNewStatus() == PICKED);
        var notPickedFuture =
                observer.nextOnNextMatching(update -> update.getNewStatus() == NOT_PICKED);

        PickUpShard pickUpShard = pickUpShard(index);
        ShardPickedUp pickedUp = syncShardService().pickShard(pickUpShard);
        syncShardService().releaseSession(releaseShard(pickedUp));

        ShardInfoUpdate pickedUpdate = shardPickedWithoutTime(index);
        ShardInfoUpdate unpickedUpdate = shardUnpicked(index);

        assertContains(pickedFuture, pickedUpdate);
        assertContains(notPickedFuture, unpickedUpdate);
        assertHasNoError(observer);
        assertUpdatesIn(observer).containsExactly(pickedUpdate, unpickedUpdate);
    }

    @Test
    @DisplayName("notify when message is written")
    void notifyMessageWritten() {
        ShardIndex index = newIndex(1, 5);
        var observer = subscribeToUpdates();
        var messageWrittenFuture = observer.nextOnNext();

        var message = copyWithNewShard(toDeliver(newUuid(), TypeUrl.of(Something.class)), index);

        System.out.printf("+ + Writing message: `%s`\n", Json.toCompactJson(message));
        syncInboxService().writeOne(writeMessage(message));
        System.out.printf("+ + Message written: `%s`\n", Json.toCompactJson(message));

        ShardInfoUpdate messageWritten = messagesCountChangedTo(index, 1);

        assertContains(messageWrittenFuture, messageWritten);
        assertHasNoError(observer);
        System.out.printf("+ + Asserting notifications.\n");
        assertUpdatesIn(observer).containsExactly(messageWritten);
    }

    @Test
    @DisplayName("notify when message is removed")
    void notifyMessageRemoved() {
        ShardIndex index = newIndex(1, 5);
        var observer = subscribeToUpdates();
        var messageWrittenFuture =
                observer.nextOnNextMatching(update -> update.getNewMessagesCount() == 1);
        var messageRemovedFuture =
                observer.nextOnNextMatching(update -> update.getNewMessagesCount() == 0);

        var message = copyWithNewShard(toDeliver(newUuid(), TypeUrl.of(Something.class)), index);

        System.out.printf("+ + Writing message: `%s`\n", Json.toCompactJson(message));
        syncInboxService().writeOne(writeMessage(message));
        System.out.printf("+ + Message written: `%s`\n", Json.toCompactJson(message));
        System.out.printf("+ + Removing message: `%s`\n", Json.toCompactJson(message));
        syncInboxService().removeOne(removeMessage(message));
        System.out.printf("+ + Message removed: `%s`\n", Json.toCompactJson(message));

        ShardInfoUpdate messageWritten = messagesCountChangedTo(index, 1);
        ShardInfoUpdate messageRemoved = messagesCountChangedTo(index, 0);

        assertContains(messageWrittenFuture, messageWritten);
        assertContains(messageRemovedFuture, messageRemoved);
        assertHasNoError(observer);
        System.out.printf("+ + Asserting notifications.\n");
        assertUpdatesIn(observer).containsExactly(messageWritten, messageRemoved);
    }

    @Test
    @DisplayName("notify when multiple messages are written")
    void notifyMessagesWritten() {
        ShardIndex index = newIndex(1, 5);
        var observer = subscribeToUpdates();

        var message1 = copyWithNewShard(toDeliver(newUuid(), TypeUrl.of(Something.class)), index);
        var message2 = copyWithNewShard(toDeliver(newUuid(), TypeUrl.of(Something.class)), index);

        var message1WrittenFuture =
                observer.nextOnNextMatching(update -> update.getNewMessagesCount() == 1);
        var message2WrittenFuture =
                observer.nextOnNextMatching(update -> update.getNewMessagesCount() == 2);

        System.out.printf("+ + Writing messages: `%s`, `%s`\n", Json.toCompactJson(message1),
                          Json.toCompactJson(message2));
        syncInboxService().writeMany(writeMessages(index, message1, message2));
        System.out.printf("+ + Messages written: `%s`, `%s`\n", Json.toCompactJson(message1),
                          Json.toCompactJson(message2));

        ShardInfoUpdate message1Written = messagesCountChangedTo(index, 1);
        ShardInfoUpdate message2Written = messagesCountChangedTo(index, 2);

        assertContains(message1WrittenFuture, message1Written);
        assertContains(message2WrittenFuture, message2Written);
        assertHasNoError(observer);
        System.out.printf("+ + Asserting notifications.\n");
        assertUpdatesIn(observer).containsExactly(message1Written, message2Written);
    }

    @Test
    @DisplayName("notify when multiple messages are removed")
    void notifyMessagesRemoved() {
        ShardIndex index = newIndex(1, 5);
        var observer = subscribeToUpdates();

        var message1 = copyWithNewShard(toDeliver(newUuid(), TypeUrl.of(Something.class)), index);
        var message2 = copyWithNewShard(toDeliver(newUuid(), TypeUrl.of(Something.class)), index);

        var message1WrittenFuture =
                observer.nextOnNextMatching(update -> update.getNewMessagesCount() == 1);
        var message2WrittenFuture =
                observer.nextOnNextMatching(update -> update.getNewMessagesCount() == 2);

        System.out.printf("+ + Writing messages: `%s`, `%s`\n", Json.toCompactJson(message1),
                          Json.toCompactJson(message2));
        syncInboxService().writeMany(writeMessages(index, message1, message2));
        System.out.printf("+ + Messages written: `%s`, `%s`\n", Json.toCompactJson(message1),
                          Json.toCompactJson(message2));
        ShardInfoUpdate message1Written = messagesCountChangedTo(index, 1);
        ShardInfoUpdate message2Written = messagesCountChangedTo(index, 2);
        assertContains(message1WrittenFuture, message1Written);
        assertContains(message2WrittenFuture, message2Written);

        var message1RemovedFuture =
                observer.nextOnNextMatching(update -> update.getNewMessagesCount() == 1);
        var message2RemovedFuture =
                observer.nextOnNextMatching(update -> update.getNewMessagesCount() == 0);

        System.out.printf("+ + Removing messages: `%s`, `%s`\n", Json.toCompactJson(message1),
                          Json.toCompactJson(message2));
        syncInboxService().removeMany(removeMessages(index, message1, message2));
        System.out.printf("+ + Messages removed: `%s`, `%s`\n", Json.toCompactJson(message1),
                          Json.toCompactJson(message2));

        ShardInfoUpdate message1Removed = messagesCountChangedTo(index, 1);
        ShardInfoUpdate message2Removed = messagesCountChangedTo(index, 0);

        assertContains(message1RemovedFuture, message1Removed);
        assertContains(message2RemovedFuture, message2Removed);
        assertHasNoError(observer);
        System.out.printf("+ + Asserting notifications.\n");
        assertUpdatesIn(observer).containsExactly(
                message1Written,
                message2Written,
                message1Removed,
                message2Removed
        );
    }

    /**
     * Subscribes to the shard updates on the {@code AdminService} and returns an observer that
     * collects all updates for further assertions.
     *
     * <p>Also waits for {@link #SLEEP_SECONDS} to ensure that the subscription is created
     * on the server.
     */
    private FutureMemoizingObserver<ShardInfoUpdate> subscribeToUpdates() {
        var observer = new FutureMemoizingObserver<ShardInfoUpdate>() {
            @Override
            public void onNext(ShardInfoUpdate value) {
                System.out.printf("+= += onNext(), `%s`\n", Json.toCompactJson(value));
                super.onNext(value);
            }

            @Override
            public void onError(Throwable t) {
                System.out.printf("+= += onError(), `%s`\n", t);
                super.onError(t);
            }

            @Override
            public void onCompleted() {
                System.out.printf("+= += Observer completed.\n");
                super.onCompleted();
            }
        };
        System.out.printf("+ + Subscribing to updates...\n");
        WithAckObserver ackObserver = new WithAckObserver(observer);
        adminService().subscribeToShardUpdates(Empty.getDefaultInstance(), ackObserver);
        System.out.printf("+ + received a response but waiting for an ack from the server...\n");
        ackObserver.waitForAcknowledgment();
        System.out.printf("+ + Subscribed.\n");
        return observer;
    }
}
