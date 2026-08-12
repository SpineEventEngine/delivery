delivery-server
--------------

This repository provides a reusable gRPC-based Inbox delivery service and configuration client and a
complementary application for Spine-based apps.

What is this project about?
--------------

Delivery Server is an application that is deployed on a separate from the main
application server, and serves as a remote `ShardedWorkRegistry` and `Inbox` storage.
Delivery Server communicates with the main application nodes through the gRPC protocol.

This functionality is provided by the [`server`](server) application.

For more detailed information about the server's implementation please refer to the
[server readme][server-readme] file.

[server-readme]: server/README.md

Applications
--------------

This repository contains several runnable applications:

 1. [server](server) — the Delivery Server application itself. It serves the shard
registry and the `Inbox` storage to the nodes of a Spine-based application.
Implemented as a plain gRPC server — without Spine inside — with in-memory, Redis,
or Hazelcast storage. See the [module documentation][server-readme] for the exposed
ports, storage modes, and other configuration options.

 2. [admin-server](admin-server) — the maintenance and administration tool for running
Delivery Server instances, showing the status of their shards. It obtains the status
information by connecting to a Delivery Server as a gRPC client, and serves it over HTTP.

 3. [deployment / cloud-run](deployment/cloud-run) — the application that
starts the [server](server) inside a Docker container. The
[Launcher][server-launcher] is responsible for starting the Delivery Server.


[server-launcher]: deployment/cloud-run/src/main/java/io/spine/delivery/launcher/Launcher.java

Compatibility
--------------
For the `Spine` versions below `1.8.0` use `delivery-server:0.7.2`.

For `Spine` `1.8.0` and above use `delivery-server:0.8.0` and above.

For `Spine` `1.9.0` and above use `delivery-server:0.9.0` and above.

Distribution
-------------

The [`server`](server) is distributed as a Docker container
hosted on the Google Container Registry. There is a Terraform module 
[spine-delivery-server][terraform-module] that automates the deployment of the containers to the
GCE instance. All the configuration parameters that are available for the servers also may be set
through the Terraform module configuration.

[terraform-module]: https://registry.terraform.io/modules/SpineEventEngine/spine-delivery-server/google/latest
