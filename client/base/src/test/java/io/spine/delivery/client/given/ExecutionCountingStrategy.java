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

package io.spine.delivery.client.given;

import com.google.common.collect.ImmutableList;
import io.spine.delivery.client.ExecutionFailedException;
import io.spine.delivery.client.RequestExecutionStrategy;
import io.spine.delivery.client.RequestWithResult;
import io.spine.delivery.client.VoidRequest;

/**
 * A strategy for testing that counts the amount of operations executed.
 */
public final class ExecutionCountingStrategy implements RequestExecutionStrategy {

    private int voidRequestExecutions = 0;

    private int requestWithResultExecutions = 0;

    /**
     * Increments the execution counter for {@code VoidRequest} and executes
     * the given {@code operation}.
     *
     * <p>Even if the operation throws an exception, it will be counted as an execution.
     *
     * @throws ExecutionFailedException if the operation throws an exception
     */
    @Override
    public void execute(VoidRequest operation) throws ExecutionFailedException {
        voidRequestExecutions++;
        try {
            operation.run();
        } catch (RuntimeException e) {
            throw new ExecutionFailedException(ImmutableList.of(e));
        }
    }

    /**
     * Increments the execution counter for {@code RequestWithResult} and executes
     * the given {@code operation}.
     *
     * <p>Even if the operation throws an exception, it will be counted as an execution.
     *
     * @throws ExecutionFailedException if the operation throws an exception
     */
    @Override
    public <R> R evaluate(RequestWithResult<R> operation) throws ExecutionFailedException {
        requestWithResultExecutions++;
        try {
            return operation.evaluate();
        } catch (RuntimeException e) {
            throw new ExecutionFailedException(ImmutableList.of(e));
        }
    }

    /**
     * Returns a number of {@code VoidRequest}s executed with this strategy.
     */
    public int voidExecutions() {
        return voidRequestExecutions;
    }

    /**
     * Returns a number of {@code RequestWithResult} executed with this strategy.
     */
    public int withResultEvaluations() {
        return requestWithResultExecutions;
    }
}
