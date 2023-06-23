/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage;

import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import io.spine.query.ColumnName;
import io.spine.query.Direction;
import io.spine.query.RecordColumn;
import io.spine.query.SortBy;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * A comparator for sorting records in a provided {@linkplain SortBy sorting order}.
 *
 * <p>This is a copy of the {@linkplain io.spine.server.storage.memory.RecordComparator} that is
 * made to be able to instantiate this class outside of the package, as this type is very useful
 * for custom storage implementations.
 *
 * @param <I>
 *         the type of the record identifiers
 * @param <R>
 *         the type of the records to compare
 * @implNote More sophisticated storage implementations can sort records by
 *         non-{@link Comparable} fields like {@link Message message}-type
 *         fields, depending on their storage method (e.g. comparing the string content of
 *         messages).The in-memory implementation stores all column values "as-is" and cannot do
 *         that. Trying to {@linkplain SortBy sort by} column of non-comparable type will lead to
 *         an exception being thrown.
 */
public final class RecordComparator<I, R extends Message>
        implements Comparator<RecordWithColumns<I, R>>, Serializable {

    private static final long serialVersionUID = 0L;

    private final RecordColumn<R, ?> column;

    private RecordComparator(RecordColumn<R, ?> column) {
        this.column = column;
    }

    /**
     * A static factory for {@code EntityRecordComparator} instances.
     *
     * @param sortByList
     *         a specification of columns and the directions for sorting
     * @return a new comparator instance, which uses specified columns and aligns items
     *         in the provided directions
     * @throws IllegalArgumentException
     *         if the provided {@code SortBy} list is empty
     */
    public static <I, R extends Message>
    Comparator<RecordWithColumns<I, R>> accordingTo(List<SortBy<?, R>> sortByList) {
        checkArgument(!sortByList.isEmpty(),
                      "`RecordComparator` requires at least one `SortBy` instance.");
        Comparator<RecordWithColumns<I, R>> result = null;
        for (SortBy<?, R> sortBy : sortByList) {
            Comparator<RecordWithColumns<I, R>> thisComparator;
            Direction direction = sortBy.direction();
            RecordColumn<R, ?> column = sortBy.column();
            thisComparator = direction == Direction.ASC
                             ? ascending(column)
                             : descending(column);
            result = result == null
                     ? thisComparator
                     : result.thenComparing(thisComparator);

        }
        return result;
    }

    private static <I, R extends Message>
    Comparator<RecordWithColumns<I, R>> ascending(RecordColumn<R, ?> column) {
        return new RecordComparator<>(column);
    }

    private static <I, R extends Message>
    Comparator<RecordWithColumns<I, R>> descending(RecordColumn<R, ?> column) {
        return RecordComparator.<I, R>ascending(column)
                               .reversed();
    }

    @Override
    @SuppressWarnings("ChainOfInstanceofChecks" /* Different special cases are covered. */)
    public int compare(RecordWithColumns<I, R> a, RecordWithColumns<I, R> b) {
        checkNotNull(a);
        checkNotNull(b);

        ColumnName columnName = column.name();
        Object aValue = a.columnValue(columnName);
        Object bValue = b.columnValue(columnName);
        if (aValue == null) {
            return bValue == null ? 0 : -1;
        }
        if (bValue == null) {
            return +1;
        }
        if (aValue instanceof Comparable) {
            @SuppressWarnings({"unchecked", "rawtypes"}) // For convenience.
            int result = ((Comparable) aValue).compareTo(bValue);
            return result;
        }

        if (aValue instanceof Timestamp) {
            int result = Timestamps.compare((Timestamp) aValue, (Timestamp) bValue);
            return result;
        }
        throw newIllegalStateException(
                "The message record value is neither a `Comparable` nor a `Timestamp`."
        );
    }
}
