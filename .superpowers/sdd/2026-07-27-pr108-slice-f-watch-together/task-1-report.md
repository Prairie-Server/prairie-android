# Slice F Task 1 report

## Status

Implemented realtime transport semantics and credential boundaries only:

- explicit decoded `room_closed` remains the terminal
  `RoomRealtimeEvent.Closed` protocol event;
- writable handshakes publish `RoomRealtimeEvent.Opened`;
- physical EOF, handshake failure, and socket I/O failure publish typed
  `RoomRealtimeEvent.TransportTerminated`;
- connection-owner cancellation is rethrown without publishing either close
  event;
- all client and repository send methods return actual Boolean delivery;
- socket session assignment and cleanup are guarded by a mutex and connection
  identity, so an old finalizer cannot clear its replacement;
- the websocket URL encodes room id as one path segment, removes the access
  JWT, and retains only the server-required room/profile query fields;
- `ws://` and `wss://` canonicalize to their corresponding HTTP origins for
  same-origin and cleartext-consent checks.

No repository reconnect/lease redesign, `RoomSession`, controller, or UI work
was implemented.

## Design

### Testable physical socket boundary

`DefaultWatchTogetherRealtimeClient` uses a small common socket connector seam.
The production connector is a real Ktor `webSocketSession`; common tests use
controlled connections to deterministically exercise EOF, receive/open
failure, cancellation, send failure, concurrent replacement, and cleanup.

The active physical connection is assigned before `Opened`. Both assignment
and send lookup use one mutex. Finalization clears the mutable send target only
when it is still the exact connection being finalized.

### Protocol versus transport termination

`decodeRoomFrame` alone creates `Closed`, and only from a `room_closed` frame.
The socket loop creates `TransportTerminated` for normal EOF and failures.
Cancellation is handled before the general failure branch and rethrown, so an
owner-driven cancellation remains silent.

### Credential boundary

The access token is still required to attempt the room socket but is no longer
placed in the request target. The existing same-origin Silo auth plugin adds
`Authorization`, `X-Profile-Id`, and `X-Profile-Token` headers. The server
currently still requires `room_token`, `profile_id`, and `profile_token` query
parameters; the client KDoc records that residual request-target exposure.

Cleartext consent canonicalizes `ws://host[:port]` to
`http://host[:port]` and `wss://` to `https://`. It passes the canonical HTTP
origin to the consent store, allowing an existing exact-origin approval to
authorize the corresponding websocket. Unapproved cleartext fails in the auth
plugin before the OkHttp engine observes any header or query credential.

## Tests

Common:

- `RoomFrameDecoderTest`
- `WatchTogetherRealtimeClientTest`
- `HttpOriginPolicyTest`
- `SiloAuthPluginPinTest`
- `WatchTogetherRepositoryTest`

Android/JVM real transport:

- `WatchTogetherRealtimeWebSocketTest`

The real test uses the production Ktor/OkHttp client against
`MockWebServer`. It verifies the encoded handshake path, JWT absence from the
query, retained room/profile query contract, auth/profile headers,
open-before-early-snapshot ordering, outbound attach/ping ordering, normal
physical EOF, owner cancellation, and pre-engine cleartext rejection.

## TDD evidence

### RED

Command:

```text
./gradlew :shared:testDebugUnitTest \
  --tests 'org.siloserver.silo.network.WatchTogetherRealtimeClientTest' \
  --tests 'org.siloserver.silo.network.HttpOriginPolicyTest' \
  --tests 'org.siloserver.silo.network.SiloAuthPluginPinTest'
```

Observed compile RED:

```text
Unresolved reference 'Opened'
Unresolved reference 'TransportTerminated'
Unit send results were incompatible with Boolean delivery assertions
WatchTogetherSocketConnector / WatchTogetherSocketConnection were unresolved
No socket-seam constructor existed
BUILD FAILED in 1s
```

This established the missing physical/protocol distinction, writable epoch,
Boolean send contract, and testable connection-ownership boundary before
production changes.

