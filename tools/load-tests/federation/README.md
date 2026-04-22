# Federation Load Test Tool

This module provisions mirrored users on both federation nodes over HTTP, opens XMPP clients with Smack, sends one direct message per pair in both directions, and writes a JSON summary with counts, latencies, and failures.

## Default Topology
- node A HTTP: `http://localhost:8081`
- node A XMPP: `127.0.0.1:5223` with domain `node-a.local`
- node B HTTP: `http://localhost:8082`
- node B XMPP: `127.0.0.1:5224` with domain `node-b.local`

## Example Run

```bash
./gradlew :tools:load-tests:federation:run --args='--client-count=50 --run-id=sample --output=docs/evidence/sample-load.json'
```

When you invoke the documented Gradle task from the repository root, relative `--output` paths are resolved from the repository root as well.

## Useful Overrides
- `--run-id=<value>`
- `--client-count=<value>`
- `--username-prefix=<value>`
- `--password=<value>`
- `--delivery-timeout-seconds=<value>`
- `--output=<path>`
- `--a-http=<url>`
- `--a-xmpp-host=<host>`
- `--a-xmpp-port=<port>`
- `--a-domain=<domain>`
- `--b-http=<url>`
- `--b-xmpp-host=<host>`
- `--b-xmpp-port=<port>`
- `--b-domain=<domain>`

## Current Federation Assumption

The tool provisions mirrored usernames on both nodes for every pair because the current Milestone 7 federation mapping reuses local direct-message eligibility rules on both sides.

## Current Teardown Limit

The harness sends the XMPP stream-close marker and then force-closes the client socket so large runs do not spend tens of seconds waiting for server-side close-handshake timeouts. Federation delivery and peer traffic counters remain valid, but some recent XMPP client sessions can still land as `ERROR` instead of `DISCONNECTED` in the admin dashboard because the current in-process adapter does not complete a clean close handshake fast enough for the load harness path.
