# Missing k6 Features Observed in `examples/origin-k6`

Gaps are based on Lyocell docs (`README.md`, `K6_REFERENCE.md`, `TECHNICAL_DESIGN.md`) versus the k6 features exercised by the upstream scripts.

- HTTP options & response fields [DONE]
  - TLS config per `tlsconfig.js` / `tls_skip_cert_verification.js`: `insecureSkipTLSVerify` is implemented. TLS/OCSP metadata (`tls_info`, `proto`, `ocsp`) surfaced.
  - Redirect controls: `maxRedirects` implemented.
  - Request tags/cookies/auth: `tags`, `cookies`, `auth` (Basic/Digest) implemented.
  - HTTP/2 and proto info: `res.proto` exposed.
  - HTML parsing helpers: `res.html()` via Jsoup implemented.
- HTTP batching & parallelism [DONE]
  - `http.batch()` implemented using Java 25 Structured Concurrency.
- Authentication helpers [DONE]
  - Digest/basic auth options implemented.
- Cookies [DONE]
  - `http.cookieJar()` and `new http.CookieJar()` implemented.
- Cryptography / encoding [DONE]
  - Extended hashes (`sha1`, `sha384/512`, `createHash`, `createHMAC`) implemented.
  - Base64 variants (`rawstd`, `rawurl`, etc.) implemented.
- Protocols [DONE]
  - WebSockets (`lyocell/ws`) implemented.
  - gRPC (`lyocell/net/grpc`) implemented (unary calls).
  - MCP (`lyocell/mcp`) implemented (SSE transport, JSON-RPC 2.0).
- WebCrypto subset [DONE]
  - `crypto.subtle` (AES-GCM/CBC), `getRandomValues`, `randomUUID` implemented.
- Experimental modules [DONE]
  - `lyocell/experimental/fs` and `csv` implemented.
- Timers [DONE]
  - `setTimeout`/`setInterval` implemented with thread-safe event loop.
- Secrets [DONE]
  - `lyocell/secrets` implemented.

If any of these are added, the corresponding examples should become runnable in Lyocell. For now they represent feature gaps relative to upstream k6 usage. 
 
## Implementation Plan (detailed)

### 1) HTTP options, response fields, HTML parsing
- Add params model: extend `HttpRequestOptions` to include `tlsCipherSuites`, `tlsVersionMin/Max`, `insecureSkipTLSVerify`, `maxRedirects`, per-request `redirects`, `tags`, `cookies`, `auth`. Wire to CLI `--http-*` flags parity if applicable.
- Client: adapt the shared Java `HttpClient`/`HttpRequestFactory` to:
  - Configure TLS context and cipher suites; allow insecure mode; surface negotiated `tls_version`, `tls_cipher_suite`; expose OCSP stapling status if supported (fall back to “unknown” when unavailable).
  - Honor redirect policy per-request and per-options; enforce cap to prevent loops.
  - Support HTTP/2: enable ALPN, expose `res.proto`.
  - Add cookie handling hooks (see Cookies section).
- Response: extend `Response` object to surface `proto`, TLS info, OCSP status, and maintain existing timing fields.
- HTML helpers: bundle Jsoup (lightweight) and add `res.html(selector?)` returning a queryable object with minimal methods used in examples (`text()`, `attr()`, `find` alias).
- Tests: integration against `httpbun` and `badssl` (for TLS/OCSP where possible); add redirect tests; add HTML parsing snapshot tests; add per-request override test.
- Docs: update `K6_REFERENCE.md` HTTP module table; add examples for TLS/redirect/auth/cookies/tags.

### 2) Request tagging & metrics
- Store request-level tags on metrics emissions (`http_req_duration`, `http_reqs`, custom trends) similar to k6.
- Ensure thresholds support tag filters (`metric{tag:value}`).
- Tests: threshold with tags, request tags propagation, custom metric tags.

### 3) Cookies & auth helpers
- Implement `CookieJar` and VU-local jar:
  - Backed by `java.net.CookieManager` per VU; allow `http.cookieJar()` and `new http.CookieJar()` returning wrappers.
  - Support request `cookies` param (non-persistent) and jar persistence across requests.
