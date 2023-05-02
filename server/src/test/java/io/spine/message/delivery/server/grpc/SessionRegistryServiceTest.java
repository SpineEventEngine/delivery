/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

import com.google.protobuf.util.Durations;
import io.grpc.StatusRuntimeException;
import io.spine.message.delivery.admin.given.FutureMemoizingObserver;
import io.spine.message.delivery.admin.grpc.ShardInfoUpdate;
import io.spine.message.delivery.admin.grpc.ShardStatus;
import io.spine.message.delivery.command.PickUpShard;
import io.spine.message.delivery.command.ReleaseExpiredSessions;
import io.spine.message.delivery.event.ExpiredSession;
import io.spine.message.delivery.event.ExpiredSessionsReleased;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.server.WithApp;
import io.spine.server.NodeId;
import io.spine.server.delivery.DeliveryStrategy;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.WorkerId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static io.spine.message.delivery.admin.grpc.ShardStatus.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`SessionRegistryService` should")
final class SessionRegistryServiceTest extends WithApp {

    private final ShardIndex shard = DeliveryStrategy.newIndex(1, 2);
    private final NodeId node = NodeId.newBuilder()
            .setValue(SessionRegistryServiceTest.class.getName())
            .vBuild();
    private final WorkerId worker = WorkerId.newBuilder()
            .setNodeId(node)
            .setValue(SessionRegistryServiceTest.class.getName())
            .vBuild();

    @Test
    @DisplayName("pick up a shard")
    void pickUpShard() {
        var request = PickUpShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .vBuild();
        var expected = ShardPickedUp.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .buildPartial();
        var response = sessionRegistry().pickShard(request);
        assertThat(response)
                .comparingExpectedFieldsOnly()
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("do not pick up a shard for delivery if one is already picked up")
    void notPickUpShard() {
        var request = PickUpShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .vBuild();
        var firstAttempt = sessionRegistry().pickShard(request);
        assertThat(firstAttempt)
                .isNotEqualToDefaultInstance();
        assertThrows(StatusRuntimeException.class, () -> sessionRegistry().pickShard(request));
    }

    @Test
    @DisplayName("release expired sessions")
    @SuppressWarnings("ResultOfMethodCallIgnored")
    void releaseExpiredSessions() {
        var observer = subscribeToUpdates();
        var pickShard = PickUpShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .vBuild();
        sessionRegistry().pickShard(pickShard);
        waitForPickUp(observer, shard);
        sleepUninterruptibly(2, TimeUnit.SECONDS); // Wait for the session to expire.
        var releaseExpired = ReleaseExpiredSessions.newBuilder()
                .setInactivityPeriod(Durations.fromSeconds(1))
                .vBuild();
        ExpiredSessionsReleased result = sessionRegistry().releaseSessions(releaseExpired);
        assertThat(result.getShardCount())
                .isEqualTo(1);
        ExpiredSession expiredSession = result.getShard(0);
        assertThat(expiredSession.getShard())
                .isEqualTo(shard);
        assertThat(expiredSession.getWorker())
                .isEqualTo(worker);
    }

    @Test
    @DisplayName("filter out sessions that were already released")
    @SuppressWarnings("ResultOfMethodCallIgnored")
    void filterOutAlreadyReleasedSessions() {
        var observer = subscribeToUpdates();
        var pickShard = PickUpShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .vBuild();
        sessionRegistry().pickShard(pickShard);
        waitForPickUp(observer, shard);
        sleepUninterruptibly(2, TimeUnit.SECONDS); // Wait for the session to expire.
        var releaseExpired = ReleaseExpiredSessions.newBuilder()
                .setInactivityPeriod(Durations.fromSeconds(1))
                .vBuild();
        ExpiredSessionsReleased released = sessionRegistry().releaseSessions(releaseExpired);
        assertThat(released.getShardCount())
                .isEqualTo(1);
        ExpiredSession expiredSession = released.getShard(0);
        assertThat(expiredSession.getShard())
                .isEqualTo(shard);
        assertThat(expiredSession.getWorker())
                .isEqualTo(worker);
        ExpiredSessionsReleased result = sessionRegistry().releaseSessions(releaseExpired);
        assertThat(result.getShardCount())
                .isEqualTo(0);
    }

    @Test
    @DisplayName("release no expired sessions if does not match the criteria")
    @SuppressWarnings("ResultOfMethodCallIgnored")
    void releaseNoExpiresSessions() {
        var pickShard = PickUpShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .vBuild();
        sessionRegistry().pickShard(pickShard);
        var releaseExpired = ReleaseExpiredSessions.newBuilder()
                .setInactivityPeriod(Durations.fromSeconds(30))
                .vBuild();
        ExpiredSessionsReleased result = sessionRegistry().releaseSessions(releaseExpired);
        assertThat(result.getShardCount())
                .isEqualTo(0);
    }

    /**
     * Waits for an update of the given {@code shard} that acknowledges the shard pick up.
     *
     * <p>Throws {@code TimeoutException} if the update is not received within {@code 2} seconds.
     */
    private static void
    waitForPickUp(FutureMemoizingObserver<ShardInfoUpdate> observer, ShardIndex shard) {
        try {
            observer.nextOnNextMatching(is(shard).and(hasStatus(PICKED)))
                    .get(2, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a new {@code Predicate} for the {@code ShardInfoUpdate} that tests if the update
     * has the given {@code index}.
     */
    private static Predicate<ShardInfoUpdate> is(ShardIndex index) {
        return u -> index.equals(u.getIndex());
    }

    /**
     * Creates a new {@code Predicate} for the {@code ShardInfoUpdate} that tests if the update
     * notifies about a {@linkplain  ShardStatus#PICKED PICKED} shard status.
     */
    private static Predicate<ShardInfoUpdate> hasStatus(ShardStatus status) {
        return u -> u.getNewStatus() == status;
    }
}
