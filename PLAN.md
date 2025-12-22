# Project Plan: Lyocell (Java k6 Clone)

This document outlines the roadmap for rewriting Lyocell as a high-performance, Java 25-based load testing tool compatible with k6 scripts.

## Strategy
We will adopt an **MVP-first approach**, focusing on getting a "walking skeleton" running end-to-end before adding complexity. The architecture relies heavily on **Java 25 Virtual Threads** for concurrency and **GraalJS** for script execution.

## Roadmap

### Phase 1: The Walking Skeleton (MVP)
**Goal:** Run a simple JS script that makes a single HTTP request.
*   **Core:** Set up project structure, dependencies (GraalJS, Polyglot).
*   **Module System:** Implement `LyocellFileSystem` to handle `import ... from 'k6/http'`.
*   **Engine:** Implement `JsEngine` with `HostAccess` to expose Java bindings (`HttpBridge`).
*   **Modules:** Implement a minimal `HttpBridge` (GET only) and `console.log` support.
*   **CLI:** Wire up `Main` to accept a file path, initialize the engine, and execute.
*   **Outcome:** `lyocell run script.js` executes and prints output.

### Phase 2: The Swarm (Concurrency)
**Goal:** Scale to multiple Virtual Users (VUs) using Virtual Threads.
*   **Concurrency:** Implement `TestEngine` to spawn $N$ Virtual Threads.
*   **Context:** Ensure strict isolation (one Graal Context per VU).
*   **Lifecycle:** Implement the `init` -> `setup` -> `default` (loop) -> `teardown` lifecycle.
*   **Options:** Parse `--vus` and `--duration` flags.
*   **Outcome:** `lyocell run -u 10 -d 5s script.js` runs 10 concurrent VUs.

### Phase 3: The Pulse (Metrics)
**Goal:** Collect, aggregate, and report performance data.
*   **API:** Implement `k6/metrics` (`Counter`, `Trend`, `Rate`, `Gauge`).
*   **Aggregation:** Create a high-performance, lock-free `MetricsCollector` (distributed buffers + central ingester).
*   **Reporting:** Generate the standard "end-of-test" summary to stdout.
*   **Outcome:** Users see request rates, latencies, and pass/fail counts at the end.

### Phase 4: The Toolkit (Feature Parity)
**Goal:** Support common k6 scripting patterns.
*   **Core:** Implement `check()`, `group()`, and `sleep()`.
*   **HTTP:** Full `Response` object (timings, JSON parsing) and `params` (headers, tags).
*   **TUI:** Real-time status dashboard using JLine (re-integrating existing code).
*   **Outcome:** Ability to run realistic, complex user scripts.

### Phase 5: The Director (Advanced Orchestration)
**Goal:** Support complex scenarios and thresholds.
*   **Scenarios:** Parse `options.scenarios` to support `ramping-vus` and `constant-arrival-rate`.
*   **Thresholds:** Implement pass/fail criteria (e.g., `p(95) < 200`).
*   **Outcome:** Full k6 compatibility for CI/CD pipelines.

## Immediate Next Steps
1.  Initialize the Phase 1 structure.
2.  Implement the basic `JsEngine` wrapper.
3.  Create the `HttpModule` stub.
