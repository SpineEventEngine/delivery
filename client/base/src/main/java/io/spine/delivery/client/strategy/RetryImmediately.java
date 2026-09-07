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

package io.spine.delivery.client.strategy;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A strategy that will be immediately retrying requests in case of failures.
 */
public final class RetryImmediately extends AbstractExecutionStrategy {

    private final int retryCount;

    private int attempts = 0;

    /**
     * Creates a new {@code RetryImmediately} strategy with the given amount of retry attempts.
     */
    public static RetryImmediately times(int n) {
        checkArgument(n > 0, "A positive value expected. Encountered: %s.", n);
        return new RetryImmediately(n);
    }

    private RetryImmediately(int count) {
        super();
        retryCount = count;
    }

    @Override
    protected <R> ActionWithResult<R> handleException(FailedRequest<R> failure) {
        attempts++;
        if (attempts >= retryCount) {
            return failure.propagate();
        }
        return failure.retry();
    }

    @Override
    protected Action handleException(FailedVoidRequest failure) {
        attempts++;
        if (attempts >= retryCount) {
            return failure.propagate();
        }
        return failure.retry();
    }
}
