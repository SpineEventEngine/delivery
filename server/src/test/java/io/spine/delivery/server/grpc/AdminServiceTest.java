/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.spine.delivery.server.grpc;

import com.google.protobuf.Empty;
import io.spine.delivery.admin.given.BlockingMemoizingObserver;
import io.spine.delivery.admin.given.WithAckObserver;
import io.spine.delivery.admin.grpc.ShardInfoUpdate;
import io.spine.delivery.server.WithApp;
import io.spine.logging.WithLogging;
import io.spine.test.delivery.Something;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.delivery.admin.ShardInfoUpdates.messagesCountChangedTo;
import static io.spine.delivery.admin.ShardInfoUpdates.shardUnpicked;
import static io.spine.delivery.admin.given.SubscriptionAssertions.assertContains;
import static io.spine.delivery.admin.given.SubscriptionAssertions.assertHasNoError;
import static io.spine.delivery.admin.given.SubscriptionAssertions.assertUpdatesIn;
import static io.spine.delivery.admin.grpc.ShardStatus.NOT_PICKED;
import static io.spine.delivery.admin.grpc.ShardStatus.PICKED;
import static io.spine.delivery.given.TestInboxMessages.toDeliver;
import static io.spine.delivery.server.grpc.given.AdminServiceTestEnv.copyWithNewShard;
import static io.spine.delivery.server.grpc.given.AdminServiceTestEnv.pickUpShard;
import static io.spine.delivery.server.grpc.given.AdminServiceTestEnv.releaseShard;
import static io.spine.delivery.server.grpc.given.AdminServiceTestEnv.removeMessage;
import static io.spine.delivery.server.grpc.given.AdminServiceTestEnv.removeMessages;
import static io.spine.delivery.server.grpc.given.AdminServiceTestEnv.request;
import static io.spine.delivery.server.grpc.given.AdminServiceTestEnv.shardInfo;
import static io.spine.delivery.server.grpc.given.AdminServiceTestEnv.shardPickedWithoutTime;
import static io.spine.delivery.server.grpc.given.AdminServiceTestEnv.testMessage;
import static io.spine.delivery.server.grpc.given.AdminServiceTestEnv.writeMessage;
import static io.spine.delivery.server.grpc.given.AdminServiceTestEnv.writeMessages;
import static io.spine.server.delivery.DeliveryStrategy.newIndex;

@DisplayName("`AdminService` should")
final class AdminServiceTest extends WithApp implements WithLogging {

    /**
     * The observers of the {@linkplain #subscribeToUpdates() created} subscriptions,
     * remembered for the cancellation on the test completion.
     */
    private final List<WithAckObserver> subscriptions = new ArrayList<>();

    /**
     * Cancels the subscriptions created by the test.
     *
     * <p>Runs before the superclass shuts down the channel and the server, so that
     * the still-open streaming calls do not have to be force-killed by the teardown —
     * which otherwise may race the server shutdown and pollute the log with warnings.
     */
    @AfterEach
    void cancelSubscriptions() {
        subscriptions.forEach(WithAckObserver::cancel);
    }

    @Test
    @DisplayName("get current information about shards")
    void getShardInfo() {
        var shard1 = newIndex(1, 5);
        var shard2 = newIndex(2, 5);
        var shard3 = newIndex(3, 5);
        var shard4 = newIndex(4, 5);

        syncInboxService().writeOne(testMessage(shard1));
        syncInboxService().writeOne(testMessage(shard2));

        syncShardService().pickShard(pickUpShard(shard2));
        var outcome = syncShardService().pickShard(pickUpShard(shard3));
        syncShardService().pickShard(pickUpShard(shard4));
        syncShardService().releaseSession(releaseShard(outcome.getPickedUp()));

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
        var index = newIndex(1, 5);
        var observer = subscribeToUpdates();

        var future = observer.waitForAny();
        syncShardService().pickShard(pickUpShard(index));

        var expected = shardPickedWithoutTime(index);

        assertContains(future, expected);
        assertHasNoError(observer);
        assertUpdatesIn(observer).containsExactly(expected);
    }

    @Test
    @DisplayName("notify if shard is released")
    void notifyUnpicked() {

        var index = newIndex(1, 5);
        var observer = subscribeToUpdates();

        var pickedFuture =
                observer.waitForMatching(update -> update.getNewStatus() == PICKED);
        var notPickedFuture =
                observer.waitForMatching(update -> update.getNewStatus() == NOT_PICKED);

        var pickUpShard = pickUpShard(index);
        var outcome = syncShardService().pickShard(pickUpShard);
        syncShardService().releaseSession(releaseShard(outcome.getPickedUp()));

        var pickedUpdate = shardPickedWithoutTime(index);
        var unpickedUpdate = shardUnpicked(index);

        assertContains(pickedFuture, pickedUpdate);
        assertContains(notPickedFuture, unpickedUpdate);
        assertHasNoError(observer);
        assertUpdatesIn(observer).containsExactly(pickedUpdate, unpickedUpdate);
    }

