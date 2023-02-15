message-delivery
--------------

This repository provides a reusable gRPC-based Inbox delivery service and configuration client and a
complimentary application for Spine-based apps.


Applications
--------------

This repository contains several runnable applications:

[server](server) — Spine-based gRPC Liquor server.

[simple-server](simple-server) — Simple gRPC Liquor server that doesn't use Spine inside. Created
for improving performance.

[admin-server](admin-server) — gRPC client that works in pair with listed above Liquor
servers. It connects to the Liquor server and provides information about shard status over HTTP for 
maintenance and administration purposes.

[deployment / server-cloud-run](deployment/server-cloud-run) — This is the application created 
for starting the [server](server) and [admin-server](admin-server) together inside a docker 
container. The [Launcher][server-launcher] is responsible for starting Liquor and Admin Server if 
configured.

[deployment / simple-server-cloud-run](deployment/simple-server-cloud-run) — Created for the same
purposes as [deployment / server-cloud-run](deployment/server-cloud-run) but starts 
[simple-server](simple-server) instead, that's why it uses another 
[Launcher][simple-server-launcher].


[server-launcher]: deployment/server-cloud-run/src/main/java/io/spine/message/delivery/launcher/Launcher.java
[simple-server-launcher]: deployment/simple-server-cloud-run/src/main/java/io/spine/message/delivery/launcher/Launcher.java

Compatibility
--------------
For the `Spine` versions below `1.8.0` use `message-delivery:0.7.2`.

For `Spine` `1.8.0` and above use `message-delivery:0.8.0` and above.
