/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage.redis.given;

import com.google.protobuf.Any;
import io.spine.protobuf.AnyPacker;
import io.spine.query.RecordColumn;
import io.spine.query.RecordQuery;
import io.spine.query.Subject;
import io.spine.server.entity.EntityRecord;
import io.spine.test.entity.Project;
import io.spine.testdata.Sample;

import static io.spine.query.RecordColumn.create;

/**
 * The test environment for {@link io.spine.server.storage.memory.RecordQueryMatcher} tests.
 *
 * <p>Provides various types of {@linkplain RecordColumn record columns}
 * that can be used to emulate a client-side query.
 */
@SuppressWarnings("BadImport")       // `create` looks fine in this context.
public final class RecordQueryMatcherTestEnv {

    /** Prevents instantiation of this test env class. */
    private RecordQueryMatcherTestEnv() {
    }

    /**
     * Creates an empty {@code Subject} for the {@link EntityRecord}.
     */
    public static Subject<Object, EntityRecord> recordSubject() {
        return RecordQuery.newBuilder(Object.class, EntityRecord.class)
                .build()
                .subject();
    }

    /**
     * Creates a {@code Subject} for the {@link EntityRecord} with the given ID.
     */
    public static <I> Subject<I, EntityRecord> recordSubject(I id) {
        return RecordQuery.newBuilder(parameterizedClsOf(id), EntityRecord.class)
                .id()
                .is(id)
                .build()
                .subject();
    }

    @SuppressWarnings("unchecked")  // as per the declaration.
    private static <I> Class<I> parameterizedClsOf(I id) {
        return (Class<I>) id.getClass();
    }

    /**
     * A {@code Column} which holds an {@link Any} instance.
     */
    public static RecordColumn<EntityRecord, Any> anyColumn() {
        return create("wrapped_state", Any.class, (r) -> anyValue());
    }

    /**
     * The {@link Any} value held by the corresponding {@linkplain #anyColumn() entity column}.
     */
    public static Any anyValue() {
        Project someMessage = Sample.messageOfType(Project.class);
        Any value = AnyPacker.pack(someMessage);
        return value;
    }

    /**
     * A {@code Column} which holds a {@code boolean} value.
     */
    public static RecordColumn<EntityRecord, Boolean> booleanColumn() {
        return create("internal", Boolean.class, (r) -> booleanValue());
    }

    /**
     * A {@code Column} which holds a {@code boolean} value.
     */
    public static RecordColumn<EntityRecord, Boolean> booleanColumn(String name) {
        return create(name, Boolean.class, (r) -> booleanValue());
    }

    /**
     * The {@code boolean} value held by the corresponding {@linkplain #booleanColumn() entity
     * column}.
     */
    private static boolean booleanValue() {
        return true;
    }
}
