# Make the admin shard message count idempotent

Fixes [#70][issue-70].

## Goal

Stop the per-shard message count reported by `AdminService` from drifting after
duplicate removals and rewrites of stored inbox messages.

## Rationale

`AdminService` fed a plain counter from `ReportingRecordStorage` notifications:
`+1` per reported write, `-1` per reported delete. The notifications report
*operations*, not *changes*: `deleteAll(ids)` reports every requested ID, even
one already gone or listed twice, and every write is reported, including the
rewrite of a stored message. Both are routine in production — a client retries
`removeMany()` after a transport error, and Spine's `Conveyor` rewrites kept
messages in a batch before removing the delivered ones — so the count went
negative or crept upwards until a restart.

Of the three fixes the issue lists, the idempotent counter — option 2, tracking
the set of message IDs per shard — is the one that costs nothing on the write
path:

- Option 1 (report only real changes) needs an existence check before each
  write. `RecordStorage.read(id)` is query-based, and on Redis and Hazelcast a
  query is a full scan of the map, so the check would dominate every write.
- Option 3 (count on demand) breaks the push updates: the admin UI assigns
  `ShardInfoUpdate.new_messages_count` directly, so every write would have to
  recount the shard.

## Outcome

- `ShardMessagesCountHolder` keeps the set of message IDs per shard and derives
  the count from it: `messageWritten(id)` and `messageRemoved(id)` replace
  `updateCount(index, delta)`. A rewrite or a repeated removal is a no-op, and
  the count can no longer be negative. Within a shard, a message is keyed by
  its UUID string. Memory is proportional to the number of messages currently
  in the inbox, whichever storage backs it, and transient, since the delivery
  removes a message once it is processed.
- `AdminService` fills the holder from storage *after* subscribing, so a
  message written during startup is neither missed nor double-counted.
- The `ShardInfoUpdates.messagesCountChangedTo()` Javadoc no longer claims the
  count may be negative; it explains why the value is not validated.
- `ReportingRecordStorage` and its contract are unchanged: it keeps reporting
  operations, as its tests assert.
- Tests: `ShardMessagesCountHolderSpec` (unit) and `AdminServiceSpec`
  (the end-to-end cases the issue suggests).

## Performance evaluation

A review asked to confirm the choice for throughput: hundreds of shards with
hundreds to thousands of messages each, at 100–200 messages per second. A
throwaway probe (not committed) measured the options on a laptop, with fresh
IDs parsed per operation as gRPC delivers them. 900,000 messages in 300 shards:

| Accounting                      | Cost per operation | Memory per message |
|---------------------------------|--------------------|--------------------|
| Old counter (drifts)            | ~50 ns             | none               |
| Set of `InboxMessageId` per shard | ~200 ns          | 199 B              |
| Set of UUID strings per shard   | ~130 ns            | 118 B              |

On the full in-memory write path, the admin accounting, notifications
included, adds well under a microsecond to a microsecond per operation.

The alternatives cost milliseconds per write, with 50,000 stored messages:

- Option 1 needs an existence check before each write. `RecordStorage.read(id)`
  is query-based, and every storage used here (in-memory, Redis, Hazelcast)
  answers it by scanning all records: 3.7–8 ms per write in memory, growing
  linearly with the inbox, and worse over the network.
- Option 3, counting from storage: 15–22 ms for all shards per call, and
  20–100 ms to recount one shard after a write, since a shard query scans
  every record too.

So option 2 stays, keyed by UUID strings, which takes 40% less memory than
keeping the identifier messages.

## Status

Done — delete this file when the branch merges to master.

[issue-70]: https://github.com/SpineEventEngine/delivery/issues/70
