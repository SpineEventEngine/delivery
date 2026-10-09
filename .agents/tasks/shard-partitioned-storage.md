# Shard-partitioned storage for the Delivery server

## Goal

Replace the storage layer of the Delivery server so that every operation costs
in proportion to the shard it touches, or the single message it names, instead
of the whole inbox. Make the distributed modes (Redis, Hazelcast) serve exactly
the same content from every Delivery node. Make the admin message counts exact,
and throttle the admin updates per shard.

The in-memory mode comes first: it must not lose to `master` on the time of
any call at any inbox size measured, nor on heap per stored message. See
"Performance".

This work lands in its own pull request, off `master`, before issue #70 and
PR #77 are resumed. See "Relationship to issue #70 and PR #77".

## Hard constraints

1. **NO MIGRATION.** The new version contains no code that reads, converts,
   detects, or cleans up data stored by previous versions. Old data is deleted
   manually by humans; how and when is out of scope for this work and for this
   document.
2. **The public gRPC API stays the same.** Every service, message, and field of
   `InboxService`, `ShardService`, `AdminService`, and the health service stays
   unchanged. Every observable behavior stays as listed in "Preserved
   behavior"; the only exceptions are listed in "Intentional changes". Existing
   client applications cannot be modified.
3. **The packaging stays the same.** The image name, its entry point, the
   ports, the bundled `redisson-config.yaml` and `hazelcast.yaml`, and the
   meaning of every existing environment variable (`PORT`, `USE_REDIS`,
   `REDIS_HOST`, `REDIS_PORT`, `USE_HAZELCAST`, `HZ_*`,
   `MAX_INBOUND_MESSAGE_SIZE`, `SHARD_PROCESSING_TIMEOUT`, and
   `JAVA_TOOL_OPTIONS` for `-Dhazelcast.config`) stay unchanged. Any new setting
   is optional, with a default that keeps the server working without it.
4. **Single-tenant, as today.** An `InboxMessage` has no tenant field: the
   tenant travels inside the wrapped `Event` or `Command` context, which the
   server stores without looking into it. Core's `InboxStorage` is documented as
   single-tenant, and the server creates both of its storages single-tenant.
   Shard indexes span all tenants. The new storage keeps this model.
5. **No Delivery node is a single point of failure.** In the distributed modes,
   when one Delivery node goes down, the others keep serving exactly the same
   content. The availability of the Redis server itself is outside this work,
   which keeps today's single-server connection.
6. **No counters, and no sets kept only to count or deduplicate messages.** The
   number of messages in a shard is a property of the data structure that holds
   the shard. The indexes below exist for queries, not for counting.

## Problems with the current storage

All three backends implement the generic `RecordStorage` of core-jvm, and the
server reaches them through `InboxStorage` and `ShardRegistryStorage`.

