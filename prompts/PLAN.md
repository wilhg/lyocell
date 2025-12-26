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

### ✅ Phase 9: Protocols & Advanced Modules
**Goal:** Expand beyond HTTP to support modern protocols and experimental k6 features.
*   **HTTP Parity:** Implemented `http.batch()`, advanced options (`auth`, `tags`, `cookies`), and HTML parsing (Jsoup).
*   **Protocols:** Implemented `lyocell/ws` (WebSocket) and `lyocell/net/grpc` (gRPC).
*   **WebCrypto:** Implemented `crypto.subtle` subset (AES-GCM, AES-CBC).
*   **Timers:** Added `setTimeout`, `setInterval` with safe JS event loop.
*   **Experimental:** Implemented `lyocell/experimental/fs` and `lyocell/experimental/csv`.
*   **MCP:** Implemented `lyocell/mcp` for Model Context Protocol load testing.

### ✅ Phase 10: Stability & Lifecycle
**Goal:** Ensure robustness and proper resource cleanup.
*   **Event Loop:** Refactored `JsEngine` to use a `LinkedBlockingQueue` event loop for thread-safe asynchronous operations.
*   **Cleanup:** Implemented `LyocellModule.close()` for resource disposal (connections, timers).

## Roadmap (TBD)

### ⏳ Phase 11: Distribution & UI
**Goal:** Improve developer experience and multi-platform support.
*   **TUI:** Real-time dashboard (Curses-like UI).
*   **Distributions:** Linux/Windows builds in CI.
*   **Advanced gRPC:** Proto-file loading and reflection-based invocation.

## Current Status
The project is now a feature-rich k6 clone. It supports HTTP, gRPC, WebSocket, and MCP protocols, along with advanced standard library features like SharedArray, WebCrypto, and Timers. It uses a high-performance Java 25 Virtual Thread architecture with a robust JS event loop.

## Completed Enhancements
*   **HTTP Parallelism:** `http.batch()` is implemented using Structured Concurrency.
*   **Granular Timings:** SURFACED in the response object.
*   **Protocols:** WebSocket, gRPC, and MCP are fully supported.
