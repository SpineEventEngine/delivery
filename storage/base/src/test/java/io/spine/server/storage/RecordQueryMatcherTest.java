/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Any;
import io.spine.base.Identifier;
import io.spine.query.RecordColumn;
import io.spine.query.RecordQuery;
import io.spine.query.RecordQueryBuilder;
import io.spine.query.Subject;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.LifecycleFlags;
import io.spine.server.storage.given.Sample;
import io.spine.server.storage.query.RecordQueryMatcher;
import io.spine.test.entity.ProjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.spine.server.storage.given.RecordQueryMatcherTestEnv.anyColumn;
import static io.spine.server.storage.given.RecordQueryMatcherTestEnv.anyValue;
import static io.spine.server.storage.given.RecordQueryMatcherTestEnv.booleanColumn;
import static io.spine.server.storage.given.RecordQueryMatcherTestEnv.recordSubject;
import static io.spine.testing.TestValues.nullRef;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`RecordQueryMatcher` should")
class RecordQueryMatcherTest {

    @Test
    @DisplayName("match everything except `null` to empty query")
    void matchEverythingToEmpty() {
        var sampleSubject = recordSubject();
        var matcher = new RecordQueryMatcher<Object, EntityRecord>(sampleSubject);

        assertFalse(matcher.test(nullRef()));
        assertTrue(matcher.test(recordWith(Object.class, sampleEntityRecord())));
    }

    @Test
    @DisplayName("match IDs")
    void matchIds() {
        var genericId = Sample.messageOfType(ProjectId.class);
        var subject = recordSubject(genericId);

        var matcher = new RecordQueryMatcher<ProjectId, EntityRecord>(subject);
        var matching = sampleEntityRecord(genericId);
        var nonMatching = sampleEntityRecord(Sample.messageOfType(ProjectId.class));
        assertTrue(matcher.test(recordWith(ProjectId.class, matching)));
        assertFalse(matcher.test(recordWith(ProjectId.class, nonMatching)));
    }

    @Test
    @DisplayName("match columns")
    void matchColumns() {
        var column = booleanColumn();
        var actualValue = true;
        var query =
                newBuilder().where(column)
                            .is(actualValue)
                            .build();
        var matcher =
                new RecordQueryMatcher<Object, EntityRecord>(query.subject());

        var matching = archivedEntityRecord(actualValue);
        var nonMatching = archivedEntityRecord(!actualValue);
        assertTrue(matcher.test(recordWith(Object.class, matching)));
        assertFalse(matcher.test(recordWith(Object.class, nonMatching)));
    }

    @Test
    @DisplayName("match `Any` instances")
    void matchAnyInstances() {
        var column = anyColumn();
        var actualValue = anyValue();
        var query =
                newBuilder().where(column)
                            .is(actualValue)
                            .build();
        var matcher = new RecordQueryMatcher<Object, EntityRecord>(query);

        var matching = statefulEntityRecord(actualValue);
        assertTrue(matcher.test(recordWith(Object.class, matching)));
    }

    @Test
    @DisplayName("not match by wrong field name")
    void notMatchByWrongField() {
        var target = booleanColumn("some_random_name");
        var query =
                newBuilder().where(target)
                            .is(true)
                            .build();
        var matcher = new RecordQueryMatcher<Object, EntityRecord>(query);

        assertFalse(matcher.test(recordWith(Object.class, sampleEntityRecord())));
    }

    /**
     * Wraps the given {@code record} into a {@link RecordWithColumns}, deriving the column values
     * via a {@link RecordSpec} that reads the record's fields.
     */
    private static <I> RecordWithColumns<I, EntityRecord>
    recordWith(Class<I> idType, EntityRecord record) {
        return RecordWithColumns.create(record, spec(idType));
    }

    private static <I> RecordSpec<I, EntityRecord> spec(Class<I> idType) {
        return new RecordSpec<>(
                idType,
                EntityRecord.class,
                r -> idType.cast(Identifier.unpack(r.getEntityId())),
                ImmutableList.of(anyColumn(), booleanColumn())
        );
    }

    private static RecordQueryBuilder<Object, EntityRecord> newBuilder() {
        return RecordQuery.newBuilder(Object.class, EntityRecord.class);
    }

    private static EntityRecord sampleEntityRecord() {
        return sampleEntityRecord(Identifier.newUuid());
    }

    private static EntityRecord sampleEntityRecord(Object id) {
        return EntityRecord.newBuilder()
                .setEntityId(Identifier.pack(id))
                .build();
    }

    private static EntityRecord archivedEntityRecord(boolean archived) {
        return EntityRecord.newBuilder()
                .setEntityId(Identifier.pack(Identifier.newUuid()))
                .setLifecycleFlags(LifecycleFlags.newBuilder()
                                           .setArchived(archived))
                .build();
    }

    private static EntityRecord statefulEntityRecord(Any state) {
        return EntityRecord.newBuilder()
                .setEntityId(Identifier.pack(Identifier.newUuid()))
                .setState(state)
                .build();
    }
}
