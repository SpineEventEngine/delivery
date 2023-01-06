/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client.failures;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Encapsulates a list of other strategies and will apply each encapsulated strategy
 * to the operation if previous strategy failed.
 */
public final class StrategiesChain implements ErrorHandlingStrategy {

    private final ImmutableList<ErrorHandlingStrategy> strategies;

    private StrategiesChain(List<ErrorHandlingStrategy> strategies) {
        this.strategies = ImmutableList.copyOf(strategies);
    }

    /**
     * Starts building the {@code StrategiesChain}.
     *
     * @param strategy
     *         the first strategy to be added to the chain.
     * @return {@code Builder} to continue build the {@code StrategiesChain}.
     */
    public static Builder with(ErrorHandlingStrategy strategy) {
        checkNotNull(strategy);
        return new Builder(strategy);
    }

    /**
     * Executes the given {@code operation} applying one strategy after another in case if previous
     * strategy could not handle the occurred exception (the {@code StrategyFailedException} was
     * thrown by the strategy).
     *
     * @throws StrategyFailedException
     *         if none of the strategies could handle the occurred exception.
     */
    @Override
    public void runWithStrategy(VoidOperation operation) throws StrategyFailedException {
        List<Exception> occurredExceptions = new ArrayList<>();
        for (ErrorHandlingStrategy strategy : strategies) {
            try {
                strategy.runWithStrategy(operation);
                return;
            } catch (StrategyFailedException e) {
                occurredExceptions.addAll(e.occurredExceptions());
            }
        }
        throw new StrategyFailedException(ImmutableList.copyOf(occurredExceptions));
    }

    /**
     * Executes the given {@code operation} applying one strategy after another in case if previous
     * strategy could not handle the occurred exception (the {@code StrategyFailedException} was
     * thrown by the strategy).
     *
     * @throws StrategyFailedException
     *         if none of the strategies could handle the occurred exception.
     */
    @Override
    public <R> R runWithStrategy(OperationWithResult<R> operation) throws StrategyFailedException {
        List<Exception> occurredExceptions = new ArrayList<>();
        for (ErrorHandlingStrategy strategy : strategies) {
            try {
                return strategy.runWithStrategy(operation);
            } catch (StrategyFailedException e) {
                occurredExceptions.addAll(e.occurredExceptions());
            }
        }
        throw new StrategyFailedException(ImmutableList.copyOf(occurredExceptions));
    }

    /**
     * Builder of the {@code StrategiesChain}.
     */
    public static class Builder {

        private final List<ErrorHandlingStrategy> strategies = new ArrayList<>();

        private Builder(ErrorHandlingStrategy strategy) {
            strategies.add(strategy);
        }

        /**
         * Adds the given {@code strategy} to the sequence of strategies to be executed.
         */
        @SuppressWarnings("QuestionableName") // The API looks good with this name.
        public Builder then(ErrorHandlingStrategy strategy) {
            checkNotNull(strategy);
            strategies.add(strategy);
            return this;
        }

        /**
         * Builds the {@code StrategiesChain}.
         */
        public StrategiesChain build() {
            return new StrategiesChain(strategies);
        }
    }
}
