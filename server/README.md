Delivery Server
---------------

This module holds the server component of Delivery Server that is written as a plain gRPC
application.

The server is a runnable application that exposes gRPC APIs for working with the delivery.

# Exposed ports

By default, the server is running a gRPC server that is available at port `8484`.

The port may be additionally configured by setting the `PORT` environment variable.

# Storage mode

The server supports 3 storage modes: in-memory, Redis-based, and Hazelcast-based

The in-memory storage provides the best-possible performance and is used by default.

The Redis storage adds an ability to store the server info in a separate more durable storage. In
order to use Redis storage one must set the `USE_REDIS` environment variable to any value (we check
only the presence of the variable and ignore its value) and also configure the `REDIS_HOST`
environment variable. The latter allows configuring the host where the Redis server should be
accessible by the application. It is also possible to configure the `REDIS_PORT`
environment variable that denotes the port on which Redis is accessible. The port defaults to
`6379`. If the `USE_REDIS` variable is set, but the `REDIS_HOST` is not configured, the server will
stay in in-memory mode.

The Hazelcast-based storage adds an ability to start several Delivery Server instances in a
replicated cluster where all the Delivery Server instances form a single memory space and
automatically accessing data of each other. This means that changes made on one Delivery Server
instance will be available for all other instances in the cluster. **Pay attention that this mode is
experimental, and there is no strong consistency guaranty in the cluster for now.** In order to use
Hazelcast storage one must set the `USE_HAZELCAST` environment variable to any value (we check only
the presence of the variable and ignore its value).

Hazelcast starts an embedded server and automatically discovers other Delivery Server instances in
Hazelcast mode in the same network, so no additional effort is required.

# Inbound message size

By default, the maximum message size allowed to be received by the server is `4 MiB`.

This can be configured by setting a custom value (in bytes) to the `MAX_INBOUND_MESSAGE_SIZE`
environment variable. Allowed values are in bounds from `1` to `Integer.MAX_VALUE` inclusive.

# Stale shards auto release

`DeliveryShardRegistry` accepts `processingTimeout` upon which the registry can decide if a session
is stale. The check is performed when a session is asked for picking up. If a gap between
`session.whenLastPickedUp()` and `now()` is equal to or more than processingTimeout, the session is
considered stale and can be picked up again. The fact of a session
"auto-release" is logged to `WARNING` level.

The processing timeout is read from the `SHARD_PROCESSING_TIMEOUT` env variable. The number of
seconds is expected there. By default, it is 0, which means that the stale-check is not performed at
all.
