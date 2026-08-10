/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage.given;

import com.google.protobuf.Any;
import io.spine.protobuf.AnyPacker;
import io.spine.query.RecordColumn;
import io.spine.query.RecordQuery;
import io.spine.query.Subject;
import io.spine.server.entity.EntityRecord;
import io.spine.test.entity.Project;

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
     * A {@code Column} that reads the {@code state} of an {@link EntityRecord} as an {@link Any}.
     *
     * <p>Reading a real record field (rather than a constant) lets the current Spine SPI derive
     * per-record column values via {@link io.spine.server.storage.RecordWithColumns#create}.
     */
    public static RecordColumn<EntityRecord, Any> anyColumn() {
        return create("wrapped_state", Any.class, EntityRecord::getState);
    }

    /**
     * A sample {@link Any} value that can be stored in the {@link #anyColumn() any column}.
     */
    public static Any anyValue() {
        var someMessage = Sample.messageOfType(Project.class);
        var value = AnyPacker.pack(someMessage);
        return value;
    }

    /**
     * A {@code Column} that reads the {@code archived} lifecycle flag of an {@link EntityRecord}.
     */
    public static RecordColumn<EntityRecord, Boolean> booleanColumn() {
        return booleanColumn("archived");
    }

    /**
     * A {@code boolean} {@code Column} with a custom name, reading the {@code archived} flag.
     */
    public static RecordColumn<EntityRecord, Boolean> booleanColumn(String name) {
        return create(name, Boolean.class, r -> r.getLifecycleFlags()
                                                 .getArchived());
    }
}
