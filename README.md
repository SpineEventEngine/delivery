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

[server](server) — the gRPC Delivery Server that doesn't use Spine inside.

[admin-server](admin-server) — gRPC client that works in pair with listed above Delivery Server
instances. It connects to the Delivery Server and provides information about shard status over HTTP
for maintenance and administration purposes.

[deployment / simple-server-cloud-run](deployment/simple-server-cloud-run) — the application that
starts the [server](server) inside a Docker container. The
[Launcher][server-launcher] is responsible for starting the Delivery Server.


[server-launcher]: deployment/simple-server-cloud-run/src/main/java/io/spine/delivery/launcher/Launcher.java

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
