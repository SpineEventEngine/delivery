/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.delivery.server.grpc;

import com.google.common.truth.extensions.proto.IterableOfProtosFluentAssertion;
import com.google.protobuf.Empty;
import io.spine.delivery.admin.given.BlockingMemoizingObserver;
import io.spine.delivery.admin.given.WithAckObserver;
import io.spine.delivery.admin.grpc.ShardInfoUpdate;
import io.spine.delivery.admin.grpc.ShardStatus;
import io.spine.delivery.server.WithApp;
import io.spine.logging.WithLogging;
import io.spine.server.delivery.ShardIndex;
import io.spine.test.delivery.Something;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.delivery.admin.given.SubscriptionAssertions.assertHasNoError;
import static io.spine.delivery.admin.grpc.ShardInfoUpdate.WHEN_LAST_PICKED_FIELD_NUMBER;
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
import static io.spine.delivery.server.grpc.given.AdminServiceTestEnv.testMessage;
import static io.spine.delivery.server.grpc.given.AdminServiceTestEnv.writeMessage;
import static io.spine.delivery.server.grpc.given.AdminServiceTestEnv.writeMessages;
import static io.spine.server.delivery.DeliveryStrategy.newIndex;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Tests {@code AdminService} through the gRPC API of a running app.
 *
 * <p>The app throttles the updates of each shard, so the changes of a shard made within
 * the throttling interval arrive as one update with their final state. To assert the exact
 * sequence of the states that a subscriber receives, a test waits for each expected state
 * before it causes the next change.
 */
@DisplayName("`AdminService` should")
final class AdminServiceTest extends WithApp implements WithLogging {

    /**
     * How long a test waits for an expected update.
     */
    private static final int WAIT_SECONDS = 5;

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
    @DisplayName("notify when a shard is picked")
    void notifyPicked() {
        var index = newIndex(1, 5);
        var observer = subscribeToUpdates();
        var picked = observer.waitForMatching(update -> update.getNewStatus() == PICKED);

        syncShardService().pickShard(pickUpShard(index));

        var update = awaitState(picked, state(index, PICKED, 0));
        assertThat(update.hasWhenLastPicked()).isTrue();
        assertHasNoError(observer);
        assertUpdatesIn(observer).containsExactly(state(index, PICKED, 0));
    }

    @Test
    @DisplayName("notify when a shard is released")
    void notifyUnpicked() {
        var index = newIndex(1, 5);
        var observer = subscribeToUpdates();

        var picked = observer.waitForMatching(update -> update.getNewStatus() == PICKED);
        var outcome = syncShardService().pickShard(pickUpShard(index));
        var pickedUpdate = awaitState(picked, state(index, PICKED, 0));

        var released = observer.waitForMatching(update -> update.getNewStatus() == NOT_PICKED);
        syncShardService().releaseSession(releaseShard(outcome.getPickedUp()));
        var releasedUpdate = awaitState(released, state(index, NOT_PICKED, 0));

        assertThat(releasedUpdate.getWhenLastPicked())
                .isEqualTo(pickedUpdate.getWhenLastPicked());
        assertHasNoError(observer);
        assertUpdatesIn(observer).containsExactly(state(index, PICKED, 0),
                                                  state(index, NOT_PICKED, 0));
    }

    @Test
    @DisplayName("notify when a message is written")
    void notifyMessageWritten() {
        var index = newIndex(1, 5);
        var observer = subscribeToUpdates();
        var written = observer.waitForAny();

        syncInboxService().writeOne(writeMessage(newMessage(index)));

        awaitState(written, state(index, NOT_PICKED, 1));
        assertHasNoError(observer);
        assertUpdatesIn(observer).containsExactly(state(index, NOT_PICKED, 1));
    }

    @Test
    @DisplayName("notify when a message is removed, including a count of zero")
    void notifyMessageRemoved() {
        var index = newIndex(1, 5);
        var observer = subscribeToUpdates();
        var message = newMessage(index);

        var written = observer.waitForMatching(update -> update.getNewMessagesCount() == 1);
        syncInboxService().writeOne(writeMessage(message));
        awaitState(written, state(index, NOT_PICKED, 1));

        var removed = observer.waitForMatching(update -> update.getNewMessagesCount() == 0);
        syncInboxService().removeOne(removeMessage(message));
        awaitState(removed, state(index, NOT_PICKED, 0));

        assertHasNoError(observer);
        assertUpdatesIn(observer).containsExactly(state(index, NOT_PICKED, 1),
                                                  state(index, NOT_PICKED, 0));
    }

