/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage.query;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Message;
import io.spine.query.Column;
import io.spine.query.LogicalOperator;
import io.spine.query.QueryPredicate;
import io.spine.query.RecordQuery;
import io.spine.query.Subject;
import io.spine.query.SubjectParameter;
import io.spine.server.storage.RecordWithColumns;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Exceptions.newIllegalArgumentException;

/**
 * Matches the records to the {@linkplain RecordQuery#subject() subject} of a {@link RecordQuery}.
 *
 * <p>This class is a copy of {@linkplain io.spine.server.storage.memory.RecordQueryMatcher} that
 * is made to be able to instantiate the class outside of the package, as this type is very useful
 * for custom storage implementations.
 *
 * @param <I>
 *         the type of the identifiers of the records
 * @param <R>
 *         the type of the messages stored as records
 */
public final class RecordQueryMatcher<I, R extends Message>
        implements Predicate<@Nullable RecordWithColumns<I, R>> {

    private final ImmutableSet<I> acceptedIds;
    private final QueryPredicate<R> predicate;

    /**
     * Creates a new matcher for the given subject.
     */
    public RecordQueryMatcher(Subject<I, R> subject) {
        checkNotNull(subject);
        // Pack IDs from the query for faster search using packed IDs from loaded records.
        this.acceptedIds = subject.id()
                                  .values();
        this.predicate = subject.predicate();
    }

    /**
     * Creates a new matcher with the {@code Subject} of the given {@code query}.
     */
    public RecordQueryMatcher(RecordQuery<I, R> query) {
        this(checkNotNull(query).subject());
    }

    @Override
    public boolean test(@Nullable RecordWithColumns<I, R> input) {
        if (input == null) {
            return false;
        }
        boolean result = idMatches(input) && columnValuesMatch(input);
        return result;
    }

    private boolean idMatches(RecordWithColumns<I, R> record) {
        if (acceptedIds.isEmpty()) {
            return true;
        }
        I actualId = record.id();
        return acceptedIds.contains(actualId);
    }

    private boolean columnValuesMatch(RecordWithColumns<I, R> record) {
        return checkPredicate(record, predicate);
    }

    private static <I, R extends Message> boolean
    checkPredicate(RecordWithColumns<I, R> record, QueryPredicate<R> predicate) {
        boolean match;

        LogicalOperator operator = predicate.operator();
        ImmutableList<SubjectParameter<R, ?, ?>> parameters = predicate.parameters();
        ImmutableList<QueryPredicate<R>> children = predicate.children();
        switch (operator) {
            case AND:
                match = checkAnd(record, parameters, children);
                break;
            case OR:
                match = checkEither(record, parameters, children);
                break;
            default:
                throw newIllegalArgumentException("Logical operator `%s` is invalid.",
                                                  operator);
        }
        return match;
    }

    private static <I, R extends Message> boolean
    checkAnd(RecordWithColumns<I, R> record, ImmutableList<SubjectParameter<R, ?, ?>> params,
             ImmutableList<QueryPredicate<R>> predicates) {
        if (params.isEmpty() && predicates.isEmpty()) {
            return true;
        }
        boolean paramsMatch =
                params.stream()
                      .allMatch(param -> matches(record, param));
        if (paramsMatch) {
            boolean predicatesMatch =
                    predicates.stream()
                              .allMatch(predicate -> checkPredicate(record,
                                                                    predicate));
            return predicatesMatch;
        }
        return false;
    }

    private static <I, R extends Message> boolean
    checkEither(RecordWithColumns<I, R> record,
                ImmutableList<SubjectParameter<R, ?, ?>> params,
                ImmutableList<QueryPredicate<R>> predicates) {
        if (params.isEmpty() && predicates.isEmpty()) {
            return true;
        }
        boolean paramsMatch =
                params.stream()
                      .anyMatch(param -> matches(record, param));
        if (!paramsMatch) {
            boolean predicatesMatch =
                    predicates.stream()
                              .anyMatch(predicate -> checkPredicate(record, predicate));
            return predicatesMatch;
        }
        return true;
    }

    private static <I, R extends Message> boolean
    matches(RecordWithColumns<I, R> recWithColumns, SubjectParameter<R, ?, ?> param) {
        Column<R, ?> column = param.column();
        if (!recWithColumns.hasColumn(column.name())) {
            return false;
        }
        @Nullable Object columnValue = recWithColumns.columnValue(param.column()
                                                                       .name());
        boolean result = checkSingleParameter(param, columnValue);
        return result;
    }

    private static <R extends Message> boolean
    checkSingleParameter(SubjectParameter<R, ?, ?> parameter, @Nullable Object actualValue) {
        if (actualValue == null) {
            return false;
        }
        Object paramValue = parameter.value();
        boolean result = parameter.operator()
                                  .eval(actualValue, paramValue);
        return result;
    }
}