The first real-websocket run then passed every app assertion except
MockWebServer shutdown in the cancellation test. Its server listener observed
the client close but did not answer the close handshake, leaving the dispatcher
queue alive. Adding the correct server-side close response fixed the fixture;
no production behavior changed for that failure.

### GREEN

Fresh focused command:

```text
./gradlew \
  :shared:testDebugUnitTest \
    --tests 'org.siloserver.silo.network.RoomFrameDecoderTest' \
    --tests 'org.siloserver.silo.network.WatchTogetherRealtimeClientTest' \
    --tests 'org.siloserver.silo.network.HttpOriginPolicyTest' \
    --tests 'org.siloserver.silo.network.SiloAuthPluginPinTest' \
    --tests 'org.siloserver.silo.repository.WatchTogetherRepositoryTest' \
  :android-shared:testDebugUnitTest \
    --tests 'org.siloserver.silo.common.network.WatchTogetherRealtimeWebSocketTest' \
  --rerun-tasks
```

Observed:

```text
RoomFrameDecoderTest: 10 tests, 0 failures/errors
WatchTogetherRealtimeClientTest: 7 tests, 0 failures/errors
HttpOriginPolicyTest: 13 tests, 0 failures/errors
SiloAuthPluginPinTest: 20 tests, 0 failures/errors
WatchTogetherRepositoryTest: 11 tests, 0 failures/errors
WatchTogetherRealtimeWebSocketTest: 3 tests, 0 failures/errors
BUILD SUCCESSFUL in 16s
70 actionable tasks: 70 executed
```

Total: 64 tests, 0 failures, 0 errors.

## Concerns

- Room/profile credentials remain in the query because the server contract
  still requires them. The access JWT is removed, and the residual exposure is
  documented.
- `WatchTogetherRepository` only receives the new open/termination event and
  Boolean send surface in this task. Attempt counting, stable-window reset,
  immutable leases, and reconnect ownership are intentionally deferred to
  Slice F Task 2.
- Existing Kotlin/Gradle deprecation and coroutine opt-in warnings remain.
- No push, PR, or merge was performed.

## Review correction: coherent websocket auth scope

The websocket handshake now captures `snapshotCurrentScope()` exactly once at
connect start. It validates the access token against that exact snapshot,
derives `profile_id` and `profile_token` from the same snapshot, and passes the
snapshot through the connector boundary. The production Ktor connector applies
`authScope(scope)` to the websocket request, so URL rebasing, bearer lookup, and
profile headers remain pinned even if the globally-active server, profile, or
temporary identity changes before the engine builds the handshake. A missing
snapshot or missing exact-scope access token terminates before the connector
opens.

### Correction TDD evidence

RED command:

```text
./gradlew :android-shared:testDebugUnitTest \
  --tests 'org.siloserver.silo.common.network.WatchTogetherRealtimeWebSocketTest.handshake remains entirely on captured auth scope when active identity switches'
```

Before the correction, the deterministic fixture switched the active identity
from server A to server B immediately before engine/auth-plugin processing.
The test failed because the request did not reach captured server A:

```text
1 test completed, 1 failed
BUILD FAILED in 2s
```

After the correction, that real-WebSocket test proves the request reaches only
server A and carries A's room/profile query fields, bearer, and profile headers;
server B receives no request. A common seam test additionally proves that an
unavailable exact-scope access token fails closed before connector invocation.

Fresh focused GREEN used the same command listed above with all six test
classes and `--rerun-tasks`. Observed:

```text
RoomFrameDecoderTest: 10 tests, 0 failures/errors
WatchTogetherRealtimeClientTest: 8 tests, 0 failures/errors
HttpOriginPolicyTest: 13 tests, 0 failures/errors
SiloAuthPluginPinTest: 20 tests, 0 failures/errors
WatchTogetherRepositoryTest: 11 tests, 0 failures/errors
WatchTogetherRealtimeWebSocketTest: 4 tests, 0 failures/errors
BUILD SUCCESSFUL in 17s
70 actionable tasks: 70 executed
```

Total after correction: 66 tests, 0 failures, 0 errors. Existing off-origin and
cleartext-consent gates, including the real pre-engine rejection test, remain
covered and green. No push, PR, or merge was performed.
