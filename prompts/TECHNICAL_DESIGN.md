# Lyocell Technical Design & Specifications

This document complements `K6_REFERENCE.md` by providing the **implementation details** required to build the system.

## 1. Engine Architecture (The Core)

### A. Lifecycle & Threading
We use a **Thread-per-VU** model powered by Java 25 Virtual Threads. The lifecycle strictly follows the standard k6 stages:

1.  **`TestEngine`**: The main coordinator.
    *   Creates a `StructuredTaskScope` (Java 25) to manage the VU threads.
    *   Parses CLI arguments (`-u`, `-i`) and environment variables.
    *   Instantiates `VuWorker` runnables.

2.  **`VuWorker` & `JsEngine`**: A `Runnable` representing one Virtual User.
    *   **State**: Holds its own `JsEngine` instance.
    *   **Event Loop**: `JsEngine` implements an **Event Loop** using a `LinkedBlockingQueue`. This allows background events (SSE messages, timers, network callbacks) to be queued and processed by the main VU thread.
    *   **Context Isolation**: Every VU gets a fresh GraalJS `Context`. Access is strictly controlled via a `lock`.
    *   **Pause/Resume**: When the VU blocks on a network call or a future, it "pauses" the engine (releasing the context lock) and processes queued events to avoid deadlocks.
    *   **Execution Flow**:
        1.  **`init` Stage**: Creates Context, loads the user script, executes global scope.
        2.  **`setup` Stage**: (Only VU #0 does this). Executes `setup()`, serializes result.
        3.  **`vu` Stage**: Executes `default()` repeatedly.
        4.  **`teardown` Stage**: Cleanup.
        5.  **`teardown` Stage**: (Only VU #0). Executes `teardown()`.

### B. GraalJS Context Management
*   **Isolation**: STRICT. `Context` is **not** thread-safe. Every VU gets a fresh `Context`.
*   **Source Caching**: The user's script is read *once* into a `org.graalvm.polyglot.Source` object. This cached `Source` is passed to every VU context to avoid re-reading/parsing overhead.
*   **Engine Sharing**: All Contexts share the same underlying `org.graalvm.polyglot.Engine` to enable code sharing (JIT compilation artifacts) across threads.

## 2. Module System (`import ... from 'lyocell/...'`)

GraalJS does not automatically find "virtual" files. We implemented a custom filesystem to inject the `k6` API.

### A. `LyocellFileSystem`
Implements `org.graalvm.polyglot.io.FileSystem`.
*   **Strategy**: Intercepts `parsePath` and `newByteChannel`.
*   **Detection**: Checks if paths start with or contain `lyocell/` (e.g., `lyocell/http`, `lyocell/metrics`).
*   **Virtual Files**:
    *   `lyocell/http`: Returns a synthetic source code that exports Java bindings (proxies to `LyocellHttp`). Supports `batch()`.
    *   `lyocell/net/grpc`: Bridge to gRPC Java client for unary calls.
    *   `lyocell/ws`: WebSocket client bridge.
    *   `lyocell/mcp`: Model Context Protocol client with SSE transport.
    *   `lyocell/timers`: Standard `setTimeout` and `setInterval` implementations.
    *   `lyocell/experimental/fs`: Basic file system access.
    *   `lyocell/experimental/csv`: CSV parsing utilities.
    *   `lyocell/metrics`: Exports `Counter` and `Trend` classes that bridge to `LyocellMetrics`.
    *   `k6`: Exports core functions like `check`, `group`, `sleep`.

### B. Global Bindings (`HostAccess`)
We inject Java objects into the `globalThis` scope of every Context.
*   **Security**: `HostAccess.newBuilder().allowAccessAnnotatedBy(HostAccess.Export.class).build()`.
*   **Global Objects**:
    *   `LyocellHttp`: Backed by `HttpModule.java`.
    *   `LyocellMetrics`: Backed by `MetricsModule.java`.
    *   `LyocellCore`: Backed by `CoreModule.java`.
    *   `__ENV`: A map exposing environment variables.

## 3. Metrics System

### A. Collection
*   **`MetricsCollector`**: Facade over **Micrometer** with a `CompositeMeterRegistry` (default `SimpleMeterRegistry` + optional sinks).
*   **Counters/Trends**: k6 metric types map to Micrometer counters and `DistributionSummary` (publishes p95/p99).
*   **Rates/Gauges**: Boolean rates tracked as `<name>.true`/`<name>.total`; gauges backed by `AtomicReference`.
*   **Timeline**: Iteration success/failure recorded into a time-series buffer for HTML report generation.

### B. Reporting
*   **`SummaryReporter`**: Reads Micrometer snapshots to print k6-style ASCII summaries.
*   **`HtmlReportRenderer`**: Consumes `TimeSeriesData` (1s buckets) to render static, shareable HTML without JS dependencies.

## 4. Observability Architecture

Lyocell uses **Micrometer** as its metrics engine and generates static HTML reports.

### A. Micrometer Integration

*   **Core**: Uses `io.micrometer.core.instrument.CompositeMeterRegistry` to manage internal metrics.
*   **Polymorphism**: `MetricsCollector` delegates to the registry.
*   **Efficiency**: Utilizes **HdrHistogram** (via Micrometer) for accurate, memory-bounded percentiles (p95, p99).

### B. Reporting

*   **Console**: A text-based summary is printed to stdout at the end of the test.
*   **HTML**: A standalone HTML5 report is generated via `HtmlReportRenderer`, containing charts and detailed tables.

### C. Configuration

*   **Source**: Configuration is read from `options.lyocell.outputs` (JS) or CLI flags (`-o`).



## 5. Advanced Workloads (Scenarios)



Lyocell supports the full k6 `scenarios` specification, allowing for complex workload modeling.



### A. Workload Executors

Each scenario is assigned a specialized `WorkloadExecutor`:

*   **`SharedIterationsExecutor`**: A fixed number of iterations shared among a pool of VUs.

*   **`PerVuIterationsExecutor`**: Each VU executes a fixed number of iterations.

*   **`ConstantVusExecutor`**: A fixed number of VUs execute as many iterations as possible for a specific duration.

*   **`RampingVusExecutor`**: A variable number of VUs execute iterations, following a predefined schedule of stages.

*   **`ConstantArrivalRateExecutor`**: A fixed number of iterations are started at a specific rate (iterations per period).



### B. Execution Orchestration

The `TestEngine` uses `StructuredTaskScope` to manage the lifecycle of these executors, ensuring that resources are cleaned up and metrics are aggregated even if a scenario fails or times out.



## 6. Implementation Details



1.  **JSON Handling**: We use Jackson to bridge JSON data between `setup()` and `default()` phases, as Graal values cannot be shared across contexts.

2.  **Native Image**: The project is fully compatible with GraalVM Native Image.

    *   **Reflection**: `reflect-config.json` manually registers all Module methods.

    *   **GC**: Uses Serial GC on macOS (G1 not supported) and G1 on Linux.

3.  **Standard Library**: Modules like `lyocell/crypto`, `lyocell/encoding`, `lyocell/data`, and `lyocell/execution` are implemented as high-performance Java modules injected into the JS environment.