| Problem | Where |
|---|---|
| Every query scans all records of the inbox: the in-memory storage filters its whole map, Redis fetches and deserializes every entry of one big hash, and Hazelcast pulls `entrySet()` of the whole map to the caller. | core-jvm `memory/TenantRecords.findRecords`; `redis/TenantRecords.filterRecords`; `HazelcastRecordStorage.queryRecords` |
| A read by ID is a query too, so it scans the whole inbox as well. | `RecordStorage.read(id)` → `toQuery(id)` |
| The in-memory storage hands out an iterator over a `synchronizedMap` without holding its lock. | core-jvm `memory/TenantRecords.index()` |
| All writes, deletes, reads by ID, and page reads of a node are serialized on one monitor. | `ExtendedInboxStorage` (`synchronized` methods; only `newestMessageToDeliver` is not synchronized) |
| Picking a shard reads and then writes the session record under an in-process lock only, so two nodes can pick the same shard at the same time. Release and expiry follow the same pattern. | `DeliveryShardRegistry` |
| The admin message counts reflect only the operations performed through the node that serves the admin request, and drift. Every write sends one admin update per message. | `AdminService` (issue #70) |

Measured on a laptop, with 300 shards:

| Operation | Messages stored | Cost today |
|---|---|---|
| One shard's page (`FindManyInShard`), in memory | 50,000 | 20–100 ms |
| Read by ID (`FindOne`), in memory | 50,000 | 3.7–8 ms |
| Read all messages, Redis in a local container | 200,000 | 4.4 s |

A Redis or Hazelcast shard query cannot cost less than reading the whole inbox,
because that is what it does.

## Preserved behavior

| gRPC call | Behavior |
|---|---|
| `InboxService.WriteOne` | Stores the message under its ID, replacing a stored message with the same ID. |
| `InboxService.WriteMany` | Same as `WriteOne` for each message. The `shard` field of the request is not used; each message goes to the shard of its own ID. |
| `InboxService.RemoveOne`, `RemoveMany` | Remove the messages with the IDs of the messages in the request; nothing else of those messages is used. `RemoveMany` does not use its `shard` field. An absent ID is not an error. |
| `InboxService.FindOne` | Returns the message with the given ID, if any. |
| `InboxService.FindManyInShard` | Returns messages of the shard with `when_received` strictly after `since_when`, or all of them if `since_when` is unset. Ordered by `when_received`, then by `version`, ascending. At most `page_size` messages. A `page_size` that is not positive fails the call (status `UNKNOWN`, from an `IllegalArgumentException`), as it does today through core's query builder. |
| `InboxService.NewestMessageToDeliver` | Returns the message of the shard in the `TO_DELIVER` status with the latest `when_received`, if any. |
| `ShardService.PickShard` | Succeeds if the shard has no worker, or if its session is stale: the processing timeout is positive and strictly more time than that has passed since `when_last_picked`. On success, the record gets the new worker and `when_last_picked` = now. Otherwise, returns `ShardAlreadyPickedUp` with the current worker and pick time. |
| `ShardService.ReleaseSession` | Clears the worker of the shard's record, if the record exists. The worker in the request is not checked. |
| `ShardService.ReleaseSessions` | Clears the worker of every record whose worker has been picked for at least the given period, and returns those records as they were before the release, with the worker still set. |
| `AdminService.GetShardInfo` | Lists shards with the status, the last pick time, and the number of messages of each. |
| `AdminService.SubscribeToShardUpdates` | Streams `ShardInfoUpdate`s after a shard is picked or released, and after its messages change. |

"Now" is the clock of the node that serves the call, as today, so that the time
provider of tests keeps working.

A known flaw is preserved on purpose: core's `InboxPage` passes the last
`when_received` of a page as the next `since_when`, so messages with an equal
`when_received` that straddle a page boundary are skipped. Fixing it would
change the behavior that clients see.

Unexpected server-side failures, including an unavailable backend, fail the
call with status `UNKNOWN`, as uncaught exceptions do today.

## Intentional changes

| Behavior | Today | After this work | Why |
|---|---|---|---|
| Exclusivity of `PickShard` | Holds within one node only. | Holds across all the nodes that share the storage, with one exception: while a network split divides a Hazelcast cluster, each part may pick the same shard (see "Hazelcast backend"). | Defect: two nodes can pick one shard. |
| Message counts of `AdminService` | Count only the operations of the serving node, and drift (issue #70). | Exact, and the same on every node. | Defect. |
| Shards listed by `GetShardInfo` | Shards of the registry, plus every shard the node has seen since it started, even with a count of zero or below. | Shards of the registry, plus every shard that holds at least one message. | Follows from exact counts. |
| Updates of `SubscribeToShardUpdates` | One update per message written or removed, and one per pick or release, each carrying only the changed field. Nothing is sent before the first change. | Throttled per shard: at most one update per shard per interval (25 ms by default). Each update carries the shard's full current state: status, last pick time, and message count. Right after the acknowledgment, a new subscriber receives the current state of every known shard. See "Admin updates". | The admin clients need the current state and an exact zero, not every intermediate step. In protobuf, a count of 0 in an update that carries only a status looks the same as a real 0, so only full-state updates make zero reliable. |
| The admin UI showing a count of 0 | Ignores updates whose count is 0, so a drained shard keeps its last non-zero count. | Shows 0. | Defect. The UI ships in the image; its interface does not change. |
| Timestamps outside the range of `google.protobuf.Timestamp` (see "Terms") | Accepted on write. Afterwards, any call that compares such a value fails: a page read that sorts two or more messages of the shard, or that has a `since_when` and a non-empty shard, and `NewestMessageToDeliver`. | Compared as plain numbers; nothing fails. | Only a defective client sends such values. Comparing plain numbers removes all validation and keeps writes exactly as today. |
| Ties in `FindManyInShard` and `NewestMessageToDeliver` | Unspecified within an equal `when_received` (and an equal `version` for pages). | Broken by the message UUID, compared as unsigned UTF-8 bytes. | Deterministic results across backends. |
| Java API of `spine-delivery-server` | — | The classes listed in "Removed classes" go away, and the constructors of the services and of `DeliveryShardRegistry` change. `DeliveryServerApp`'s constructors, `main`, `shutdown`, `healthService`, `HOST`, and `PORT` stay. | The classes belong to the replaced storage. This is a breaking change of the Java API, so the minor version is bumped. |

The admin tests that assert exact sequences of updates
(`AdminServiceTest.notifyMessagesWritten`, `notifyMessagesRemoved`,
`notifyMessageRemoved`, `notifyUnpicked`) change to the throttled, full-state
updates. They run with the default interval, and wait for each expected state
before they cause the next change, so that no two changes merge into one update.

## Design

### Terms

- **Valid timestamp**: a `google.protobuf.Timestamp` within the range its
  definition allows, from 0001-01-01T00:00:00Z to 9999-12-31T23:59:59Z: seconds
  from -62,135,596,800 to 253,402,300,799, and nanos from 0 to 999,999,999. The
  wire format accepts any `int64` seconds and any `int32` nanos, and the server
  does not validate incoming messages; Spine's validation runs where a client
  builds a message. So a value outside the range can only come from a defective
  client. This design never checks the range.
- **Order key**: the tuple (`when_received.seconds`, `when_received.nanos`,
  `version`, UUID), compared in that order; the numbers as signed integers, the
  UUID (the `uuid` of the message's `InboxMessageId`) as unsigned UTF-8 bytes,
  as Redis compares strings. Comparing the strings by Unicode code points gives
  the same order without encoding them. For valid timestamps, this is
  chronological order.
  Pages are ordered by the order key ascending; the newest message to deliver is
  the one with the largest.
- **Encoded order key**: the first three components as a 42-character ASCII
  string, `<seconds>:<nanos>:<version>`, used by Redis. Each number is offset
  so that it is never negative, then zero-padded: `seconds` plus 2⁶³ to 20
  digits, `nanos` plus 2³¹ to 10 digits, and `version` plus 2³¹ to 10 digits.
  Every `int64` and `int32` value fits. Comparing two encoded keys byte by byte
  orders them as the numbers they encode. Java computes the offsets without
  overflow, as the unsigned value of the number with its sign bit flipped (for
  example, `Long.toUnsignedString(seconds ^ Long.MIN_VALUE)`); Lua never does
  arithmetic on keys.
- **Shard tag**: `<index>/<ofTotal>` of a `ShardIndex`, in decimal.

### Validation

`InboxService` checks one thing before it calls a store: `page_size` must be
positive. A failed check throws `IllegalArgumentException`, which the call
reports as `UNKNOWN`, the same status that core's check produces today. Nothing
else is validated, as today.

### Two stores in place of the generic record storage

`storage/base` defines two interfaces in Kotlin, implemented by each backend:

```kotlin
interface InboxStore : AutoCloseable {
    fun write(messages: Iterable<InboxMessage>)
    fun delete(ids: Iterable<InboxMessageId>)
    fun find(id: InboxMessageId): InboxMessage?
    fun page(shard: ShardIndex, since: Timestamp?, pageSize: Int): List<InboxMessage>
    fun newestToDeliver(shard: ShardIndex): InboxMessage?
    fun count(shard: ShardIndex): Int
    fun count(shards: Collection<ShardIndex>): Map<ShardIndex, Int>  // includes zeros
    fun counts(): Map<ShardIndex, Int>                                // only shards holding messages
    fun subscribe(onChange: Consumer<ShardIndex>): Subscription
}

interface ShardSessionStore : AutoCloseable {
    fun read(shard: ShardIndex): Stored?
    fun read(shards: Collection<ShardIndex>): Map<ShardIndex, Stored>
    fun readAll(): List<Stored>
    fun compareAndSet(shard: ShardIndex, expected: Stored?,
                      replacement: ShardSessionRecord, writeId: UUID): CasOutcome
    fun subscribe(onChange: Consumer<ShardIndex>): Subscription
}
```

The batched reads, `count(shards)` and `read(shards)`, serve the admin updates.

- `write` and `delete` group their arguments by shard and handle each shard in
  one atomic step, or in a few atomic chunks for the Redis size limits. A batch
  spanning several shards is atomic per shard or chunk, which is no weaker than
  today, where every record is written separately.
- `write` and `delete` are idempotent: executing either twice in a row leaves
  the same state. The backend clients retry commands whose outcome they do not
  know, so this matters. A resend that interleaves with a later operation on the
  same message can still undo that operation, as it can today.
- A `Stored` holds a `ShardSessionRecord`, the write ID it was written with,
  and their exact stored form: in Redis and Hazelcast, the 16 bytes of the
  write ID followed by the bytes of the record; in memory, the record instance.
  `compareAndSet` writes `replacement` with `writeId` only if the stored record
  and write ID still equal `expected`'s, or if there is no record and
  `expected` is `null`. It returns either `APPLIED`, or `CONFLICT` with
  the current `Stored`, if any.
- The write ID lets a writer tell its own record from an equal record written
  by another operation. Every attempt of one operation passes the same write ID,
  and different operations pass different ones. As `compareAndSet` compares
  the write IDs too, a record that an equal record of another operation replaced
  no longer matches the expected one.
- `subscribe` reports the shard of every change made through any node, after the
  change is applied. Every write counts as a change, even one that stores an equal
  message, because telling them apart costs a comparison of whole messages; a
  delete that removes nothing does not. It carries only the shard: whoever reacts
  reads the current state from the store. `close` releases the subscriptions and the resources of
  the store.

### The shard registry on top of `compareAndSet`

`DeliveryShardRegistry` keeps its decision logic and drops `synchronized`.
Every read-then-write becomes: read the `Stored` record, decide in Java, and
`compareAndSet` against it. Each call has a budget of 16 attempts; when it runs
out, the call fails with `IllegalStateException` (status `UNKNOWN`) and a logged
error.

- **`CONFLICT`.** The call decides again from the current record.
- **An exception from `compareAndSet`**, for example a response timeout of the
  Redis client, or a failed Hazelcast operation. The write may or may not have
  been applied, so the call reads the record again and decides again from what
  it finds. The call fails only if that read fails too.
- **`PickShard` recognizes its own write.** The call takes "now" once, builds
  its replacement once, and takes a random UUID as its write ID, so every
  attempt writes the same record with the same write ID. When the record found
  after a `CONFLICT` or an exception carries that write ID, the pick counts as
  applied: the backend client resent the write after a lost reply, the reply
  itself was lost, or an earlier attempt landed late. No other call has that
  write ID, so the record of another call never counts as this call's own, even
  one with the same worker and the same "now". Two concurrent picks of one shard
  by one worker therefore exclude each other, as picks by different workers do.
- **Releases do not look for their own writes.** Each release passes a write ID
  of its own, as every write does, and decides on the record alone.
  - `ReleaseSession` writes the cleared record whenever the record exists, even
    if its worker is already cleared, as it does today.
  - `ReleaseSessions` clears only records that still have a worker, so an
    attempt that decides again and finds the worker cleared does nothing.

`ReleaseSessions` reports a record only when its own `compareAndSet` returned
`APPLIED`, so concurrent calls return disjoint sets. A release whose reply was
lost goes unreported; the result of `ReleaseSessions` is informational.

### The shard as the unit of storage

The in-memory and Hazelcast backends share one data structure per shard,
`ShardInbox`, generic over the form in which a message is held:

- a hash map from the UUID to the held message;
- a sorted map of the held messages, ordered by the order key;
- a sorted map of the held `TO_DELIVER` messages, ordered by the order key,
  created on the first such message.

The sorted maps are `TreeMap`s used directly, without `TreeSet` wrappers, and
hold the same objects as the hash map, with a comparator that reads the order
key from them. So there is no separate key object per message. A `ShardInbox`
is guarded by its own monitor, not a separate lock object.

- **In memory**, a held message is the `InboxMessage` object itself: nothing is
  serialized or parsed, and reads return the stored objects, as `master` does.
- **In Hazelcast**, a held message is the message's protobuf bytes together with
  its order-key fields and its status, extracted by the caller before the
  processor runs. The member therefore holds about as much as `master`'s
  `BINARY` map, and processors never parse protobuf on the partition thread.

A page starts at the first message whose (`seconds`, `nanos`) is strictly
greater than `since_when`, found with a probe that the comparator orders after
every message of exactly that time. So there is no successor arithmetic and no
overflow at the extremes.

| Operation | Cost within a shard of n messages |
|---|---|
| Write or delete one message | O(log n) |
| Find by ID | O(1) |
| Page of k messages | O(log n + k) |
| Newest message to deliver | O(log n) |
| Count | O(1), the size of the hash map |

Overwrites and repeated deletes cannot skew the count, because the hash map
holds each UUID once.

### In-memory backend

- `ConcurrentHashMap<ShardIndex, ShardInbox>`, each `ShardInbox` guarded by its
  own monitor. Operations on different shards never wait on each other.
- A `ShardInbox` is created on the first write to its shard and is never
  removed, so no write can race with a removal. `counts()` skips empty shards.
- The messages are the objects that the gRPC layer parsed. Reads return them
  as they are, as `master` does; protobuf messages are immutable.
- Change listeners are called after the shard's monitor is released. They only
  mark the shard as changed (see "Admin updates"). An exception from a listener
  is caught and logged.
- The shard registry is a `ConcurrentHashMap<ShardIndex, Stored>`.
  `compareAndSet` runs inside `compute` for the shard and compares the stored
  record and write ID with the expected ones by `equals`.

### Hazelcast backend

**Configuration.** The factory loads the configuration with `Config.load()`,
which applies `hazelcast.yaml`, `-Dhazelcast.config`, and the `HZ_*` overrides,
as `newHazelcastInstance()` does today. It then adds the following and starts
the member with `newHazelcastInstance(config)`:

- the map `delivery-inbox`, with the `OBJECT` in-memory format and a synchronous
  backup count of 1;
- the map `delivery-sessions`, with the `BINARY` in-memory format, a synchronous
  backup count of 1, and neither expiration nor eviction, so that a `default`
  map configuration does not apply to it;
- the `DataSerializableFactory` of `ShardInbox` and of the entry processors.

A map configuration of the same name in the loaded configuration is replaced.
Tests pass their own `Config`, for example with an isolated cluster name, to a
constructor of the factory that skips `Config.load()`.

**Inbox.** `delivery-inbox` is an `IMap<String, ShardInbox>` keyed by the shard
tag. Every inbox operation is an entry processor executed on the member that
owns the shard: one network round trip, atomic with respect to every other
operation on that shard. `counts()` runs a read-only processor on all entries.

**Processor contract.**

- Processors check all of their input before they change anything, and are
  deterministic. With the `OBJECT` format, they change the stored `ShardInbox`
  in place, and nothing is rolled back if they throw midway.
- A writing processor that changed the shard ends with `entry.setValue(...)`:
  Hazelcast decides the event type, and whether there is an event at all, from
  that call. A processor that empties a shard removes the entry with
  `setValue(null)`. A processor that changed nothing, such as a delete of absent
  IDs, does not call it, so it causes no event, as the Redis delete script
  publishes nothing in that case.
- Writing processors keep the default backup processor, which re-executes them
  on the backup replica. A backup therefore costs the operation, not the size of
  the shard.
- Read-only processors (`find`, `page`, `newestToDeliver`, `count`, `counts`)
  implement `ReadOnly` and return `null` from `getBackupProcessor()`.
- The inbox map is used only through processors and listeners. No `get`, `put`,
  `values`, or predicate queries, which would serialize whole shards or read
  them off the partition thread.
- `ShardInbox`, the processors, and their results implement
  `IdentifiedDataSerializable`, with a fixed factory ID and class IDs. Results
  carry message bytes, never Java-serialized protobuf objects. Whole shards are
  serialized only when partitions migrate, replicas synchronize, or a
  split-brain is merged.

**Changes.** One cluster-wide entry listener on `delivery-inbox`, registered
without values, so that a `ShardInbox` is never serialized for an event. Each
event reports the shard of its key. The registry map has a listener too, also
without values. Events that a member published right before it crashed can be
lost, and a lost partition loses its entries without events, so a node marks
every known shard as changed when a member leaves the cluster, when a partition
is lost, and after a split-brain merge (a `MembershipListener`,
a `PartitionLostListener`, and the `MERGED` lifecycle event).

**Shard registry.** `delivery-sessions` is an `IMap<String, byte[]>` of
the stored forms of the session records, keyed by the shard tag. `compareAndSet` maps to
`replace(key, expected, replacement)` or `putIfAbsent`, which compare the stored
binary form.

**Durability.** Each partition has a synchronous backup on another member.
When a member leaves, Hazelcast re-invokes the operations in flight to it on the
new owner. That is safe for every operation here:

- inbox processors are deterministic and idempotent;
- a re-invoked `PickShard` write finds its own bytes and counts as applied;
- a re-invoked `ReleaseSession` writes the same cleared record again;
- a re-invoked `ReleaseSessions` attempt decides again and does nothing.

A completed operation is lost only after two failures in a row: its backup was
not acknowledged within Hazelcast's backup timeout (5 seconds by default), and
then its primary member failed before the replicas synchronized. All of this
assumes that the members form one cluster, which is a property of the
deployment.

During a split-brain, both sides may pick one shard, and after the merge
Hazelcast's default `PutIfAbsentMergePolicy` keeps one side's version of each
shard entry, dropping the other side's writes to it. Handling split-brain is out
of scope.

A prototype holding message bytes, as this design does, with 100,000 messages in
300 shards, on one embedded member, took 22.5 µs per write, 0.22 ms per
50-message page, and 0.04 ms per count, and produced exactly one listener event
per write, each write being a processor call that changed its shard. A call to
another member adds one network round trip.

### Redis backend

**Keys.** Each shard owns four keys, sharing a hash tag so that they map to one
slot of a Redis Cluster:

| Key | Type | Content |
|---|---|---|
| `delivery:{inbox:<tag>}:messages` | hash | UUID → message bytes |
| `delivery:{inbox:<tag>}:keys` | hash | UUID → encoded order key |
| `delivery:{inbox:<tag>}:all` | sorted set, all scores 0 | `<encoded order key>:<UUID>`, every message |
| `delivery:{inbox:<tag>}:pending` | sorted set, all scores 0 | `<encoded order key>:<UUID>`, `TO_DELIVER` messages |

The shard registry is one hash, `delivery:{sessions}`, from the shard tag to the
stored form of the session record.

**Encoding.** Keys, hash fields, sorted-set members, and both change channels
use `StringCodec` (UTF-8). Hash values, script arguments, and script results use
`ByteArrayCodec`. Message values are exactly `InboxMessage.toByteArray()`;
registry values are exactly `ShardSessionRecord.toByteArray()`. These formats
are fixed for the life of the stored data.

**Scripts.** Every script receives its keys through `KEYS[]` and runs with
`EVALSHA`, loading itself again on `NOSCRIPT`. Scripts run only commands that
cannot fail on well-formed keys, and the Java side prepares every argument
before the first script call, because a script that fails midway keeps its
earlier writes.

- **Write.** Per shard and chunk. For each message: read its old encoded key
  from `:keys`; if there was one, remove the old member from `:all` and
  `:pending`; then `HSET` the value and the new key, and `ZADD` the new member
  to `:all`, and to `:pending` if the message is `TO_DELIVER`. Finally, publish
  the shard tag on `delivery:changes:inbox`.
- **Delete.** Per shard and chunk. For each UUID with an encoded key in `:keys`:
  remove the member from both sorted sets and the field from both hashes. If
  anything was removed, publish the shard tag as above.
- **Find by ID.** `HGET` on the shard's `:messages`, since the ID names the
  shard.
- **Page.** `ZRANGEBYLEX` on `:all`, from `[<seconds>:<nanos>;` (the encoded
  `since_when`, or `-` without it) to `+`, with `LIMIT 0 page_size`; then one
  `HGET` per member, in a loop. Members whose `when_received` equals
  `since_when` start with `<seconds>:<nanos>:` and sort before the bound,
  because `:` precedes `;`, so `since_when` is exclusive. Within an equal
  encoded key, members are ordered by the UUID bytes, as the order key requires.
- **Newest to deliver.** `ZREVRANGEBYLEX` on `:pending` from `+` to `-` with
  `LIMIT 0 1`, then `HGET`.
- **Count.** `HLEN` of `:messages`. `counts()` finds the shards with `SCAN` over
  `delivery:{inbox:*}:messages`; `SCAN` walks the whole logical database and
  filters afterwards, so it costs in proportion to every key in the database. It
  then pipelines `HLEN`.
- **Registry `compareAndSet`.** A script that writes the replacement only if the
  stored bytes equal the expected ones, or if there is no field and no expected
  record. On success, it publishes the shard tag on `delivery:changes:sessions`.

**Limits.** Write and delete batches are cut into chunks of at most 1,000
messages and 8 MiB of message bytes, with at least one message per chunk. Each
chunk is one script call, which bounds the time a script blocks Redis. Pages
fetch values one `HGET` at a time, so there is no `unpack` limit. A single
message is limited only by Redis's `proto-max-bulk-len` (512 MB by default), as
today.

**Missing pieces.** If a member of `:all` or `:pending` has no value, for
example after someone deleted keys by hand, scripts skip it and report it, and
Java logs a warning. The design assumes that Redis does not evict keys.

**Changes.** Each node holds one subscription to the two channels. Redis
publish/subscribe delivers at most once, so whenever a node's subscription is
established again (`StatusListener.onSubscribe`), the node marks every known
shard as changed (see "Admin updates").

**Connection.** The connection keeps coming from `redisson-config.yaml`
(`redisson-test-config.yaml` in tests), as today. Redis 6 or newer is required,
the version the tests use. The new keys cannot clash with the current layout,
whose keys are named `<tenant>-<ID type>-<record type>`. Two deployments that
share one Redis database share their data, as they do today.

### Admin updates

This is how a node sends `ShardInfoUpdate`s to its admin subscribers, the
clients of `SubscribeToShardUpdates`.

1. **Every node learns which shards changed.** A write, a delete, a pick, or a
   release of a shard, made through any node, reaches every node as "shard X
   changed". In memory the store reports it directly; in Redis it arrives as a
   message on a change channel; in Hazelcast as a map event. Nothing else is
   carried: not the count, not the record.
2. **Each node throttles per shard.** The interval is 25 ms by default. When a
   shard changes and no update for it went out during the last interval, the
   shard is due right away. Otherwise it is due when the interval ends. Any
   further changes during the interval are absorbed into that one update. The
   throttling of one shard never postpones another.
3. **An update carries the shard's current state, read when it is sent.** A
   dedicated thread works in sweeps: it takes all shards that are due, clears
   their "changed" marks, then reads their records and message counts from the
   stores in one batch (one Redis pipeline; one Hazelcast `executeOnKeys` and
   one `getAll`), and sends each shard's full state: status, last pick time,
   and count. Each subscriber gets the update unless it equals the last state
   that subscriber received for the shard.

The rest follows from these three rules:

- **The write path never waits.** Marking a shard as changed is a constant-time
  update of an in-memory map; reading and sending happen on the dedicated
  thread.
- **No change is lost between a mark and a read.** A sweep clears a shard's mark
  before it reads the shard's state. A change that lands during the read marks
  the shard again, so a later sweep picks it up.
- **The final state arrives** within one interval plus one read sweep after the
  last change, including a count of 0, because an update reads the state at
  send time. A sweep's reads are bounded by the backend's call timeout, so a
  stalled backend delays every shard of that sweep by at most that timeout. The
  exception is a change notification that never arrives: Redis delivers them at
  most once, and Hazelcast drops events when its event queue overflows (which
  it logs) or when a member crashes before delivering them; the triggers below
  cover what they can, and the next change of the shard corrects the rest.
- **Memory is bounded** by the number of shards and of subscribers: per shard,
  the time of its last update and whether it is due; per subscriber and shard,
  the last state that subscriber received. Admin subscribers are few. A
  subscriber's states go away with it.
- **Missed changes** are covered by marking every known shard as changed when
  a Redis subscription is re-established, when a Hazelcast member leaves or
  a Hazelcast partition is lost, and after a Hazelcast split-brain merge.
  The known shards are the shards of the registry, the shards in `counts()`,
  and the shards that some subscriber last received with messages, picked, or
  with the time of a pick. A shard emptied in the meantime is then reported with
  0, and a shard whose session record vanished is reported as not picked.
- **A shard that an update cannot carry is left out.** A `ShardInfoUpdate` must
  carry a set shard index, so a shard whose index is not set, which only
  a defective client can write, is never sent, neither in the initial state nor
  later. The failure is logged, and the other shards are sent as usual.
- **Failures are contained.** If a sweep's batched read fails, the sweep marks
  its shards as changed again, so a later sweep retries them; retries back off
  from the larger of 10 ms and the interval, doubling up to one second, while
  the reads keep failing. A failed send to one subscriber is logged and does
  not affect the other subscribers or the other shards.
- **A new subscriber starts from the current state, on the sender thread.** A
  subscriber joins in this order:
  1. the node sends it the acknowledgment;
  2. the node hands it to the sender thread;
  3. on that thread, the node reads the state of every known shard (as defined
     under "Missed changes" above), sends it to this subscriber alone, one
     update per shard, and records each as the subscriber's last received
     state;
  4. only then does the subscriber join the set that sweeps serve.

  Sweeps run on the same thread, so no sweep reaches a subscriber before its
  initial state, and none repeats it. A change that lands after the read marks
  its shard, and the next sweep delivers it. So a change that happens between a
  client's `GetShardInfo` and its subscription, as in the admin UI, which calls
  `GetShardInfo` first, is never lost. If the read fails, it is retried with the
  same backoff before the subscriber joins the sweeps.
- **No subscribers, no work.** While a node has no admin subscribers, the
  listeners do not even mark shards, so it neither marks, reads, nor sends. A
  subscriber counts from step 1 of its joining on, so marking starts before its
  initial read, and a change that lands after that read is marked.
- **Shutdown** stops the sending thread before the stores are closed.

**Configuration.** `AdminService` takes the interval as a constructor parameter.
`DeliveryServerApp` reads it from the optional environment variable
`SHARD_UPDATES_INTERVAL_MILLIS`: a non-negative whole number of milliseconds.
Without the variable, the interval is 25 ms. A value that is not a non-negative
integer stops the server at startup, as an unparsable `SHARD_PROCESSING_TIMEOUT`
does today; unlike that one, the error message names the variable. An interval
of 0 turns throttling off: every change makes its shard due at once. Changes
that land while a read is in flight still merge into the next update, and an
unchanged state is still not sent. A container receives the variable at start,
like every other setting of the image. The parsing of the variable is tested as
a function; the tests of the sender pass the interval to it directly.

`GetShardInfo` reads the stores directly and is always exact.

### The Delivery server on top of the stores

- **One backend connection per node.** In Redis mode, `DeliveryServerApp` opens
  one Redis connection (a `RedissonClient`); in Hazelcast mode, it starts one
  Hazelcast member (a `HazelcastInstance`). Both stores of the node use that
  connection or member. In the in-memory mode, the stores keep the data in the
  server's own memory, so there is no connection or member to create.
- `DeliveryServerApp` passes the stores to the services. On shutdown, it stops
  the admin sender, then closes the subscriptions, the stores, and the
  connection or member, in that order, as `factory.close()` closes the storages
  today.
- `InboxService` checks `page_size`, then calls one store operation.
- `ShardService` uses `DeliveryShardRegistry`, built on `ShardSessionStore`.
- `AdminService.GetShardInfo` combines `ShardSessionStore.readAll()` with
  `InboxStore.counts()`. `SubscribeToShardUpdates` serves the updates described
  in "Admin updates". The startup read of all messages goes away.
- The admin UI applies a count of 0 like any other count.

### Observability

The server logs, without adding metrics:

- registry conflicts that exhaust their retries;
- script errors and missing values in Redis;
- every re-established Redis subscription;
- failed admin update reads or sends;
- Hazelcast member removals and lost partitions.

### Removed classes

- `server`: `ExtendedInboxStorage`, `ShardRegistryStorage`,
  `ReportingStorageFactory`, `ReportingRecordStorage`,
  `SingletonStorageFactory`, `StorageSubscriber`, `StorageSubscription`.
- `grpc-api`: the running-count holder `ShardMessagesCountHolder`, and
  `ShardUpdateSubscribersHolder`, `FilteringObserver`, and
  `TransformingStreamObserver`, which only the old `AdminService` used.
  `ShardInfoUpdates` gets a factory method for an update with the full state,
  which replaces its factories of partial updates.
- `storage/redis`: `RedisRecordStorage`, `RedisStorageFactory`'s record-storage
  API, `MultitenantStorage`, `FlatTenantStorage`, `TenantRecords`,
  `TenantDataStorage`, and their tests. The lookup of `redisson-config.yaml` and
  `redisson-test-config.yaml` stays.
- `storage/hazelcast`: `HazelcastRecordStorage`, `HazelcastStorageFactory`'s
  record-storage API, and their tests. `hazelcast.yaml` and
  `HazelcastConfigSpec` stay.
- `storage/base`: `RecordQueryMatcher`, `InMemoryRecordComparator`, and
  `RecordStorageContractTest` with its fixtures.

None of the storage modules is published from `master`; `server` is, which is
why the minor version is bumped.

## Performance

The in-memory mode is the priority. **In memory, this work must not lose to
`master` on the time of any call at any inbox size measured, nor on the heap
metrics defined below.** A win in the distributed modes does not make up for a
loss in memory.

**How.** One benchmark harness, a gRPC client that calls the public API only,
runs unchanged against a server built from `master` and from this branch. The
gRPC API is the same on both, so the harness is too. It is not committed.

**What.** With 300 shards and inboxes of 100, 10,000, and 200,000 messages:

- the latency of every `InboxService` and `ShardService` call, with one client
  and with concurrent clients;
- the throughput of `WriteMany` and `RemoveMany` at 200 messages per second and
  at the maximum the server sustains;
- the server's heap per stored message, as the slope between 10,000 and 200,000
  messages, (heap(200,000) − heap(10,000)) / 190,000, each heap taken after a
  full garbage collection with `jcmd <pid> GC.run` and `GC.heap_info` on the
  server's process; for the in-memory mode, and for Hazelcast both with one
  member, which holds no backups, and with two members, each of which holds a
  backup of the other's partitions;
- the heap for 300 messages, one per shard: the server's heap with one message
  in each of the 300 shards, minus its heap when empty. It includes the 300
  messages and the fixed cost of their shards, which `master` does not have;
- in the distributed modes, the cost of a node start with a full inbox, which
  on `master` reads every message for the admin counts.

**When it counts as a loss.** Each latency is measured as the median and the
99th percentile over 10 runs; each heap figure over 5 runs. A result loses when
it is worse than `master`'s by more than the spread between those runs on
`master` itself. Results within the spread count as equal; the smallest inbox,
where gRPC overhead dominates, is expected to land there. This rule applies to
the per-message heap slope and to the heap for 300 messages alike.

The in-memory mode is measured first, and any loss there is fixed before the
distributed modes are measured. Redis runs in a local container; Hazelcast runs
with one member, and also with a second, remote member. The results, with the
`master` baseline, go into the pull request.

**Why the in-memory mode should win.** On `master`, every write builds a record
whose constructor copies nine column values into a new `HashMap`, roughly
470–480 bytes per message on top of the message itself, and every query,
including a read by ID, scans all messages. Here, a write adds the message
object to one hash map and one or two `TreeMap`s of its shard, roughly 80–120
bytes per message, and every read is a lookup within one shard. A shard costs a
few hundred bytes once; the harness checks that this does not make small inboxes
lose.

## Alternatives considered

- **Hazelcast, one entry per message, with indexes on the shard and the order
  key.** Rejected: Hazelcast does not co-locate a shard's entries by default,
  and a prototype's paged query combining the shard with a time range took
  30–64 ms over 100,000 entries, because the time range matched about half of
  them.
- **Hazelcast, one entry per message, co-located per shard by a partitioning
  strategy, with a sorted index and a `PartitionPredicate`.** Rejected: every
  page still runs the query engine and deserializes each matching entry, index
  maintenance is paid per message, a count is a query, and change events come
  per message. The shard-per-entry design does each operation in one processor
  call, counts in O(1), and yields one event per change of a shard.
- **Redis, the current single hash, read with `HSCAN MATCH` on the shard inside
  the field names.** Rejected: Redis would still walk every message of the inbox
  for each page.

## Relationship to issue #70 and PR #77

- This work resolves issue #70: exact counts, the same on every node, throttled
  per shard, with full-state updates and the admin UI showing 0.
- PR #77's accounting with ID sets is dropped. The rest of PR #77 (the module
  renames and the publication of the storage modules, the Kotlin
  `test-artifacts` script, the isolated Hazelcast tests, and the switch to
  `io.grpc:grpc-services`) is redone on top of this work, and its version bump
  moves above this work's.

## Out of scope

- Redis connection modes other than the single server configured today, such as
  Sentinel or Cluster. The hash tags and `KEYS[]` keep the layout ready for
  them.
- Handling of a Hazelcast split-brain beyond its defaults.
- A configurable key prefix for Redis.

## Testing

- **Contract suites** for `InboxStore` and `ShardSessionStore`, run against all
  three backends: Redis in a Testcontainers container, gated by the existing
  Docker checks, and Hazelcast with an isolated cluster name and multicast off.
  They cover:
  - `since_when` exclusivity, and ordering by version and by UUID within an
    equal `when_received`;
  - an overwrite that changes the status, the receive time, or the version;
  - negative versions, and timestamps inside and outside the valid range,
    including the extremes of `int64` and `int32`;
  - deleting an absent ID, duplicates within a batch, and a batch spanning
    several shards;
  - a page larger than 8,000 messages, a batch larger than one chunk, and
    messages close to `MAX_INBOUND_MESSAGE_SIZE`;
  - the newest message to deliver, with and without candidates;
  - counts after every kind of change, including zero, and empty shards left
    out of `counts()`;
  - change notifications from every kind of change, and none from a change that
    changes nothing;
  - `compareAndSet` with and without an expected record, executed twice in a
    row, against a stale expected record, and against an expected record that
    an equal record of another write replaced;
  - a `compareAndSet` that applies its write and then throws, injected through a
    test decorator of the store: `PickShard` must succeed, and `ReleaseSessions`
    must not report the session;
  - a `PickShard` attempt that lands after a later attempt of the same call has
    been sent: the call still succeeds;
  - a pick of the same shard by the same worker at the same "now", made through
    another registry between a pick's read and its write: the later pick fails
    with `ShardAlreadyPickedUp`.
- **Validation**: a non-positive page size fails the call with `UNKNOWN`, the status
  that `master` produces for it, as "Validation" explains.
- **Admin updates**:
  - the first change of a shard is sent at once; changes within the interval
    become one update at its end, with the final state;
  - concurrent writers to one shard: the last update equals `count()` and the
    shard's record;
  - a new subscriber's initial state is not sent to it a second time by the
    sweep of a change it already reflects;
  - a change concurrent with a new subscription: the subscriber ends at the
    current state, without a duplicate;
  - a subscriber that joins after another one received a state still gets that
    state;
  - a Hazelcast member leaving, and a split-brain merge, mark every known shard
    as changed;
  - the parsing of `SHARD_UPDATES_INTERVAL_MILLIS`, tested as a function:
    absent gives 25 ms, 0 is accepted, and a negative or unparsable value is
    rejected with a message naming the variable;
  - a batched read that fails marks its shards again, and a later sweep sends
    their updates;
  - a change back to the state a subscriber last received sends nothing to it;
  - shards are throttled independently;
  - every update carries the status, the last pick time, and the count,
    including a count of 0;
  - the interval comes from `SHARD_UPDATES_INTERVAL_MILLIS`, defaults to 25 ms,
    and 0 turns throttling off;
  - a re-established Redis subscription reports a shard emptied in the
    meantime with 0;
  - a failed send to one subscriber does not stop the others; nothing is read
    while there are no subscribers; shutdown stops the sender;
  - a new subscriber first receives the current state of every known shard,
    including a change made between its `GetShardInfo` and its subscription.
- **Multi-node tests** with two stores on one Redis, and with two Hazelcast
  members, in the storage modules, which already run Docker-based suites:
  - concurrent creations of one session record let exactly one win, which is
    what makes concurrent picks, and concurrent `ReleaseSessions` calls,
    exclusive; the registry's decisions on top of `compareAndSet` are tested
    in the server against a store that injects conflicts and lost replies;
  - a change made through one node is reported to the subscribers of the other;
  - dropping Redis's publish/subscribe connections makes the stores report
    missed changes once the subscription is established again, and
    a Hazelcast member leaving does the same; the server tests that missed
    changes make the admin sender re-read every known shard, including one
    emptied in the meantime;
  - Hazelcast keeps all data after one member is terminated
    (`getLifecycleService().terminate()`, which skips the graceful migration).
- **The existing gRPC-level suites** of the server, and the Docker-based
  integration suites: their assertions stay, except the admin tests listed in
  "Intentional changes"; fixtures that build services from a `StorageFactory`,
  such as `ShardServiceTestEnv`, are adapted.
- **Performance**: see "Performance".

## Plan

- [x] Review this document.
- [x] Add the store interfaces, `ShardInbox`, and the contract suites to
      `storage/base`. The order-key codec moves to the Redis step, its only user.
- [x] Implement the in-memory backend.
- [x] Move the server onto the stores, with the admin updates and their
      configuration; remove the classes listed above; fix the admin UI's zero.
- [x] Measure the in-memory mode against `master`; fix any loss. No result
      loses; the results are in the pull request.
- [x] Implement the Hazelcast backend.
- [x] Implement the Redis backend.
- [x] Push the branch only from here on. Until both new backends exist, the
      Docker-based `DistributedTest`, which runs Hazelcast containers built from
      the working tree, cannot pass, so no earlier state is pushed.
- [x] Add the multi-node tests.
- [x] Keep a write ID per call with each session record, so that a pick never
      takes the record of another call for its own write.
- [ ] Measure the distributed modes against `master`; record all results in the
      pull request.
- [x] Update `server/README.md` and `docs/project.md`: the storage modes, the
      consistency of the Hazelcast mode, `SHARD_UPDATES_INTERVAL_MILLIS`, and
      the stale-session threshold, which the code applies when strictly more
      than the timeout has passed (the README says "equal to or more").
- [x] Bump the minor version.
- [x] Run `./gradlew clean build dokkaGenerate`.

## Status

In review — delete this file when the branch merges to master.
