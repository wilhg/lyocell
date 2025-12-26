# Project Context: Lyocell (k6 Clone)

## Project Overview

**Lyocell** is a high-performance load testing tool rewritten to be a **Java-based clone of k6**. It allows users to run standard k6 JavaScript scripts using **Java 25 Virtual Threads** for concurrency and **GraalVM (GraalJS)** for script execution.

**Key Technologies:**
*   **Java 25:** Uses **Virtual Threads** (Project Loom) for massive concurrency (1 Virtual User = 1 Virtual Thread).
*   **GraalVM (GraalJS):** Embeds the Graal JavaScript engine to execute k6 scripts (`.js`) with high performance.
*   **Structured Concurrency:** Manages the lifecycle of Virtual Users efficiently.
*   **Native Image:** Compiles to a native binary for instant startup.

## Development Status
*   **Phase 1-8 Complete:** Core engine, basic modules, observability (HTML Report), standard library expansion, and advanced scenarios are implemented.
*   **Detailed Docs:** Technical design, plan, and reference manuals are located in the `prompts/` directory.
*   **Tests:** 100% integration test coverage for core features (`http`, `metrics`, `cli`, `examples`, `scenarios`).
*   **Examples:** `examples/` folder contains tested scripts (`basic-get.js`, `post-json.js`) that run against `sharat87/httpbun`.

## Building and Running

### Prerequisites
*   **Java 25** (with `--enable-preview`).
*   **GraalVM** (for native builds).

### Key Commands

| Action | Command | Description |
| :--- | :--- | :--- |
| **Run** | `./gradlew run --args="script.js -u 10"` | Runs the application with the specified script. |
| **Build Native** | `./gradlew nativeCompile` | Builds the standalone native binary. |
| **Test** | `./gradlew test` | Runs unit tests (JUnit 5). |

## Architecture Summary

*   **Engine:** `TestEngine` manages the `StructuredTaskScope` and various `WorkloadExecutor` implementations (e.g., `RampingVusExecutor`).
*   **JS Runtime:** Uses a strict "Context-per-VU" model with `JsEngine` handling Graal `Context` creation and script evaluation.
*   **Modules:** Standard k6 modules are implemented as Java modules and injected via `LyocellFileSystem`.
*   **Metrics:** Uses **Micrometer** for high-performance metrics aggregation and generates HTML reports.

## Development Conventions
*   **Strict Java 25:** Must use `--enable-preview`.
*   **Best Practice:** Must follow Java 25 best practices.
*   **TDD:** Every new feature or fix must include unit or integration tests.
*   **GraalJS:** Enable experimental options (`engine.WarnVirtualThreadSupport=false`) to suppress warnings on Native Image.
