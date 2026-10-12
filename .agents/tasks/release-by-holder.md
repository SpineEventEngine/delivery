# Release a shard only by the worker that holds it

Issue: [#73](https://github.com/SpineEventEngine/delivery/issues/73), part of #71.

## Goal

A `ReleaseShard` request clears the session of the shard only when the worker
of the request holds the shard. A release by any other worker leaves the session
intact and logs a warning.

## Rationale

`DeliveryShardRegistry.releaseShard(ShardIndex)` cleared the worker of any
record, and `ShardService.releaseSession` ignored `ReleaseShard.worker`. So when
worker B picked a shard whose session of worker A went stale, A's release
(or a late retry of it) cleared B's session, and worker C could pick the shard
while B was still delivering.

The gRPC API stays as it is: `ReleaseShard.worker` is already required, and every
client sends the worker that picked the shard.

## Plan

- `DeliveryShardRegistry.releaseShard(ShardIndex, WorkerId)`: write the cleared
  record only if the stored worker equals the given one, and return
  a `ReleaseOutcome`: `RELEASED`, `NOT_PICKED_UP`, or `HELD_BY_ANOTHER_WORKER`.
- Do not write when the record has no worker: nothing to release. A release whose
  reply was lost recognizes its own write by the write ID, as a pick does, and
  reports `RELEASED`.
- `DeliveryShardSession` keeps its worker and releases with it.
- `ShardService.releaseSession` passes `request.getWorker()`, and does all
  the logging: a warning without the holder's name for `HELD_BY_ANOTHER_WORKER`,
  and a `DEBUG` line with the outcome of every release.
- Declare `<T extends @Nullable Object>` on `update` and `Decision`, which carry
  a `null` result under `@NullMarked`. Move each decision into a documented private
  method (`decidePick`, `decideRelease`, `decideExpiry`), whose return type gives
  IDEA the nullable result that it cannot infer from a lambda.

## Tests

- `DeliveryShardRegistrySpec`: a release by another worker leaves the session
  intact; a stale holder's release after another worker picked the shard leaves
  the new session intact; a release of a released shard writes nothing.
- `ShardServiceTest`: a `ReleaseShard` from another worker keeps the shard taken.
