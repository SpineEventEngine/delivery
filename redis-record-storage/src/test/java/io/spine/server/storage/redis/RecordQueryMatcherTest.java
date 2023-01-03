/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage.redis;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Any;
import io.spine.base.Identifier;
import io.spine.query.ColumnName;
import io.spine.query.RecordColumn;
import io.spine.query.RecordQuery;
import io.spine.query.RecordQueryBuilder;
import io.spine.query.Subject;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.storage.EntityRecordWithColumns;
import io.spine.test.entity.ProjectId;
import io.spine.test.entity.TaskId;
import io.spine.testdata.Sample;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.spine.server.storage.redis.given.RecordQueryMatcherTestEnv.anyColumn;
import static io.spine.server.storage.redis.given.RecordQueryMatcherTestEnv.anyValue;
import static io.spine.server.storage.redis.given.RecordQueryMatcherTestEnv.booleanColumn;
import static io.spine.server.storage.redis.given.RecordQueryMatcherTestEnv.recordSubject;
import static io.spine.testing.TestValues.nullRef;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`RecordQueryMatcher` should")
class RecordQueryMatcherTest {

    @Test
    @DisplayName("match everything except `null` to empty query")
    void matchEverythingToEmpty() {
        Subject<Object, EntityRecord> sampleSubject = recordSubject();
        RecordQueryMatcher<?, EntityRecord> matcher = new RecordQueryMatcher<>(sampleSubject);

        assertFalse(matcher.test(nullRef()));
        assertTrue(matcher.test(EntityRecordWithColumns.of(sampleEntityRecord())));
    }

    @Test
    @DisplayName("match IDs")
    void matchIds() {
        ProjectId genericId = Sample.messageOfType(ProjectId.class);
        Subject<ProjectId, EntityRecord> subject = recordSubject(genericId);

        RecordQueryMatcher<ProjectId, EntityRecord> matcher = new RecordQueryMatcher<>(subject);
        EntityRecord matching = sampleEntityRecord(genericId);
        EntityRecord nonMatching = sampleEntityRecord(Sample.messageOfType(ProjectId.class));
        EntityRecordWithColumns<ProjectId> matchingRecord = EntityRecordWithColumns.of(matching);
        EntityRecordWithColumns<ProjectId> nonMatchingRecord =
                EntityRecordWithColumns.of(nonMatching);
        assertTrue(matcher.test(matchingRecord));
        assertFalse(matcher.test(nonMatchingRecord));
    }

    @Test
    @DisplayName("match columns")
    void matchColumns() {
        RecordColumn<EntityRecord, Boolean> column = booleanColumn();
        boolean actualValue = false;
        ColumnName columnName = column.name();
        RecordQuery<Object, EntityRecord> query =
                newBuilder()
                        .where(column)
                        .is(actualValue)
                        .build();

        RecordQueryMatcher<Object, EntityRecord> matcher =
                new RecordQueryMatcher<>(query.subject());

        EntityRecord matching = sampleEntityRecord(Sample.messageOfType(TaskId.class));
        Map<ColumnName, Object> matchingColumns = ImmutableMap.of(columnName, actualValue);
        EntityRecordWithColumns<Object> matchingRecord =
                EntityRecordWithColumns.of(matching, matchingColumns);

        EntityRecord nonMatching = sampleEntityRecord(Sample.messageOfType(TaskId.class));
        EntityRecordWithColumns<Object> nonMatchingRecord = EntityRecordWithColumns.of(nonMatching);

        assertTrue(matcher.test(matchingRecord));
        assertFalse(matcher.test(nonMatchingRecord));
    }

    @Test
    @DisplayName("match `Any` instances")
    void matchAnyInstances() {
        RecordColumn<EntityRecord, Any> column = anyColumn();
        Any actualValue = anyValue();

        ColumnName columnName = column.name();

        EntityRecord record = sampleEntityRecord();
        Map<ColumnName, Object> columns = singletonMap(columnName, actualValue);
        EntityRecordWithColumns<Object> recordAndCols = EntityRecordWithColumns.of(record, columns);
        RecordQuery<Object, EntityRecord> query = newBuilder().where(column)
                                                              .is(actualValue)
                                                              .build();
        RecordQueryMatcher<Object, EntityRecord> matcher = new RecordQueryMatcher<>(query);
        assertTrue(matcher.test(recordAndCols));
    }

    @Test
    @DisplayName("not match by wrong field name")
    void notMatchByWrongField() {
        RecordColumn<EntityRecord, Boolean> target = booleanColumn("some_random_name");
        RecordQuery<Object, EntityRecord> query =
                newBuilder()
                        .where(target)
                        .is(true)
                        .build();
        RecordQueryMatcher<Object, EntityRecord> matcher = new RecordQueryMatcher<>(query);

        EntityRecord record = sampleEntityRecord();
        EntityRecordWithColumns<Object> recordWithColumns = EntityRecordWithColumns.of(record);
        assertFalse(matcher.test(recordWithColumns));
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
}
