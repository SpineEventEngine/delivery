# Drop `ShardProcessingSession` of `core-jvm` from the server

## Goal

Stop extending `io.spine.server.delivery.ShardProcessingSession` of `core-jvm`, so that
the next `CoreJvm` bump, which removes the class, does not break the build.

## Rationale

`core-jvm` no longer uses the class. Its only users are `DeliveryShardSession`, the only
subclass, and `DeliveryShardRegistry.pickUp(ShardIndex, WorkerId)`, which returns it.
With a single implementation, the abstraction has nothing to abstract over.

## Steps

1. Merge the code of `ShardProcessingSession` into `DeliveryShardSession`, which no longer
   extends anything. `complete()` becomes package-private, which is the access it already had.
2. Make `DeliveryShardRegistry.pickUp(ShardIndex, WorkerId)` return `DeliveryShardSession`,
   and drop the downcasts in `DeliveryShardRegistrySpec`.
3. Rename the display name of `WorkRegistryTest.PickUp.session()`, which checks
   a `ShardSessionRecord`.
4. Bump the version to `0.22.2`.
5. Verify with `./gradlew build dokkaGenerate`.
