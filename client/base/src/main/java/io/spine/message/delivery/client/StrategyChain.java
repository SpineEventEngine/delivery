/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Encapsulates a list of other strategies and will apply each encapsulated strategy
 * to the operation if previous strategy failed.
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
     * Executes the given {@code operation} applying one strategy after another in case if previous
     * strategy could not handle the occurred exception (the {@code StrategyFailedException} was
     * thrown by the strategy).
     *
     * @throws ExecutionFailedException
     *         if none of the strategies could handle the occurred exception.
     */
    @Override
    public void execute(VoidRequest operation) throws ExecutionFailedException {
        checkNotNull(operation);
        List<Exception> occurredExceptions = new ArrayList<>();
        for (RequestExecutionStrategy strategy : strategies) {
            try {
                strategy.execute(operation);
                return;
            } catch (ExecutionFailedException e) {
                occurredExceptions.addAll(e.causes());
            }
        }
        throw new ExecutionFailedException(ImmutableList.copyOf(occurredExceptions));
    }

    /**
     * Executes the given {@code operation} applying one strategy after another in case if previous
     * strategy could not handle the occurred exception (the {@code StrategyFailedException} was
     * thrown by the strategy).
     *
     * @throws ExecutionFailedException
     *         if none of the strategies could handle the occurred exception.
     */
    @Override
    public <R> R evaluate(RequestWithResult<R> operation) throws ExecutionFailedException {
        checkNotNull(operation);
        List<Exception> occurredExceptions = new ArrayList<>();
        for (RequestExecutionStrategy strategy : strategies) {
            try {
                return strategy.evaluate(operation);
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