    @Test
    @DisplayName("notify once when several messages of a shard are written at once")
    void notifyMessagesWritten() {
        var index = newIndex(1, 5);
        var observer = subscribeToUpdates();
        var written = observer.waitForAny();

        syncInboxService().writeMany(writeMessages(index, newMessage(index), newMessage(index)));

        awaitState(written, state(index, NOT_PICKED, 2));
        assertHasNoError(observer);
        assertUpdatesIn(observer).containsExactly(state(index, NOT_PICKED, 2));
    }

    @Test
    @DisplayName("notify once when several messages of a shard are removed at once")
    void notifyMessagesRemoved() {
        var index = newIndex(1, 5);
        var observer = subscribeToUpdates();
        var message1 = newMessage(index);
        var message2 = newMessage(index);

        var written = observer.waitForMatching(update -> update.getNewMessagesCount() == 2);
        syncInboxService().writeMany(writeMessages(index, message1, message2));
        awaitState(written, state(index, NOT_PICKED, 2));

        var removed = observer.waitForMatching(update -> update.getNewMessagesCount() == 0);
        syncInboxService().removeMany(removeMessages(index, message1, message2));
        awaitState(removed, state(index, NOT_PICKED, 0));

        assertHasNoError(observer);
        assertUpdatesIn(observer).containsExactly(state(index, NOT_PICKED, 2),
                                                  state(index, NOT_PICKED, 0));
    }

    @Test
    @DisplayName("send a new subscriber the current state of every shard," +
            " including a change made after `GetShardInfo`")
    void sendCurrentState() {
        var withMessage = newIndex(1, 5);
        var picked = newIndex(2, 5);
        var changedLater = newIndex(3, 5);
        syncInboxService().writeOne(writeMessage(newMessage(withMessage)));
        syncShardService().pickShard(pickUpShard(picked));
        syncAdminService().getShardInfo(request());
        syncInboxService().writeOne(writeMessage(newMessage(changedLater)));

        var observer = new BlockingMemoizingObserver<ShardInfoUpdate>();
        var states = List.of(
                observer.waitForMatching(update -> update.getIndex().equals(withMessage)),
                observer.waitForMatching(update -> update.getIndex().equals(picked)),
                observer.waitForMatching(update -> update.getIndex().equals(changedLater))
        );
        subscribe(observer);

        awaitState(states.get(0), state(withMessage, NOT_PICKED, 1));
        awaitState(states.get(1), state(picked, PICKED, 0));
        awaitState(states.get(2), state(changedLater, NOT_PICKED, 1));
        assertHasNoError(observer);
        assertUpdatesIn(observer).containsExactly(state(withMessage, NOT_PICKED, 1),
                                                  state(picked, PICKED, 0),
                                                  state(changedLater, NOT_PICKED, 1));
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
        subscribe(observer);
        return observer;
    }

    private void subscribe(BlockingMemoizingObserver<ShardInfoUpdate> observer) {
        var ackObserver = new WithAckObserver(observer);
        adminService().subscribeToShardUpdates(Empty.getDefaultInstance(), ackObserver);
        ackObserver.waitForAcknowledgment();
        subscriptions.add(ackObserver);
    }

    private static io.spine.server.delivery.InboxMessage newMessage(ShardIndex index) {
        return copyWithNewShard(toDeliver(newUuid(), TypeUrl.of(Something.class)), index);
    }

    /**
     * Creates the full state of a shard, without the time of its last pick, which
     * the assertions ignore.
     */
    private static ShardInfoUpdate state(ShardIndex index, ShardStatus status, int count) {
        return ShardInfoUpdate.newBuilder()
                .setIndex(index)
                .setNewStatus(status)
                .setNewMessagesCount(count)
                .build();
    }

    /**
     * Waits for the update, and checks that it carries the expected state.
     *
     * <p>Compares all the fields, including a count of zero, except the time of the last
     * pick.
     */
    private static ShardInfoUpdate awaitState(Future<ShardInfoUpdate> future,
                                              ShardInfoUpdate expected) {
        ShardInfoUpdate update;
        try {
            update = future.get(WAIT_SECONDS, SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread()
                  .interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException(
                    "The expected update has not arrived: " + expected, e);
        }
        assertThat(update)
                .ignoringFields(WHEN_LAST_PICKED_FIELD_NUMBER)
                .isEqualTo(expected);
        return update;
    }

    private static IterableOfProtosFluentAssertion<ShardInfoUpdate>
    assertUpdatesIn(BlockingMemoizingObserver<ShardInfoUpdate> observer) {
        return assertThat(observer.responses())
                .ignoringFields(WHEN_LAST_PICKED_FIELD_NUMBER);
    }
}
