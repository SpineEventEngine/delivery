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
import io.spine.delivery.client.ExecutionFailedException;
import io.spine.delivery.client.RequestExecutionStrategy;
import io.spine.delivery.client.RequestWithResult;
import io.spine.delivery.client.VoidRequest;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A chain of execution strategies.
 *
 * <p>Executes a request starting with the first strategy in the chain. In case the execution fails,
 * the next strategy in the chain is picked.
 */
public final class StrategyChain implements RequestExecutionStrategy {

    private final ImmutableList<RequestExecutionStrategy> strategies;

    private StrategyChain(List<RequestExecutionStrategy> strategies) {
        this.strategies = ImmutableList.copyOf(strategies);
    }

    /**
     * Starts building the {@code StrategiesChain}.
     *
     * @param strategy
     *         the first strategy to be added to the chain.
     * @return {@code Builder} to continue build the {@code StrategiesChain}.
     */
    public static Builder with(RequestExecutionStrategy strategy) {
        checkNotNull(strategy);
        return new Builder(strategy);
    }

    /**
     * Executes the given {@code request} applying one strategy after another in case if
     * the previous strategy could not handle the occurred exception (the
     * {@code StrategyFailedException} was thrown by the strategy).
     *
     * @throws ExecutionFailedException
     *         if none of the strategies could handle the occurred exception
     */
    @Override
    public void execute(VoidRequest request) throws ExecutionFailedException {
        checkNotNull(request);
        List<RuntimeException> occurredExceptions = new ArrayList<>();
        for (var strategy : strategies) {
            try {
                strategy.execute(request);
                return;
            } catch (ExecutionFailedException e) {
                occurredExceptions.addAll(e.causes());
            }
        }
        throw new ExecutionFailedException(ImmutableList.copyOf(occurredExceptions));
    }

    /**
     * Executes the given {@code request} applying one strategy after another in case if
     * the previous strategy could not handle the occurred exception (the
     * {@code StrategyFailedException} was thrown by the strategy).
     *
     * @throws ExecutionFailedException
     *         if none of the strategies could handle the occurred exception
     */
    @Override
    public <R> R evaluate(RequestWithResult<R> request) throws ExecutionFailedException {
        checkNotNull(request);
        List<RuntimeException> occurredExceptions = new ArrayList<>();
        for (var strategy : strategies) {
            try {
                return strategy.evaluate(request);
            } catch (ExecutionFailedException e) {
                occurredExceptions.addAll(e.causes());
            }
        }
        throw new ExecutionFailedException(ImmutableList.copyOf(occurredExceptions));
    }

    /**
     * Builder of the {@code StrategiesChain}.
     */
    public static class Builder {

        private final List<RequestExecutionStrategy> strategies = new ArrayList<>();

        private Builder(RequestExecutionStrategy strategy) {
            strategies.add(strategy);
        }

        /**
         * Adds the given {@code strategy} to the sequence of strategies to be executed.
         */
        @SuppressWarnings("QuestionableName") // The API looks good with this name.
        public Builder then(RequestExecutionStrategy strategy) {
            checkNotNull(strategy);
            strategies.add(strategy);
            return this;
        }

        /**
         * Builds the {@code StrategiesChain}.
         */
        public StrategyChain build() {
            return new StrategyChain(strategies);
        }
    }
}
