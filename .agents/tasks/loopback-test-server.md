# Bind test gRPC servers to the loopback address the clients dial

## Goal

Tests that start a gRPC server bind it to `127.0.0.1`, the address their client dials,
instead of all interfaces (the wildcard address). In production, the server keeps
listening on all interfaces.

This removes a flake seen on macOS, in which a test RPC failed with the following
error (abridged):

```text
INTERNAL: http2 exception
Http2Exception: First received frame was not SETTINGS. Hex dump for first 5 bytes: 485454502f
```

The bytes `485454502f` spell `HTTP/`: the client reached an HTTP/1 server.

It also stops `ShardInfoSecurityTest` from failing on Windows with
`Address already in use`.

## Rationale

On macOS, the JDK enables `SO_REUSEADDR` on server sockets. With it, a listener on all
interfaces at port `P` and another process's listener on `127.0.0.1:P` coexist, and
a connection to `127.0.0.1:P` goes to the more specific one. The port allocator can
therefore give a server that binds port zero on all interfaces a port on which
a loopback-only service (an IDE, an MCP server) already listens. The server starts fine,
and the client then reaches the other service. A server bound to `127.0.0.1` owns that
address and port exclusively. Linux refuses the overlapping bind.

On Windows, the JDK binds server sockets exclusively. After a listening socket that
accepted a connection closes, another bind to its port fails until that connection,
lingering in `TIME_WAIT`, becomes inactive. `ShardInfoSecurityTest` bound the fixed
port `8484` before each test, so the next bind failed with `Address already in use`.

`grpc-java` offers no transport-neutral builder for an address, so `DeliveryServerApp`
uses `NettyServerBuilder.forAddress()` of `grpc-netty-shaded`, the transport that
production already runs on.

## Plan

- `DeliveryServerApp` listens on an `InetSocketAddress`: all interfaces at `PORT`
  in production, `HOST` with an ephemeral port in tests.
- `server` depends on `grpc-netty-shaded` at compile time.
- `WithApp`, `DeliveryServerStartupSpec`, and `admin-server`'s `AdminControllerSpec`
  and `ShardInfoSecurityTest` bind the loopback address.
- `ShardInfoSecurityTest` starts its stand-in for the Delivery server once per class, at
  an ephemeral port, as a bean of the application context. It replaces the
  `AdminServiceBlockingStub` bean with a stub that calls the stand-in.

## Tests

- `DeliveryServerStartupSpec` occupies the exact address that the app binds.
- A temporary reproduction, not kept: with an HTTP/1 server on `127.0.0.1:P`, the app
  bound to all interfaces started on `P`, and the client's RPCs failed with the error
  quoted in the Goal. Bound to `127.0.0.1`, the app failed to start on `P` while
  the HTTP/1 server held it, and an HTTP/1 server started after the app could not bind
  the app's port.

## Verification

```bash
./gradlew :delivery-server:test :admin-server:test
./gradlew build dokkaGenerate
```