- Digest auth: implement preemptive + challenge flow (MD5/MD5-sess); basic auth already via headers but add `auth: "basic"` convenience and URL creds parsing.
- Tests: cookie persistence, path/domain rules, redirect cookie behavior, per-request cookies isolation, digest round-trip against a mock (httpbin digest endpoint).

### 4) HTTP batching (`http.batch`)
- API: support array of URLs and tuple form `[method, url, body?, params?]`.
- Execution: within a VU, run requests concurrently using virtual-thread pool; preserve result order; propagate tags/thresholds; aggregate metrics.
- Error handling: surface first error, but return array with responses/errors aligned.
- Tests: success/failure mix, order preservation, metric counts, threshold with tags, connection reuse.

### 5) Crypto & encoding
- Extend `lyocell/crypto`:
  - Add `sha1`, `sha384`, `sha512`, `createHash`, `createHMAC` with digest encodings (`hex`, `base64`, `base64url`, `raw`).
  - Ensure streaming updates work.
- Extend `lyocell/encoding`:
  - Support base64 variants: `std`, `rawstd`, `url`, `rawurl`, Unicode safe encode/decode.
- Tests: vectors for each hash/digest, HMAC verification, base64 round-trips including Unicode samples (from `base64.js`).

### 6) WebCrypto subset (`crypto.subtle`, `getRandomValues`, `randomUUID`)
- Scope: implement commonly used algorithms from examples: AES-GCM/CBC/CTR, RSA-OAEP, RSA-PSS, RSASSA-PKCS1-v1_5, ECDSA P-256/384, ECDH P-256, HMAC-SHA256/512, `digest`, `generateKey`, `import/export (raw/jwk/pkcs8/spki)`, `deriveBits`, `encrypt/decrypt`, `sign/verify`, `getRandomValues`, `randomUUID`.
- Implementation: Java adapters using `javax.crypto` / `java.security` with mapping to WebCrypto structures; validate key usages; enforce extractable flags; serialize JWK; conversions between ArrayBuffer and Java types via Graal interop.
- Testing: reproduce each `webcrypto/*` example; add unit tests for key import/export, sign/verify, deriveBits symmetry, AES encrypt/decrypt round-trips.
- Perf/Safety: limit key sizes to supported sets; clear sensitive buffers where possible.

### 7) Protocols: WebSocket
- Module: `lyocell/ws` API mirroring k6 (`ws.connect(url, params, cb)`, event handlers, ping/pong, close codes, setInterval/setTimeout equivalents).
- Engine: use Java WebSocket client (Tyrus/Jetty) per VU; map events to JS callbacks; propagate tags/metrics (`ws_connecting`, `ws_msgs`, `ws_duration`).
- Tests: echo server integration (similar to `experimental/websockets/test-echo.js` and `websocket.js`), ping/pong handling, tag propagation, close semantics.

### 8) Protocols: gRPC
- Module: `lyocell/net/grpc` with `Client`, `Stream`, `invoke`, `connect`, `close`, reflection toggle, health check.
- Engine: use gRPC Java; manage channels per VU with pooling; support unary, server/client/bidi streaming; map statuses; handle metadata.
- Tests: reuse k6 route_guide proto; cover `grpc_invoke`, `grpc_client_streaming`, `grpc_server_streaming`, `grpc_healthcheck`, `grpc_reflection`.

### 9) Browser (Playwright-style)
- Acknowledge large scope; phased approach:
  - Phase A: stub module returning clear “not supported” errors to keep scripts from crashing unexpectedly.
  - Phase B: integrate `k6/browser`-like API via embedded Playwright/Chromium (likely heavy; consider optional distribution profile). Map core APIs used in examples: contexts, pages, locators, waitFor*, throttling, screenshots, permissions, routing, metrics events.
  - Tests: run selected examples (`locator.js`, `evaluate.js`, `throttle.js`, `page_waitForEvent.js`, etc.) headless in CI; gate behind `BROWSER_EXPERIMENTAL` flag.

