delivery-server
--------------

This repository provides a reusable gRPC-based Inbox delivery service and configuration client and a
complimentary application for Spine-based apps.

What is this project about?
--------------

Delivery Server is an application that is deployed on a separate from the main
application server, and serves as a remote `ShardedWorkRegistry` and `Inbox` storage.
Delivery Server communicates with the main application nodes through the gRPC protocol.

This is true for both [`simple-server`](simple-server) and [`server`](server) with the only
difference in how data is stored and processed underneath.

For more detailed information about each of the server's implementation please refer to the
[simple-server readme][simple-server-readme] or [server readme][server-readme] files.

[simple-server-readme]: simple-server/README.md
[server-readme]: server/README.md

Applications
--------------

This repository contains several runnable applications:

[server](server) — Spine-based gRPC Delivery Server.

[simple-server](simple-server) — Simple gRPC Delivery Server that doesn't use Spine inside. Created
for improving performance.

[admin-server](admin-server) — gRPC client that works in pair with listed above Delivery Server
instances. It connects to the Delivery Server and provides information about shard status over HTTP
for maintenance and administration purposes.

[deployment / server-cloud-run](deployment/server-cloud-run) — This is the application created 
for starting the [server](server) and [admin-server](admin-server) together inside a docker 
container. The [Launcher][server-launcher] is responsible for starting the Delivery Server and Admin
Server if configured.

[deployment / simple-server-cloud-run](deployment/simple-server-cloud-run) — Created for the same
purposes as [deployment / server-cloud-run](deployment/server-cloud-run) but starts 
[simple-server](simple-server) instead, that's why it uses another 
[Launcher][simple-server-launcher].


[server-launcher]: deployment/server-cloud-run/src/main/java/io/spine/delivery/launcher/Launcher.java
[simple-server-launcher]: deployment/simple-server-cloud-run/src/main/java/io/spine/delivery/launcher/Launcher.java

Compatibility
--------------
For the `Spine` versions below `1.8.0` use `delivery-server:0.7.2`.

For `Spine` `1.8.0` and above use `delivery-server:0.8.0` and above.

For `Spine` `1.9.0` and above use `delivery-server:0.9.0` and above.

Distribution
-------------

Both [`simple-server`](simple-server) and [`server`](server) are distributed as a Docker containers
that are hosted on the Google Container Registry. There is a Terraform module 
[spine-delivery-server][terraform-module] that automates the deployment of the containers to the
GCE instance. All the configuration parameters that are available for the servers also may be set
through the Terraform module configuration.

[terraform-module]: https://registry.terraform.io/modules/SpineEventEngine/spine-delivery-server/google/latest
