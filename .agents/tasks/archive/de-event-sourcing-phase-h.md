# De-event-sourcing Phase H: `delivery-server` rollout

Status: in progress.
Delete this file when the branch merges to `master`.

Parent plan: `core-jvm/.agents/tasks/de-event-sourcing-plan.md`, Phase H,
order 3. Reference vendor migrations: `gcloud-jvm` PR #204 (Datastore kinds),
`jdbc-storage` PR #181 (table-name conventions).

## Scope actually found in this repo

The Phase H table over-scoped this repo; verified state:

- **No `@Apply` in the composed build.** The single occurrence —
  `client/demo/.../GreatGreeter.java:58` — lives in `client/`, which is
  excluded from the Gradle build (Spine 1.x, built standalone; see
  `settings.gradle.kts:16-19`). The parent plan's "`SessionRegistry`
  fixture" does not exist as an aggregate class: `ShardSessionRegistry` is a
  proto-only `AGGREGATE`-kind message served by a hand-written gRPC service
  over a `RecordStorage`.
- **No `createAggregateStorage`/`AggregateStorageTest` usage** anywhere.
- The real vendor obligation is the new `StorageFactory` SPI: one abstract
  method `createRecordStorage(ContextSpec, RecordSpec, @Nullable StorageGroup)`,
  where the `StorageGroup` must be folded into the physical storage name —
  otherwise an entity's latest state and its state history (both
  `EntityRecord`) conflate.

## Changes on this branch

1. `HazelcastRecordStorage` map naming: ungrouped by
   `RecordSpec.sourceType()` (was `recordType()` — a latent conflation of
   entity types sharing the ID type); grouped by
   `<group name>-<record type simple name>`. The `'-'` separator may not
   occur in a Protobuf type name, keeping grouped names disjoint.
2. `FlatTenantStorage` (Redis) key naming: same grouped convention appended
   to the existing `tenant-idType-sourceType` scheme.
3. `simple-server` factories migrated to the 3-arg SPI:
   `SingletonStorageFactory` (group in the cache `Key`; cache is now a
   `ConcurrentHashMap` — storages are created lazily on delivery worker
   threads), `ReportingStorageFactory` (group in `TypeSpec`;
   `subscribe(idType, recordType, …)` deliberately matches **ungrouped**
   storages only).
4. `ShardRegistryStorage` and `ReportingRecordStorage` re-based onto
   `DelegatingRecordStorage` — `core-jvm` renamed the former
   `RecordStorageDelegate` base (drop-in: same constructor and API).
5. Local `AbstractStorageTest` (storage/base) renamed to
   `RecordStorageContractTest` — it collided (same FQCN) with the fixture
   published by `core-jvm` `server` test fixtures.
6. New specs: `HazelcastGroupedStorageSpec`, `RedisGroupedStorageSpec`;
   shared fixtures `ProjectEntity`/`ProjectLogEntity` and the `ProjectLog`
   test proto message in `storage/base`.

## Verification gate

- `./gradlew clean build` (test proto changed) — green.
- Grep gate: `@Apply` — zero hits outside `client/` (out of build).
- `pre-pr` checklist.

## Follow-ups (not this branch)

- Consider adopting the published `DelegatingRecordStorageTest` for both
  vendors (deep query coverage), as `gcloud-jvm` did.
- `RedisStorageFactory.close()` is a NOP — the Redisson client is never shut
  down by the factory.
- Update the Phase H table in `core-jvm/.agents/tasks/de-event-sourcing-plan.md`
  when this merges (order 3 complete; correct the fixture claim).
