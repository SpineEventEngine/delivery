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

import com.google.common.collect.ImmutableList;

/**
 * Summary of the {@code VoidRequest} failure.
 */
public final class FailedVoidRequest extends AbstractFailedRequest<Action> {

    private final Runnable retry;

    /**
     * Creates a new {@code FailedVoidRequest} with the given {@code retry} function and
     * previously occurred exceptions.
     *
     * @param retry
     *         function that will be retrying the original request.
     * @param allExceptions
     *         previously occurred exceptions.
     */
    FailedVoidRequest(Runnable retry, ImmutableList<RuntimeException> allExceptions) {
        super(allExceptions);
        this.retry = retry;
    }

    /**
     * Returns a predefined {@code Action} that retries failed {@code VoidRequest}.
     */
    @Override
    public Action retry() {
        return retry::run;
    }
}
