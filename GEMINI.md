# Project Context: Lyocell (k6 Clone)

## Project Overview

**Lyocell** is a high-performance load testing tool rewritten to be a **Java-based clone of k6**. It allows users to run standard k6 JavaScript scripts using **Java 25 Virtual Threads** for concurrency and **GraalVM (GraalJS)** for script execution.

**Key Technologies:**
*   **Java 25:** Uses **Virtual Threads** (Project Loom) for massive concurrency (1 Virtual User = 1 Virtual Thread).
*   **GraalVM (GraalJS):** Embeds the Graal JavaScript engine to execute k6 scripts (`.js`) with high performance.
*   **Structured Concurrency:** Manages the lifecycle of Virtual Users efficiently.
*   **Native Image:** Compiles to a native binary for instant startup.

## Core Documentation
*   **`K6_REFERENCE.md`**: The user manual. Explains k6 concepts, CLI usage, and the JS API schema we are implementing.
*   **`PLAN.md`**: The project roadmap. Defines the 5 phases of development (from MVP to advanced features).
*   **`TECHNICAL_DESIGN.md`**: The engineering blueprint. Details the internal threading model, module loading strategy (`LyocellFileSystem`), and metrics architecture.

## Building and Running

### Prerequisites
*   **Java 25** (with `--enable-preview`).
*   **GraalVM/Mandrel** (optional, for native builds).

### Key Commands

| Action | Command | Description |
| :--- | :--- | :--- |
| **Run** | `./gradlew run --args="script.js"` | Runs the application with the specified script. |
| **Build Native** | `./gradlew nativeCompile` | Builds the standalone native binary. |
| **Test** | `./gradlew test` | Runs unit tests (JUnit 5). |

## Architecture Summary

*   **Engine:** `TestEngine` manages the `StructuredTaskScope` and `VuWorker` threads.
*   **JS Runtime:** Uses a strict "Context-per-VU" model. `JsEngine` handles the Graal `Context` creation and script evaluation.
*   **Modules:** Custom `k6` modules (like `k6/http`) are injected via a custom `FileSystem` and `HostAccess` bindings, mapping JS calls to Java `Bridge` interfaces.
*   **Metrics:** A high-throughput, distributed metrics collection system (buffers -> central ingester) using `LongAdder` for lock-free counting.

## Development Conventions
*   **Strict Java 25:** Must use `--enable-preview`.
*   **TDD & Quality:** The project strictly follows **Test-Driven Development (TDD)**. Every new feature or fix must include unit or integration tests.
*   **Test Coverage:** Maintain high test coverage (minimum 80% as enforced by Jacoco).
*   **GraalJS Compatibility:** All JS features must align with ESM standards (`import`).
*   **Tests:** Use `Testcontainers` for integration testing against real HTTP endpoints (e.g., `httpbun`).