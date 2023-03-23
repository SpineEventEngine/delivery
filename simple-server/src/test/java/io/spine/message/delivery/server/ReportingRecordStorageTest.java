/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.spine.message.delivery.server.event.TestEvent;
import io.spine.message.delivery.server.event.TestEventId;
import io.spine.message.delivery.server.given.MemoizingStorageSubscriber;
import io.spine.message.delivery.server.given.MemoizingStorageSubscriber.SingleWrite;
import io.spine.message.delivery.server.given.DirectCallStorage;
import io.spine.message.delivery.server.given.ChainingCallStorage;
import io.spine.server.storage.RecordWithColumns;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.message.delivery.server.given.StoragesTestEnv.newTestContext;
import static io.spine.message.delivery.server.given.StoragesTestEnv.testEventWith;

@DisplayName("`ReportingRecordStorage` should")
final class ReportingRecordStorageTest {

    @Test
    @DisplayName("handle `unsubscribe` of the subscription properly")
    void unsubscribe() {
        var underTest =
                new ReportingRecordStorage<>(newTestContext(), new ChainingCallStorage());
        var subscriber = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
        var subscription = underTest.subscribe(subscriber);

        TestEventId firstId = TestEventId.generate();
        TestEvent firstRecord = testEventWith(firstId);

        underTest.write(firstId, firstRecord);
        subscription.cancel();

        TestEventId secondId =TestEventId.generate();
        TestEvent secondRecord = testEventWith(secondId);

        underTest.write(secondId, secondRecord);

        assertThat(subscriber.writes())
                .containsExactly(SingleWrite.of(firstId, firstRecord));
    }

    @Nested
    @DisplayName("if storage methods delegate to each other")
    class WithChainingCallStorage {

        private ReportingRecordStorage<TestEventId, TestEvent> underTest;

        @BeforeEach
        void setup() {
            underTest = new ReportingRecordStorage<>(
                    newTestContext(), new ChainingCallStorage()
            );
        }

        @Test
        @DisplayName("report `write(id, record)` call once")
        void reportWriteOnce() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId id = TestEventId.generate();
            TestEvent record = testEventWith(id);

            underTest.write(id, record);

            assertThat(sub.writes())
                    .containsExactly(SingleWrite.of(id, record));
        }

        @Test
        @DisplayName("report `write(record)` call once")
        void reportWriteWithRecordOnce() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId id = TestEventId.generate();
            TestEvent record = testEventWith(id);

            underTest.write(RecordWithColumns.of(id, record));

