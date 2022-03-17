Simple Message Delivery Server
---------------

This module holds the Server of the Message Delivery which is written as plain gRPC application.

The server is a runnable application which exposes gRPC APIs for working with the delivery.

# Exposed ports

By default, the server is running a gRPC server which is available at port `8484`.

The port may be additionally configured by setting the `PORT` environment variable. 

# Storage mode

The server support two storage modes: in-memory and Redis-based.

The in-memory storage provides best-possible performance and is used by default.

The Redis storage adds ability to store the server info in a separate more durable storage.
In order to use Redis storage one must set `USE_REDIS` environment variable to any value (we check 
only presence of the variable and ignore its value) and also configure `REDIS_HOST` 
environment variable. The latter allows configuring the host where the Redis server should 
be accessible by the application. It is also possible to configure `REDIS_PORT` 
environment variable which denotes the port on which Redis is accessible. The port defaults 
to `6379`. If the `USE_REDIS` variable is set, but the `REDIS_HOST` is not configured the server 
will stay in in-memory mode.

# Inbound message size

By default, the maximum message size allowed to be received by the server is `4 MiB`.

This value may be additionally configured by setting the `MAX_INBOUND_MESSAGE_SIZE`
environment variable. Allowed values are in bounds from `1` to `Integer.MAX_VALUE` inclusive.
