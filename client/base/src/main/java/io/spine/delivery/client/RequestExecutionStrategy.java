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

package io.spine.delivery.client;

/**
 * A strategy that executes requests and decides how to handle errors if any occurred.
 */
public interface RequestExecutionStrategy {

    /**
     * Executes the given {@code VoidRequest} and handles occurred exceptions in the way
     * implemented in a particular strategy.
     *
     * @throws ExecutionFailedException
     *         if the strategy is unable to handle the occurred exception
     */
    void execute(VoidRequest operation) throws ExecutionFailedException;

    /**
     * Executes the given {@code RequestWithResult} and handles occurred exceptions in the way
     * implemented in a particular strategy.
     *
     * @throws ExecutionFailedException
     *         if the strategy is unable to handle the occurred exception
     */
    <R> R evaluate(RequestWithResult<R> operation) throws ExecutionFailedException;
}
