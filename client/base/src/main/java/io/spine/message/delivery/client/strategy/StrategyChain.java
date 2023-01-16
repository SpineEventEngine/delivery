/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client.strategy;

import com.google.common.collect.ImmutableList;
import io.spine.message.delivery.client.ExecutionFailedException;
import io.spine.message.delivery.client.RequestExecutionStrategy;
import io.spine.message.delivery.client.RequestWithResult;
import io.spine.message.delivery.client.VoidRequest;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A chain of execution strategies.
 *
 * <p>Executes a request starting with the first strategy in chain. In case the execution fails,
 * the next strategy in chain is picked.
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
     * Executes the given {@code request} applying one strategy after another in case if previous
     * strategy could not handle the occurred exception (the {@code StrategyFailedException} was
     * thrown by the strategy).
     *
     * @throws ExecutionFailedException
     *         if none of the strategies could handle the occurred exception
     */
    @Override
    public void execute(VoidRequest request) throws ExecutionFailedException {
        checkNotNull(request);
        List<RuntimeException> occurredExceptions = new ArrayList<>();
        for (RequestExecutionStrategy strategy : strategies) {
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
     * Executes the given {@code request} applying one strategy after another in case if previous
     * strategy could not handle the occurred exception (the {@code StrategyFailedException} was
     * thrown by the strategy).
     *
     * @throws ExecutionFailedException
     *         if none of the strategies could handle the occurred exception
     */
    @Override
    public <R> R evaluate(RequestWithResult<R> request) throws ExecutionFailedException {
        checkNotNull(request);
        List<RuntimeException> occurredExceptions = new ArrayList<>();
        for (RequestExecutionStrategy strategy : strategies) {
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
