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

public final class RetryImmediately implements ErrorHandlingStrategy {

    private final int retryCount;

    public static RetryImmediately times(int n) {
        checkArgument(n > 0, "Retries count should be positive.");
        return new RetryImmediately(n);
    }

    private RetryImmediately(int count) {
        retryCount = count;
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
            }
        }
        throw new StrategyFailedException(ImmutableList.copyOf(caughtExceptions));
    }
}
