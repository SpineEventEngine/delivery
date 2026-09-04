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

package io.spine.server.storage.query;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Message;
import io.spine.query.QueryPredicate;
import io.spine.query.RecordQuery;
import io.spine.query.Subject;
import io.spine.query.SubjectParameter;
import io.spine.server.storage.RecordWithColumns;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkNotNull;

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
        var result = idMatches(input) && columnValuesMatch(input);
        return result;
    }

    private boolean idMatches(RecordWithColumns<I, R> record) {
        if (acceptedIds.isEmpty()) {
            return true;
        }
        var actualId = record.id();
        return acceptedIds.contains(actualId);
    }

    private boolean columnValuesMatch(RecordWithColumns<I, R> record) {
        return checkPredicate(record, predicate);
    }

    private static <I, R extends Message> boolean
    checkPredicate(RecordWithColumns<I, R> record, QueryPredicate<R> predicate) {
        boolean match;

        var operator = predicate.operator();
        var parameters = predicate.parameters();
        var children = predicate.children();
        match = switch (operator) {
            case AND -> checkAnd(record, parameters, children);
            case OR -> checkEither(record, parameters, children);
        };
        return match;
    }

    private static <I, R extends Message> boolean
    checkAnd(RecordWithColumns<I, R> record, ImmutableList<SubjectParameter<R, ?, ?>> params,
             ImmutableList<QueryPredicate<R>> predicates) {
        if (params.isEmpty() && predicates.isEmpty()) {
            return true;
        }
        var paramsMatch =
                params.stream()
                      .allMatch(param -> matches(record, param));
        if (paramsMatch) {
            var predicatesMatch =
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
        var paramsMatch =
                params.stream()
                      .anyMatch(param -> matches(record, param));
        if (!paramsMatch) {
            var predicatesMatch =
                    predicates.stream()
                              .anyMatch(predicate -> checkPredicate(record, predicate));
            return predicatesMatch;
        }
        return true;
    }

    private static <I, R extends Message> boolean
    matches(RecordWithColumns<I, R> recWithColumns, SubjectParameter<R, ?, ?> param) {
        var column = param.column();
        if (!recWithColumns.hasColumn(column.name())) {
            return false;
        }
        var columnValue = recWithColumns.columnValue(param.column()
                                                          .name());
        var result = checkSingleParameter(param, columnValue);
        return result;
    }

    private static <R extends Message> boolean
    checkSingleParameter(SubjectParameter<R, ?, ?> parameter, @Nullable Object actualValue) {
        if (actualValue == null) {
            return false;
        }
        var paramValue = parameter.value();
        var result = parameter.operator()
                              .eval(actualValue, paramValue);
        return result;
    }
}