### 10) Experimental modules
- `k6/experimental/fs`: implement async file open/read/seek/stat using NIO; ensure per-VU isolation.
- `k6/experimental/csv` and `csv.Parser`: streaming and full-parse APIs.
- `k6/experimental/redis`: wrap a lightweight Redis client (lettuce/jedis) with async methods used in examples; include setup/teardown hooks.
- `k6/experimental/streams`: implement `ReadableStream` shim with timers.
- `k6/experimental/websockets`: alias to WebSocket implementation above.
- `k6/timers`: provide `setTimeout`, `setInterval`, `clearTimeout`, `clearInterval` using virtual threads/scheduler; align with browser-like behavior.
- Tests: mirror each experimental example (`fs.js`, `csv-parse.js`, `csv-parser.js`, `redis.js`, `streams.js`, `ws.js`, `timers.js`).

### 11) Secrets
- Module: `lyocell/secrets` with `get(key)` and `source(name)` supporting file, env, and mock providers.
- CLI/config: support `--secret-source` syntax parity; env var precedence.
- Tests: run `secrets/*.js` with mock providers; verify precedence and error handling.

### 12) Outputs & telemetry
- InfluxDB v1: implement Micrometer Influx registry, map k6 metrics schema; add CLI `-o influxdb=http://host:port/db`.
- OpenTelemetry: add OTLP gRPC exporter for metrics; map names/prefix; honor `K6_OTEL_*`-style envs.
- Tests: spin docker-compose (or Testcontainers) for Influx and OTEL collector; assert samples received; thresholds unaffected.

### 13) Ordering, milestones, and cross-cutting tasks
- Milestone 1 (HTTP parity): Sections 1–5; unblock most HTTP examples.
- Milestone 2 (Protocols core): Sections 7–8; add WebSocket and gRPC.
- Milestone 3 (Experimental + secrets + outputs): Sections 10–12.
- Milestone 4 (WebCrypto full subset): Section 6 (can start in parallel if resourced).
- Milestone 5 (Browser): Section 9; optional/flagged.
- Cross-cutting:
  - Docs: update `K6_REFERENCE.md`, `TECHNICAL_DESIGN.md`, and examples table; mark experimental/flagged features.
  - Metrics: add new metric types/tags as needed (ws/grpc).
  - CLI: add validation for new options; backward compatible defaults.
  - Backward compatibility: default to existing behavior; new features opt-in where they could alter semantics (TLS strictness, redirect limits).
  - QA: add integration suites mirroring upstream examples; consider nightly job to run all origin-k6 scripts that should now pass.

### Assumptions / guardrails for future implementation (so this file is sufficient alone)
- Platform: Java 25 with virtual threads; GraalJS for JS execution; Micrometer as metrics backbone; HTML report already present.
- Compatibility: stay API-compatible with k6 where specified; where not feasible (e.g., browser), gate behind `BROWSER_EXPERIMENTAL` flag and document partial parity.
- Performance: prefer non-blocking or virtual-thread-friendly IO; avoid global locks across VUs; per-VU isolation for contexts and connection state.
- Security: `insecureSkipTLSVerify` defaults to false; secrets providers should not log values; clear key material buffers when possible.
- Defaults: keep existing behavior unless an option is explicitly set; redirects enabled with current default unless capped by new options; HTTP/2 opportunistic via ALPN.
- Testing strategy: reuse `examples/origin-k6` as acceptance; add Testcontainers for Influx/OTEL/Redis; local echo servers for WS/HTTP; route_guide for gRPC; badssl or equivalent for TLS/OCSP.
- Documentation debt: every new module/API must be reflected in `K6_REFERENCE.md` plus a short recipe example; mark experimental/unstable items.

### Open questions to resolve during implementation
- Browser scope: do we ship an embedded Chromium or require user-installed Playwright? What is the default off/on behavior in headless CI?
- WebCrypto scope: do we need strict WebCrypto IDL compliance (subtle CryptoKey attributes) or a pragmatic subset sufficient for examples?
- Outputs: prioritize OTLP metrics only, or also traces/logs? InfluxDB v1 only, or v2 as well?
- Auth/Cookies: should cookies persist across VUs for shared-jar use cases, or stay per-VU only (k6 defaults to per-VU)?
- Tag cardinality: impose limits to prevent unbounded tags in metrics (per-request tags).
