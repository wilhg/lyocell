# Lyocell Technical Design & Specifications

This document complements `K6_REFERENCE.md` by providing the **implementation details** required to build the system.

## 1. Engine Architecture (The Core)

### A. Lifecycle & Threading
We use a **Thread-per-VU** model powered by Java 25 Virtual Threads. The lifecycle strictly follows the standard k6 stages:

1.  **`TestEngine`**: The main coordinator.
    *   Creates a `StructuredTaskScope` (Java 25) to manage the VU threads.
    *   Parses CLI arguments (`-u`, `-i`) and environment variables.
    *   Instantiates `VuWorker` runnables.

2.  **`VuWorker`**: A `Runnable` representing one Virtual User.
    *   **State**: Holds its own `Context` (GraalJS) and execution loop.
    *   **Execution Flow**:
        1.  **`init` Stage**: Creates Context, loads the user script (cached `Source`), executes global scope.
        2.  **`setup` Stage**: (Only VU #0 does this). Executes `setup()`, serializes result to JSON.
        3.  **Data Broadcast**: The JSON result from `setup` is shared with all other VUs.
        4.  **`vu` Stage**: Executes `default()` function repeatedly until the iteration/duration limit is reached.
        5.  **`teardown` Stage**: (Only VU #0). Executes `teardown()`.

### B. GraalJS Context Management
*   **Isolation**: STRICT. `Context` is **not** thread-safe. Every VU gets a fresh `Context`.
*   **Source Caching**: The user's script is read *once* into a `org.graalvm.polyglot.Source` object. This cached `Source` is passed to every VU context to avoid re-reading/parsing overhead.
*   **Engine Sharing**: All Contexts share the same underlying `org.graalvm.polyglot.Engine` to enable code sharing (JIT compilation artifacts) across threads.

## 2. Module System (`import ... from 'k6/...'`)

GraalJS does not automatically find "virtual" files. We implemented a custom filesystem to inject the `k6` API.

### A. `LyocellFileSystem`
Implements `org.graalvm.polyglot.io.FileSystem`.
*   **Strategy**: Intercepts `parsePath` and `newByteChannel`.
*   **Detection**: Checks if paths start with or contain `k6/` (e.g., `k6/http`, `k6/metrics`).
*   **Virtual Files**:
    *   `k6/http`: Returns a synthetic source code that exports Java bindings (proxies to `LyocellHttp`).
    *   `k6/metrics`: Exports `Counter` and `Trend` classes that bridge to `LyocellMetrics`.
    *   `k6`: Exports core functions like `check`, `group`, `sleep`.

### B. Global Bindings (`HostAccess`)
We inject Java objects into the `globalThis` scope of every Context.
*   **Security**: `HostAccess.newBuilder().allowAccessAnnotatedBy(HostAccess.Export.class).build()`.
*   **Global Objects**:
    *   `LyocellHttp`: Backed by `HttpModule.java`.
    *   `LyocellMetrics`: Backed by `MetricsModule.java`.
    *   `LyocellCore`: Backed by `CoreModule.java`.
    *   `__ENV`: A map exposing environment variables.

## 3. Metrics System (Current MVP)

### A. Collection
*   **`MetricsCollector`**: A thread-safe, central singleton passed to all VUs.
*   **Counters**: Uses `java.util.concurrent.atomic.LongAdder` for high-throughput, lock-free counting.
*   **Trends**: Uses `Collections.synchronizedList(new ArrayList<>())`.

### B. Reporting
*   **`SummaryReporter`**: Calculates aggregations (avg, min, max, p95, p99) at the end of the test and prints a k6-style ASCII table.

## 4. Observability Architecture (Future: Phase 6)

To support Grafana (InfluxDB/Prometheus) without bloating the binary:

### A. Micrometer Integration
*   **Core**: Switch to `io.micrometer.core.instrument.CompositeMeterRegistry`.
*   **Polymorphism**: `MetricsCollector` will delegate to the registry, decoupling recording from exporting.
*   **Efficiency**: Use **HdrHistogram** (via Micrometer) for accurate, memory-bounded percentiles.

### B. Lightweight Networking
*   **Problem**: Standard Micrometer registries (e.g., Influx) pull in heavy clients like `OkHttp`.
*   **Solution**: Implement `JdkHttpSender` wrapping Java 25's `java.net.http.HttpClient`.
*   **Result**: Zero extra network dependencies.

### C. Configuration
*   **Source**: Configuration read from `options.lyocell.outputs` (JS) or CLI flags.
*   **Push (Influx)**: Starts a background Virtual Thread to flush metrics periodically.
*   **Pull (Prometheus)**: Starts a lightweight `com.sun.net.httpserver.HttpServer` on a Virtual Thread to serve `/metrics`.

## 5. Implementation Details

1.  **JSON Handling**: We use Jackson to bridge JSON data between `setup()` and `default()` phases, as Graal values cannot be shared across contexts.
2.  **Native Image**: The project is fully compatible with GraalVM Native Image.
    *   **Reflection**: `reflect-config.json` manually registers all Module methods.
    *   **GC**: Uses Serial GC on macOS (G1 not supported) and G1 on Linux.