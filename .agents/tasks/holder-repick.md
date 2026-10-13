# Let the holder pick its shard again, and narrow the window for late requests

Issue: [#74](https://github.com/SpineEventEngine/delivery/issues/74), part of #71.

## Goal

A pick-up by the worker that already holds the shard succeeds and renews the session
(`when_last_picked`). A pick-up by another worker is still rejected while its session is not
stale.

Two rules narrow the window in which a late request can affect the newer requests of the same
worker:
- a release ends only the session it found;
- the server doesn't serve a pick-up or release whose call is cancelled before it is served.

## Rationale

When a pick-up reply is lost, a release fails, or an app restarts with a stable (per-node)
worker ID, the worker asks for the shard again. Today its own session rejects it, so the
shard waits until the session expires, or forever when no timeout is set.

Worker IDs come from the client and are often per node. The server sees only
`(shard, worker)`, so it can't tell a retry or a restart from anything else. Choosing worker
IDs fine-grained enough for the app's concurrency is the client's responsibility.

Renewing the holder's session opens one hazard, a late release. Worker W releases the shard,
but the release waits on a stalled server instance, and W's call fails. W picks the shard
again on another instance and starts a new round. The old release then runs, finds "W holds
the shard", and clears W's new session, so another worker can pick the shard while W
delivers. Two rules close this:
- Rule 1: a release remembers the write ID of the record it read first. If, after a conflict,
  it finds the same worker under another write ID, that is a newer session, and the release
  leaves it intact (`RENEWED`).
- Rule 2: `ShardService` checks `Context.current().isCancelled()` before it touches the
  registry. grpc-java 1.83.1 cancels a call's `Context` right away when the client is gone
  (`ServerImpl.closedInternal` uses a non-serializing executor), so a handler that waited in the
  queue sees it. Ending a cancelled unary call with `onError` doesn't throw.

The gRPC API stays as it is: Spine 1.x apps use this server too. Nothing changes in
`core-jvm`, `gcloud-jvm`, or the client.

## Plan

- `DeliveryShardRegistry.decidePick`: after the own-write check, if the record's worker is the
  requesting one, write the new record. This renews the session. It comes before the stale
  check, so a holder renewing its own stale session gets no stale warning. A `Decision` carries
  a follow-up that `update` runs only once the decision is carried out, so the renewal
  (`DEBUG`) is logged once, after the write is applied. A stale takeover (`WARNING`) is logged
  when the pick decides on it.
  Update the `pickUp` Javadoc.
- `DeliveryShardRegistry.releaseShard`: read the record once, and pass the write ID it saw to
  `decideRelease`. The decision order is:
  1. no record → `NOT_PICKED_UP`;
  2. own write → `RELEASED`;
  3. no worker → `NOT_PICKED_UP`;
  4. another worker → `HELD_BY_ANOTHER_WORKER`;
  5. the same worker under another write ID → `RENEWED`, without a write;
  6. otherwise → write the cleared record, `RELEASED`.
- Add `ReleaseOutcome.RENEWED`.
- Pull `Decision`, `DeliveryShardSession`, and `ReleaseOutcome` up from `DeliveryShardRegistry`
  to the package level. `DeliveryShardSession` gets the registry through its constructor.
- `ShardService.pickShard` and `ShardService.releaseSession`: if the call is cancelled, log
  a warning and end the call with `CANCELLED`, without touching the registry. `releaseSession`
  logs `RENEWED` with a warning, as it does `HELD_BY_ANOTHER_WORKER`.
- `server/README.md`: describe the per-worker exclusivity, renewal, and the two rules.
- Bump the version.

## Tests

- `DeliveryShardRegistrySpec`:
  - the holder picks the shard again, and the session is renewed;
  - the holder's retried pick-up succeeds on another server;
  - another worker is rejected;
  - a release doesn't end a session that the same worker renewed while the release was in
    progress (`RENEWED`).
- `ShardServiceTest`: the same `PickUpShard` sent twice is picked up both times, and
  another worker is rejected.
- `ShardServiceSpec` (new):
  - a pick-up for a cancelled call is not served;
  - a release for a cancelled call is not served, and the shard stays taken;
  - a release keeps the session that the worker renews while the release is in
    progress.
- `DeliveryClientTest`, `ConsistencyTest`: another worker is rejected; in
  `ConsistencyTest`, the holder picks the shard again on another node.
