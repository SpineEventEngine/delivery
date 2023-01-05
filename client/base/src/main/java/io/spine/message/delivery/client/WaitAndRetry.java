/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.common.collect.ImmutableList;
import io.grpc.StatusRuntimeException;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static java.util.concurrent.TimeUnit.SECONDS;

public class WaitAndRetry implements ErrorHandlingStrategy {

    private final int waitSeconds;
    private final int retryCount;

    public static Builder waitForSeconds(int seconds) {
        checkArgument(seconds > 0, "Waiting seconds should be positive.");
        return new Builder(seconds);
    }

    private WaitAndRetry(int waitSeconds, int retryCount) {
        this.waitSeconds = waitSeconds;
        this.retryCount = retryCount;
    }

    @Override
    public void runWithStrategy(VoidOperation operation) {
        int attempts = 0;
        boolean success = false;
        List<StatusRuntimeException> caughtExceptions = new ArrayList<>(retryCount);
        while (attempts < retryCount && !success) {
            try {
                operation.run();
                success = true;
            } catch (StatusRuntimeException e) {
                caughtExceptions.add(e);
                attempts++;
                if (attempts < retryCount) {
                    sleepUninterruptibly(waitSeconds, SECONDS);
                }
            }
        }
        if (!success) {
            throw new StrategyFailedException(ImmutableList.copyOf(caughtExceptions));
        }
    }

    @Override
    public <R> R runWithStrategy(OperationWithResult<R> operation) {
        int attempts = 0;
        List<StatusRuntimeException> caughtExceptions = new ArrayList<>(retryCount);
        while (attempts < retryCount) {
            try {
                return operation.run();
            } catch (StatusRuntimeException e) {
                caughtExceptions.add(e);
                attempts++;
                if (attempts < retryCount) {
                    sleepUninterruptibly(waitSeconds, SECONDS);
                }
            }
        }
        throw new StrategyFailedException(ImmutableList.copyOf(caughtExceptions));
    }

    public static class Builder {

        private final int waitSeconds;

        private Builder(int waitSeconds) {
            this.waitSeconds = waitSeconds;
        }

        public WaitAndRetry times(int n) {
            checkArgument(n > 0, "Retry counts should be positive.");
            return new WaitAndRetry(waitSeconds, n);
        }
    }
}
