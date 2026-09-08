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
experimental, and there is no strong consistency guarantee in the cluster for now.** In order to use
Hazelcast storage one must set the `USE_HAZELCAST` environment variable to any value (we check only
the presence of the variable and ignore its value).

# Cluster discovery

Delivery servers running in Hazelcast mode find each other by IP multicast under the cluster
name `delivery`. This is configured by the `hazelcast.yaml` resource shipped with the server, and
it works wherever the network carries multicast: a LAN, a Docker or Compose network, or virtual
machines on one private network. Managed serverless platforms such as Cloud Run do not carry
multicast between instances, so instances there stay standalone.

Deployments sharing a network, for example staging and production, should use distinct cluster
names by setting the `HZ_CLUSTERNAME` environment variable.

Hazelcast's cloud auto-detection is switched off on purpose. When it is on, a member running on
an Azure, GCP, AWS, or Kubernetes host selects that platform's discovery, which needs credentials
the server does not have, and starts standalone instead of falling back to multicast.
GitHub-hosted CI runners are Azure virtual machines, which is where this first showed up.

## Cloud discovery

Where multicast is unavailable, the members can discover each other through the cloud provider's
API instead. Implementing this requires:

1. Multicast disabled and exactly one provider join enabled, since Hazelcast refuses to start
   with two discovery mechanisms enabled: `HZ_NETWORK_JOIN_MULTICAST_ENABLED=false` together
   with one of `HZ_NETWORK_JOIN_AZURE_ENABLED=true`, `HZ_NETWORK_JOIN_GCP_ENABLED=true`,
   `HZ_NETWORK_JOIN_AWS_ENABLED=true`, or `HZ_NETWORK_JOIN_KUBERNETES_ENABLED=true`.
2. An identity for the instances that may list the other instances: a managed identity on
   Azure, a service account on GCP, an instance or task role on AWS, or a service account with
   RBAC access to endpoints and pods on Kubernetes.
3. Network rules that allow TCP traffic on port `5701` between the instances.
4. Provider-specific settings, such as a resource group, zones, labels, or a headless service
   name on Kubernetes. The `HZ_*` variables suit single-value switches like the ones above.
   For anything richer, such as a member list or provider-specific keys, use a complete
   Hazelcast configuration file and point the server at it with
   `JAVA_TOOL_OPTIONS=-Dhazelcast.config=/path/to/hazelcast.yaml`. The Jib-built image has a
   fixed entry point, so `JAVA_TOOL_OPTIONS` is the way to pass JVM options to it.

The provider-specific keys are described in the [Hazelcast discovery documentation][hz-discovery].

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

[hz-discovery]: https://docs.hazelcast.com/hazelcast/latest/clusters/discovery-mechanisms