            assertThat(sub.writes())
                    .containsExactly(SingleWrite.of(id, record));
        }

        @Test
        @DisplayName("report `writeRecord(record)` call once")
        void reportWriteRecordOnce() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId id = TestEventId.generate();
            TestEvent record = testEventWith(id);

            underTest.writeRecord(RecordWithColumns.of(id, record));

            assertThat(sub.writes())
                    .containsExactly(SingleWrite.of(id, record));
        }

        @Test
        @DisplayName("report `writeAll(records)` call once")
        void reportWriteAllOnce() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId id = TestEventId.generate();
            TestEvent record = testEventWith(id);

            underTest.writeAll(List.of(RecordWithColumns.of(id, record)));

            assertThat(sub.writes())
                    .containsExactly(SingleWrite.of(id, record));
        }

        @Test
        @DisplayName("report `writeAllRecords(records)` call once")
        void reportWriteAllRecordsOnce() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId id = TestEventId.generate();
            TestEvent record = testEventWith(id);

            underTest.writeAllRecords(List.of(RecordWithColumns.of(id, record)));

            assertThat(sub.writes())
                    .containsExactly(SingleWrite.of(id, record));
        }

        @Test
        @DisplayName("report all entities written with `writeAll(records)` call once")
        void reportAllWriteAllOnce() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId firstId = TestEventId.generate();
            TestEvent firstRecord = testEventWith(firstId);

            TestEventId secondId = TestEventId.generate();
            TestEvent secondRecord = testEventWith(secondId);

            underTest.writeAll(List.of(RecordWithColumns.of(firstId, firstRecord),
                                       RecordWithColumns.of(secondId, secondRecord)));

            assertThat(sub.writes())
                    .containsExactly(SingleWrite.of(firstId, firstRecord),
                                     SingleWrite.of(secondId, secondRecord));
        }

        @Test
        @DisplayName("report all entities written with `writeAllRecords(records)` call once")
        void reportAllWriteAllRecordsOnce() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId firstId = TestEventId.generate();
            TestEvent firstRecord = testEventWith(firstId);

            TestEventId secondId = TestEventId.generate();
            TestEvent secondRecord = testEventWith(secondId);

            underTest.writeAllRecords(List.of(RecordWithColumns.of(firstId, firstRecord),
                                              RecordWithColumns.of(secondId, secondRecord)));

            assertThat(sub.writes())
                    .containsExactly(SingleWrite.of(firstId, firstRecord),
                                     SingleWrite.of(secondId, secondRecord));
        }

        @Test
        @DisplayName("report `delete(id)` call once")
        void reportDeleteOnce() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId id = TestEventId.generate();
            TestEvent record = testEventWith(id);
            underTest.write(id, record);

            underTest.delete(id);

            assertThat(sub.deletions())
                    .containsExactly(id);
        }

        @Test
        @DisplayName("report `deleteRecord(id)` call once")
        void reportDeleteRecordOnce() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId id = TestEventId.generate();
            TestEvent record = testEventWith(id);
            underTest.write(id, record);

            underTest.deleteRecord(id);

            assertThat(sub.deletions())
                    .containsExactly(id);
        }

        @Test
        @DisplayName("report all deletions with `deleteAll(ids)` call once")
        void reportAllDeleteAllIdsOnce() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId firstId = TestEventId.generate();
            TestEvent firstRecord = testEventWith(firstId);

            TestEventId secondId = TestEventId.generate();
            TestEvent secondRecord = testEventWith(secondId);

            underTest.writeAllRecords(List.of(RecordWithColumns.of(firstId, firstRecord),
                                              RecordWithColumns.of(secondId, secondRecord)));

            underTest.deleteAll(List.of(firstId, secondId));

            assertThat(sub.deletions())
                    .containsExactly(firstId, secondId);
        }

        @Test
        @DisplayName("report only actually deleted entities with `delete(id)` call")
        void reportOnlyDeletedWithDelete() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId id = TestEventId.generate();
            TestEvent record = testEventWith(id);
            underTest.write(id, record);

            underTest.delete(id);
            underTest.delete(id);

            assertThat(sub.deletions())
                    .containsExactly(id);
        }

        @Test
        @DisplayName("report only actually deleted entities with `deleteRecord(id)` call")
        void reportOnlyDeletedWithDeleteRecord() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId id = TestEventId.generate();
            TestEvent record = testEventWith(id);
            underTest.write(id, record);

            underTest.deleteRecord(id);
            underTest.deleteRecord(id);

            assertThat(sub.deletions())
                    .containsExactly(id);
        }

        @Test
        @DisplayName("report all if deleting duplicates with `deleteAll(ids)` call")
        void reportAllIfDeleteAll() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId firstId = TestEventId.generate();
            TestEvent firstRecord = testEventWith(firstId);

            TestEventId secondId = TestEventId.generate();
            TestEvent secondRecord = testEventWith(secondId);

            underTest.writeAllRecords(List.of(RecordWithColumns.of(firstId, firstRecord),
                                              RecordWithColumns.of(secondId, secondRecord)));

            underTest.deleteAll(List.of(firstId, secondId, firstId));

            assertThat(sub.deletions())
                    .containsExactly(firstId, secondId, firstId);
        }

        @Nested
        @DisplayName("and if one intentionally writes identical entities")
        class WriteIdentical {

            @Test
            @DisplayName("report all calls to `write(id, record)`")
            void reportAllWrites() {
                var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
                underTest.subscribe(sub);

                TestEventId id = TestEventId.generate();
                TestEvent record = testEventWith(id);

                underTest.write(id, record);
                underTest.write(id, record);

                assertThat(sub.writes())
                        .containsExactly(SingleWrite.of(id, record), SingleWrite.of(id, record));
            }

            @Test
            @DisplayName("report all calls to `write(record)`")
            void reportWriteWithRecord() {
                var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
                underTest.subscribe(sub);

                TestEventId id = TestEventId.generate();
                TestEvent record = testEventWith(id);

                underTest.write(RecordWithColumns.of(id, record));
                underTest.write(RecordWithColumns.of(id, record));

                assertThat(sub.writes())
                        .containsExactly(SingleWrite.of(id, record), SingleWrite.of(id, record));
            }

            @Test
            @DisplayName("report all calls to `writeRecord(record)`")
            void reportWriteRecord() {
                var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
                underTest.subscribe(sub);

                TestEventId id = TestEventId.generate();
                TestEvent record = testEventWith(id);

                underTest.writeRecord(RecordWithColumns.of(id, record));
                underTest.writeRecord(RecordWithColumns.of(id, record));

                assertThat(sub.writes())
                        .containsExactly(SingleWrite.of(id, record), SingleWrite.of(id, record));
            }

            @Test
            @DisplayName("report all calls to `writeAll(records)`")
            void reportWriteAll() {
                var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
                underTest.subscribe(sub);

                TestEventId id = TestEventId.generate();
                TestEvent record = testEventWith(id);

                underTest.writeAll(List.of(RecordWithColumns.of(id, record)));
                underTest.writeAll(List.of(RecordWithColumns.of(id, record)));

                assertThat(sub.writes())
                        .containsExactly(SingleWrite.of(id, record), SingleWrite.of(id, record));
            }

            @Test
            @DisplayName("report all calls to `writeAllRecords(records)`")
            void reportWriteAllRecords() {
                var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
                underTest.subscribe(sub);

                TestEventId id = TestEventId.generate();
                TestEvent record = testEventWith(id);

                underTest.writeAllRecords(List.of(RecordWithColumns.of(id, record)));
                underTest.writeAllRecords(List.of(RecordWithColumns.of(id, record)));

                assertThat(sub.writes())
                        .containsExactly(SingleWrite.of(id, record), SingleWrite.of(id, record));
            }

            @Test
            @DisplayName("report all entities in all calls written with `writeAll(records)`")
            void reportAllWriteAll() {
                var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
                underTest.subscribe(sub);

                TestEventId firstId = TestEventId.generate();
                TestEvent firstRecord = testEventWith(firstId);

                TestEventId secondId = TestEventId.generate();
                TestEvent secondRecord = testEventWith(secondId);

                underTest.writeAll(List.of(RecordWithColumns.of(firstId, firstRecord),
                                           RecordWithColumns.of(secondId, secondRecord)));
                underTest.writeAll(List.of(RecordWithColumns.of(firstId, firstRecord),
                                           RecordWithColumns.of(secondId, secondRecord)));

                assertThat(sub.writes())
                        .containsExactly(SingleWrite.of(firstId, firstRecord),
                                         SingleWrite.of(secondId, secondRecord),
                                         SingleWrite.of(firstId, firstRecord),
                                         SingleWrite.of(secondId, secondRecord));
            }

            @Test
            @DisplayName("report all entities in all calls written with `writeAllRecords(records)`")
            void reportAllWriteAllRecords() {
                var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
                underTest.subscribe(sub);

                TestEventId firstId = TestEventId.generate();
                TestEvent firstRecord = testEventWith(firstId);

                TestEventId secondId = TestEventId.generate();
                TestEvent secondRecord = testEventWith(secondId);

                underTest.writeAllRecords(List.of(RecordWithColumns.of(firstId, firstRecord),
                                                  RecordWithColumns.of(secondId, secondRecord)));
                underTest.writeAllRecords(List.of(RecordWithColumns.of(firstId, firstRecord),
                                                  RecordWithColumns.of(secondId, secondRecord)));

                assertThat(sub.writes())
                        .containsExactly(SingleWrite.of(firstId, firstRecord),
                                         SingleWrite.of(secondId, secondRecord),
                                         SingleWrite.of(firstId, firstRecord),
                                         SingleWrite.of(secondId, secondRecord));
            }
        }
    }

    @Nested
    @DisplayName("if storage methods doesn't delegate to each other")
    class WithDirectCallStorage {

        private ReportingRecordStorage<TestEventId, TestEvent> underTest;

        @BeforeEach
        void setup() {
            underTest = new ReportingRecordStorage<>(
                    newTestContext(), new DirectCallStorage()
            );
        }

        @Test
        @DisplayName("report `write(id, record)` call once")
        void reportWriteOnce() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId id = TestEventId.generate();
            TestEvent record = testEventWith(id);

            underTest.write(id, record);

            assertThat(sub.writes())
                    .containsExactly(SingleWrite.of(id, record));
        }

        @Test
        @DisplayName("report `write(record)` call once")
        void reportWriteWithRecordOnce() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId id = TestEventId.generate();
            TestEvent record = testEventWith(id);

            underTest.write(RecordWithColumns.of(id, record));

            assertThat(sub.writes())
                    .containsExactly(SingleWrite.of(id, record));
        }

        @Test
        @DisplayName("report `writeRecord(record)` call once")
        void reportWriteRecordOnce() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId id = TestEventId.generate();
            TestEvent record = testEventWith(id);

            underTest.writeRecord(RecordWithColumns.of(id, record));

            assertThat(sub.writes())
                    .containsExactly(SingleWrite.of(id, record));
        }

        @Test
        @DisplayName("report `writeAll(records)` call once")
        void reportWriteAllOnce() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId id = TestEventId.generate();
            TestEvent record = testEventWith(id);

            underTest.writeAll(List.of(RecordWithColumns.of(id, record)));

            assertThat(sub.writes())
                    .containsExactly(SingleWrite.of(id, record));
        }

        @Test
        @DisplayName("report `writeAllRecords(records)` call once")
        void reportWriteAllRecordsOnce() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId id = TestEventId.generate();
            TestEvent record = testEventWith(id);

            underTest.writeAllRecords(List.of(RecordWithColumns.of(id, record)));

            assertThat(sub.writes())
                    .containsExactly(SingleWrite.of(id, record));
        }

        @Test
        @DisplayName("report all entities written with `writeAll(records)` call once")
        void reportAllWriteAllOnce() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId firstId = TestEventId.generate();
            TestEvent firstRecord = testEventWith(firstId);

            TestEventId secondId = TestEventId.generate();
            TestEvent secondRecord = testEventWith(secondId);

            underTest.writeAll(List.of(RecordWithColumns.of(firstId, firstRecord),
                                       RecordWithColumns.of(secondId, secondRecord)));

            assertThat(sub.writes())
                    .containsExactly(SingleWrite.of(firstId, firstRecord),
                                     SingleWrite.of(secondId, secondRecord));
        }

        @Test
        @DisplayName("report all entities written with `writeAllRecords(records)` call once")
        void reportAllWriteAllRecordsOnce() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId firstId = TestEventId.generate();
            TestEvent firstRecord = testEventWith(firstId);

            TestEventId secondId = TestEventId.generate();
            TestEvent secondRecord = testEventWith(secondId);

            underTest.writeAllRecords(List.of(RecordWithColumns.of(firstId, firstRecord),
                                              RecordWithColumns.of(secondId, secondRecord)));

            assertThat(sub.writes())
                    .containsExactly(SingleWrite.of(firstId, firstRecord),
                                     SingleWrite.of(secondId, secondRecord));
        }

        @Test
        @DisplayName("report `delete(id)` call once")
        void reportDeleteOnce() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId id = TestEventId.generate();
            TestEvent record = testEventWith(id);
            underTest.write(id, record);

            underTest.delete(id);

            assertThat(sub.deletions())
                    .containsExactly(id);
        }

        @Test
        @DisplayName("report `deleteRecord(id)` call once")
        void reportDeleteRecordOnce() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId id = TestEventId.generate();
            TestEvent record = testEventWith(id);
            underTest.write(id, record);

            underTest.deleteRecord(id);

            assertThat(sub.deletions())
                    .containsExactly(id);
        }

        @Test
        @DisplayName("report all deletions with `deleteAll(ids)` call once")
        void reportAllDeleteAllIdsOnce() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId firstId = TestEventId.generate();
            TestEvent firstRecord = testEventWith(firstId);

            TestEventId secondId = TestEventId.generate();
            TestEvent secondRecord = testEventWith(secondId);

            underTest.writeAllRecords(List.of(RecordWithColumns.of(firstId, firstRecord),
                                              RecordWithColumns.of(secondId, secondRecord)));

            underTest.deleteAll(List.of(firstId, secondId));

            assertThat(sub.deletions())
                    .containsExactly(firstId, secondId);
        }

        @Test
        @DisplayName("report only actually deleted entities with `delete(id)` call")
        void reportOnlyDeletedWithDelete() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId id = TestEventId.generate();
            TestEvent record = testEventWith(id);
            underTest.write(id, record);

            underTest.delete(id);
            underTest.delete(id);

            assertThat(sub.deletions())
                    .containsExactly(id);
        }

        @Test
        @DisplayName("report only actually deleted entities with `deleteRecord(id)` call")
        void reportOnlyDeletedWithDeleteRecord() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId id = TestEventId.generate();
            TestEvent record = testEventWith(id);
            underTest.write(id, record);

            underTest.deleteRecord(id);
            underTest.deleteRecord(id);

            assertThat(sub.deletions())
                    .containsExactly(id);
        }

        @Test
        @DisplayName("report all if deleting duplicates with `deleteAll(ids)` call")
        void reportAllIfDeleteAll() {
            var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
            underTest.subscribe(sub);

            TestEventId firstId = TestEventId.generate();
            TestEvent firstRecord = testEventWith(firstId);

            TestEventId secondId = TestEventId.generate();
            TestEvent secondRecord = testEventWith(secondId);

            underTest.writeAllRecords(List.of(RecordWithColumns.of(firstId, firstRecord),
                                              RecordWithColumns.of(secondId, secondRecord)));

            underTest.deleteAll(List.of(firstId, secondId, firstId));

            assertThat(sub.deletions())
                    .containsExactly(firstId, secondId, firstId);
        }

        @Nested
        @DisplayName("and if one intentionally writes identical entities")
        class WriteIdentical {

            @Test
            @DisplayName("report all calls to `write(id, record)`")
            void reportAllWrites() {
                var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
                underTest.subscribe(sub);

                TestEventId id = TestEventId.generate();
                TestEvent record = testEventWith(id);

                underTest.write(id, record);
                underTest.write(id, record);

                assertThat(sub.writes())
                        .containsExactly(SingleWrite.of(id, record), SingleWrite.of(id, record));
            }

            @Test
            @DisplayName("report all calls to `write(record)`")
            void reportWriteWithRecord() {
                var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
                underTest.subscribe(sub);

                TestEventId id = TestEventId.generate();
                TestEvent record = testEventWith(id);

                underTest.write(RecordWithColumns.of(id, record));
                underTest.write(RecordWithColumns.of(id, record));

                assertThat(sub.writes())
                        .containsExactly(SingleWrite.of(id, record), SingleWrite.of(id, record));
            }

            @Test
            @DisplayName("report all calls to `writeRecord(record)`")
            void reportWriteRecord() {
                var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
                underTest.subscribe(sub);

                TestEventId id = TestEventId.generate();
                TestEvent record = testEventWith(id);

                underTest.writeRecord(RecordWithColumns.of(id, record));
                underTest.writeRecord(RecordWithColumns.of(id, record));

                assertThat(sub.writes())
                        .containsExactly(SingleWrite.of(id, record), SingleWrite.of(id, record));
            }

            @Test
            @DisplayName("report all calls to `writeAll(records)`")
            void reportWriteAll() {
                var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
                underTest.subscribe(sub);

                TestEventId id = TestEventId.generate();
                TestEvent record = testEventWith(id);

                underTest.writeAll(List.of(RecordWithColumns.of(id, record)));
                underTest.writeAll(List.of(RecordWithColumns.of(id, record)));

                assertThat(sub.writes())
                        .containsExactly(SingleWrite.of(id, record), SingleWrite.of(id, record));
            }

            @Test
            @DisplayName("report all calls to `writeAllRecords(records)`")
            void reportWriteAllRecords() {
                var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
                underTest.subscribe(sub);

                TestEventId id = TestEventId.generate();
                TestEvent record = testEventWith(id);

                underTest.writeAllRecords(List.of(RecordWithColumns.of(id, record)));
                underTest.writeAllRecords(List.of(RecordWithColumns.of(id, record)));

                assertThat(sub.writes())
                        .containsExactly(SingleWrite.of(id, record), SingleWrite.of(id, record));
            }

            @Test
            @DisplayName("report all entities in all calls written with `writeAll(records)`")
            void reportAllWriteAll() {
                var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
                underTest.subscribe(sub);

                TestEventId firstId = TestEventId.generate();
                TestEvent firstRecord = testEventWith(firstId);

                TestEventId secondId = TestEventId.generate();
                TestEvent secondRecord = testEventWith(secondId);

                underTest.writeAll(List.of(RecordWithColumns.of(firstId, firstRecord),
                                           RecordWithColumns.of(secondId, secondRecord)));
                underTest.writeAll(List.of(RecordWithColumns.of(firstId, firstRecord),
                                           RecordWithColumns.of(secondId, secondRecord)));

                assertThat(sub.writes())
                        .containsExactly(SingleWrite.of(firstId, firstRecord),
                                         SingleWrite.of(secondId, secondRecord),
                                         SingleWrite.of(firstId, firstRecord),
                                         SingleWrite.of(secondId, secondRecord));
            }

            @Test
            @DisplayName("report all entities in all calls written with `writeAllRecords(records)`")
            void reportAllWriteAllRecords() {
                var sub = new MemoizingStorageSubscriber<TestEventId, TestEvent>();
                underTest.subscribe(sub);

                TestEventId firstId = TestEventId.generate();
                TestEvent firstRecord = testEventWith(firstId);

                TestEventId secondId = TestEventId.generate();
                TestEvent secondRecord = testEventWith(secondId);

                underTest.writeAllRecords(List.of(RecordWithColumns.of(firstId, firstRecord),
                                                  RecordWithColumns.of(secondId, secondRecord)));
                underTest.writeAllRecords(List.of(RecordWithColumns.of(firstId, firstRecord),
                                                  RecordWithColumns.of(secondId, secondRecord)));

                assertThat(sub.writes())
                        .containsExactly(SingleWrite.of(firstId, firstRecord),
                                         SingleWrite.of(secondId, secondRecord),
                                         SingleWrite.of(firstId, firstRecord),
                                         SingleWrite.of(secondId, secondRecord));
            }
        }
    }
}
