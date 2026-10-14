# Bind test gRPC servers to the loopback address the clients dial

## Goal

Tests that start a gRPC server bind it to `127.0.0.1`, the address their client dials,
instead of the wildcard address. This removes a flake seen on macOS, in which a test RPC
failed with the following error (abridged). In production, the server keeps listening on
all interfaces.

```text
INTERNAL: http2 exception
Http2Exception: First received frame was not SETTINGS. Hex dump for first 5 bytes: 485454502f
```

The bytes `485454502f` spell `HTTP/`: the client reached an HTTP/1 server.

## Rationale

On macOS, the JDK enables `SO_REUSEADDR` on server sockets. With it, a wildcard listener
on port `P` and another process's listener on `127.0.0.1:P` coexist, and a connection to
`127.0.0.1:P` goes to the more specific one. The port allocator can therefore give
a wildcard `:0` bind a port on which a loopback-only service (an IDE, an MCP server)
already listens. The server starts fine, and the client then reaches the other service.
A server bound to `127.0.0.1` owns that address and port exclusively. Linux refuses
the overlapping bind.

`grpc-java` offers no transport-neutral builder for an address, so `DeliveryServerApp`
uses `NettyServerBuilder.forAddress()` of `grpc-netty-shaded`, the transport that
production already runs on.

## Plan

- `DeliveryServerApp` listens on an `InetSocketAddress`: all interfaces at `PORT`
  in production, `HOST` with an ephemeral port in tests.
- `server` depends on `grpc-netty-shaded` at compile time.
- `WithApp`, `DeliveryServerStartupSpec`, and `admin-server`'s `AdminControllerSpec`
  and `ShardInfoSecurityTest` bind the loopback address.
- `ShardInfoSecurityTest` starts its stand-in for the Delivery server once per class,
  as a bean of the application context, at an ephemeral port, and replaces the
  `AdminServiceBlockingStub` bean with one connected to it. It used to bind the fixed
  port `8484` before each test. On Windows, the JDK binds server sockets exclusively,
  so a connection of the previous server lingering in `TIME_WAIT` made the next bind
  fail with `Address already in use`.

## Tests

- `DeliveryServerStartupSpec` occupies the exact address that the app binds.
- A temporary reproduction, not kept: with an HTTP/1 server on `127.0.0.1:P`,
  the app bound to the wildcard address started on `P`, and its RPCs failed with
  the error quoted in the Goal. Bound to `127.0.0.1`, the app fails to start on `P`
  while the HTTP/1 server holds it, and an HTTP/1 server started after the app
  cannot bind the app's port.