    @Test
    @DisplayName("notify when message is written")
    void notifyMessageWritten() {
        var index = newIndex(1, 5);
        var observer = subscribeToUpdates();
        var messageWrittenFuture = observer.waitForAny();

        var message = copyWithNewShard(toDeliver(newUuid(), TypeUrl.of(Something.class)), index);

        syncInboxService().writeOne(writeMessage(message));

        var messageWritten = messagesCountChangedTo(index, 1);

        assertContains(messageWrittenFuture, messageWritten);
        assertHasNoError(observer);
        assertUpdatesIn(observer).containsExactly(messageWritten);
    }

    @Test
    @DisplayName("notify when message is removed")
    void notifyMessageRemoved() {
        var index = newIndex(1, 5);
        var observer = subscribeToUpdates();
        var messageWrittenFuture =
                observer.waitForMatching(update -> update.getNewMessagesCount() == 1);
        var messageRemovedFuture =
                observer.waitForMatching(update -> update.getNewMessagesCount() == 0);

        var message = copyWithNewShard(toDeliver(newUuid(), TypeUrl.of(Something.class)), index);

        syncInboxService().writeOne(writeMessage(message));
        syncInboxService().removeOne(removeMessage(message));

        var messageWritten = messagesCountChangedTo(index, 1);
        var messageRemoved = messagesCountChangedTo(index, 0);

        assertContains(messageWrittenFuture, messageWritten);
        assertContains(messageRemovedFuture, messageRemoved);
        assertHasNoError(observer);
        assertUpdatesIn(observer).containsExactly(messageWritten, messageRemoved);
    }

    @Test
    @DisplayName("notify when multiple messages are written")
    void notifyMessagesWritten() {
        var index = newIndex(1, 5);
        var observer = subscribeToUpdates();

        var message1 = copyWithNewShard(toDeliver(newUuid(), TypeUrl.of(Something.class)), index);
        var message2 = copyWithNewShard(toDeliver(newUuid(), TypeUrl.of(Something.class)), index);

        var message1WrittenFuture =
                observer.waitForMatching(update -> update.getNewMessagesCount() == 1);
        var message2WrittenFuture =
                observer.waitForMatching(update -> update.getNewMessagesCount() == 2);

        syncInboxService().writeMany(writeMessages(index, message1, message2));

        var message1Written = messagesCountChangedTo(index, 1);
        var message2Written = messagesCountChangedTo(index, 2);

        assertContains(message1WrittenFuture, message1Written);
        assertContains(message2WrittenFuture, message2Written);
        assertHasNoError(observer);
        assertUpdatesIn(observer).containsExactly(message1Written, message2Written);
    }

    @Test
    @DisplayName("notify when multiple messages are removed")
    void notifyMessagesRemoved() {
        var index = newIndex(1, 5);
        var observer = subscribeToUpdates();

        var message1 = copyWithNewShard(toDeliver(newUuid(), TypeUrl.of(Something.class)), index);
        var message2 = copyWithNewShard(toDeliver(newUuid(), TypeUrl.of(Something.class)), index);

        var message1WrittenFuture =
                observer.waitForMatching(update -> update.getNewMessagesCount() == 1);
        var message2WrittenFuture =
                observer.waitForMatching(update -> update.getNewMessagesCount() == 2);

        syncInboxService().writeMany(writeMessages(index, message1, message2));

        var message1Written = messagesCountChangedTo(index, 1);
        var message2Written = messagesCountChangedTo(index, 2);
        assertContains(message1WrittenFuture, message1Written);
        assertContains(message2WrittenFuture, message2Written);

        var message1RemovedFuture =
                observer.waitForMatching(update -> update.getNewMessagesCount() == 1);
        var message2RemovedFuture =
                observer.waitForMatching(update -> update.getNewMessagesCount() == 0);

        syncInboxService().removeMany(removeMessages(index, message1, message2));

        var message1Removed = messagesCountChangedTo(index, 1);
        var message2Removed = messagesCountChangedTo(index, 0);

        assertContains(message1RemovedFuture, message1Removed);
        assertContains(message2RemovedFuture, message2Removed);
        assertHasNoError(observer);
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
     * <p>Also waits for {@linkplain WithAckObserver#waitForAcknowledgment() an acknowledgment}
     * to ensure that the subscription is created on the server.
     */
    private BlockingMemoizingObserver<ShardInfoUpdate> subscribeToUpdates() {
        var observer = new BlockingMemoizingObserver<ShardInfoUpdate>();
        var ackObserver = new WithAckObserver(observer);
        adminService().subscribeToShardUpdates(Empty.getDefaultInstance(), ackObserver);
        ackObserver.waitForAcknowledgment();
        subscriptions.add(ackObserver);
        return observer;
    }
}
