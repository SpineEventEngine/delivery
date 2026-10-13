# Test the inbox stores under concurrent changes of several shards

Issue: [#75](https://github.com/SpineEventEngine/delivery/issues/75), part of #71.

## Goal

Close #75. Its fix landed in PR #78: `InMemoryInboxStore` keeps one `ShardInbox` per shard,
locked on its own, with the messages ordered by `(when_received, version, uuid)` and a separate
ordered map of the `TO_DELIVER` ones. A page is a range read, and the newest message to deliver
is the last entry of that map.

`InboxStoreContract` already covers the order, the strict `since`, the page limit, the newest
`TO_DELIVER` message, and rewrites that change the status or `when_received`. What #75 also
asks for, and nothing tests yet, is concurrent writes and deletes across shards.

## Plan

- Add a nested group to `InboxStoreContract`, so that all three backends run it:
  - several threads write, rewrite, and delete their own messages at once, in batches that
    span two shards, so that two threads also share each shard; then every shard holds exactly
    the expected messages, in order, with the expected newest message to deliver and counts;
  - the same, while other threads read pages and the newest message to deliver; each page
    must be in order, within the page size and the `since` bound, and each newest message must
    be in the `TO_DELIVER` status.
- Seed the randomness, so that a failure repeats with the same operations.
- No change to the production code.

## Verification

- `./gradlew :storage:base:test :storage:hazelcast:test :storage:redis:test`.
- Drop the per-shard locks from `InMemoryInboxStore` locally, and see the new tests fail.
