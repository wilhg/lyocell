# Project Plan: Lyocell (Java k6 Clone)

This document outlines the roadmap for rewriting Lyocell as a high-performance, Java 25-based load testing tool compatible with k6 scripts.

## Strategy
We adopted an **MVP-first approach**, focusing on getting a "walking skeleton" running end-to-end before adding complexity. The architecture relies heavily on **Java 25 Virtual Threads** for concurrency and **GraalJS** for script execution.

## Roadmap (Completed)

### ✅ Phase 1: The Walking Skeleton (MVP)
**Goal:** Run a simple JS script that makes a single HTTP request.
*   **Core:** Set up project structure, dependencies (GraalJS, Polyglot).
*   **Module System:** Implemented `LyocellFileSystem` to handle `import ... from 'lyocell/http'`.
*   **Engine:** Implemented `JsEngine` with `HostAccess` to expose Java bindings (`HttpBridge`).
*   **Modules:** Implemented a minimal `HttpBridge` (GET/POST) and `console.log` support.
*   **CLI:** Wired up `Main` to accept a file path, initialize the engine, and execute.

### ✅ Phase 2: The Swarm (Concurrency)
**Goal:** Scale to multiple Virtual Users (VUs) using Virtual Threads.
*   **Concurrency:** Implemented `TestEngine` to spawn $N$ Virtual Threads using `StructuredTaskScope`.
*   **Context:** Ensured strict isolation (one Graal Context per VU).
*   **Lifecycle:** Implemented the `init` -> `setup` -> `default` -> `teardown` lifecycle.
*   **Options:** Parsed `--vus` (`-u`) and `--iterations` (`-i`) flags.

### ✅ Phase 3: The Pulse (Metrics)
**Goal:** Collect, aggregate, and report performance data.
*   **API:** Implemented `lyocell/metrics` (`Counter`, `Trend`).
*   **Aggregation:** Created a high-performance, thread-safe `MetricsCollector` (using `LongAdder` and synchronized trends).
*   **Reporting:** Generated the standard "end-of-test" summary to stdout.

### ✅ Phase 4: The Toolkit (Feature Parity)
**Goal:** Support common k6 scripting patterns.
*   **Core:** Implemented `check()`, `group()`, and `sleep()`.
*   **HTTP:** Full `Response` object (status, body, headers, timings, JSON parsing).
*   **Environment:** Added support for `__ENV` variable for dynamic configuration.

### ✅ Phase 5: The Director (Orchestration)
**Goal:** Support thresholds and validation.
*   **Thresholds:** Implemented pass/fail criteria (e.g., `rate>0.95`, `p(95)<200`).
*   **Tests:** Added robust integration tests using `Testcontainers` and `sharat87/httpbun`.

## Roadmap (Future)

### ✅ Phase 6: The Observatory (Observability)
**Goal:** Real-time visualization via Grafana and standard metrics exports.
*   **Architecture:** Adopted **Micrometer** as the polymorphic metrics engine.
*   **Optimization:** Implemented `JdkHttpSender` using Java 25 HttpClient for zero-dependency push.
*   **Outputs:** Supports **InfluxDB** (Push) and **Prometheus** (Pull/Push) out of the box.
*   **Configuration:** Configurable via CLI flags (`--out`) and JS `options` (`options.lyocell.outputs`).

### ✅ Phase 7: Standard Library Expansion
**Goal:** Implement the full suite of standard k6 utility modules.
*   **`lyocell/execution`:** Expose `vu.idInTest`, `vu.iterationInInstance` for unique data handling.
*   **`lyocell/data`:** Implement `SharedArray` for memory-efficient data loading.
*   **`lyocell/crypto` & `lyocell/encoding`:** Add SHA-256, HMAC, and Base64 support.
*   **`k6` Core:** Add `fail()`, `randomSeed()`.

### ✅ Phase 8: The Choreographer (Advanced Scenarios)
**Goal:** Implement the `scenarios` configuration object for complex workload modeling.
*   **Architecture:** Refactor `TestEngine` to support multiple parallel executors.
*   **Executors:** Implemented `ramping-vus`, `constant-arrival-rate`, `shared-iterations`, `constant-vus`, and `per-vu-iterations`.
*   **Config:** Fully parse the `options.scenarios` object.

## Current Status
The project is functionally complete as an MVP (Minimum Viable Product). It supports the core k6 API, runs concurrently on Virtual Threads, compiles to a Native Image, and includes comprehensive documentation and examples.

## Future Enhancements
*   **HTTP Parallelism:** Implement `http.batch()` for parallel requests within a VU.
*   **Granular Timings:** Add `blocked`, `connecting`, `tls_handshaking`, etc., to `http_req_duration`.
*   **TUI:** Real-time dashboard (Curses-like UI).
*   **Protocols:** WebSocket, gRPC support.
*   **Distributions:** Linux/Windows builds in CI.
