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
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * An error-handling strategy that will be waiting a certain amount of time before
 * retrying a failed request.
 */
public final class WaitAndRetry extends AbstractExecutionStrategy {

    private final int waitSeconds;
    private final int retryCount;

    private int attempts = 0;

    /**
     * Starts a creation chain for the {@code WaitAndRetry} strategy.
     *
     * @param seconds
     *         the amount of seconds to wait before trying to execute a failed request again.
     */
    public static Builder forSeconds(int seconds) {
        checkArgument(seconds > 0, "Number of seconds must be positive. Encountered: %s.", seconds);
        return new Builder(seconds);
    }

    private WaitAndRetry(int waitSeconds, int retryCount) {
        super();
        this.waitSeconds = waitSeconds;
        this.retryCount = retryCount;
    }

    @Override
    protected <R> ActionWithResult<R> handleException(FailedRequest<R> failure) {
        attempts++;
        if (attempts >= retryCount) {
            return failure.propagate();
        }
        sleepUninterruptibly(waitSeconds, SECONDS);
        return failure.retry();
    }

    @Override
    protected Action handleException(FailedVoidRequest failure) {
        attempts++;
        if (attempts >= retryCount) {
            return failure.propagate();
        }
        sleepUninterruptibly(waitSeconds, SECONDS);
        return failure.retry();
    }

    /**
     * Builder for the {@code WaitAndRetry} strategy.
     */
    public static class Builder {

        private final int waitSeconds;

        private Builder(int waitSeconds) {
            this.waitSeconds = waitSeconds;
        }

        /**
         * Creates a new {@code WaitAndRetry} strategy with the given amount of retries.
         */
        public WaitAndRetry times(int n) {
            checkArgument(n > 0, "A positive retry amount expected. Encountered: %s.", n);
            return new WaitAndRetry(waitSeconds, n);
        }
    }
}
