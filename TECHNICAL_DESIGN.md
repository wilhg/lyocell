# Lyocell Technical Design & Specifications

This document complements `K6_REFERENCE.md` by providing the **implementation details** required to build the system. It bridges the gap between the high-level concepts and the code we need to write.

## 1. Engine Architecture (The Core)

### A. Lifecycle & Threading
We will use a **Thread-per-VU** model powered by Java 25 Virtual Threads.

1.  **`TestEngine`**: The main coordinator.
    *   Creates a `StructuredTaskScope` (Java 25) to manage the VU threads.
    *   Instantiates `VuWorker` runnables.
    *   Waits for all tasks to complete or for a termination signal.

2.  **`VuWorker`**: A `Runnable` representing one Virtual User.
    *   **State**: Holds its own `Context` (GraalJS), `VuContext` (runtime info), and `MetricsBuffer`.
    *   **Execution Flow**:
        1.  `init()`: Creates Context, loads the user script (cached `Source`), executes global scope.
        2.  `setup()`: (Only VU #0 does this). Executes `setup()`, serializes result to JSON.
        3.  `broadcast`: The JSON result is shared with all other VUs (who parse it back to a JS object).
        4.  `default()`: Loops until duration/iteration limit.
        5.  `teardown()`: (Only VU #0).

### B. GraalJS Context Management
*   **Isolation**: STRICT. `Context` is **not** thread-safe. Every VU gets a fresh `Context`.
*   **Optimization**:
    *   **Source Caching**: The user's script is read *once* into a `org.graalvm.polyglot.Source` object. This cached `Source` is passed to every VU context to avoid re-reading/parsing overhead.
    *   **Engine Sharing**: All Contexts share the same underlying `org.graalvm.polyglot.Engine` to enable code sharing (JIT compilation artifacts) across threads.

## 2. Module System (`import ... from 'k6/...'`)

GraalJS does not automatically find "virtual" files. We must implement a custom filesystem to inject the `k6` API.

### A. `LyocellFileSystem`
We will implement `org.graalvm.polyglot.io.FileSystem`.
*   **Protocol**: Intercept paths/URIs. Use a custom scheme (e.g., `lyocell:k6/http`) or strictly intercept paths starting with `k6/` if using the default scheme. *Recommendation:* Use a custom URI scheme to avoid collision with relative file paths.
*   **Virtual Files**:
    *   `k6/http`: Returns a synthetic source code that exports our Java bindings.
    *   `k6/metrics`: Same.
*   **Implementation**:
    ```java
    // Pseudo-code for the "k6/http" synthetic file content
    export const get = (url, params) => globalThis.LyocellHttp.get(url, params);
    export const post = (url, body, params) => globalThis.LyocellHttp.post(url, body, params);
    // ... maps JS exports to the global Java binding objects
    ```

### B. Global Bindings (`HostAccess`)
We will inject Java objects into the `globalThis` scope of every Context.
*   **Security**: Use `HostAccess.newBuilder().allowAccessAnnotatedBy(HostAccess.Export.class).build()`.
*   **Interfaces**:
    *   `HttpBridge`: Methods like `get(String url, Value params)`.
    *   `MetricsBridge`: Methods like `add(String name, double value, Value tags)`.
    *   `CoreBridge`: `check`, `group`, `sleep`.

### C. Data Binding & Type Conversion
GraalJS type conversion can be tricky.
*   **Arguments**: Prefer accepting `org.graalvm.polyglot.Value` in Java Bridge methods instead of `Map<String, Object>`. This gives us fine-grained control to inspect `hasMembers()`, `hasArrayElements()`, etc., without eager conversion overhead or type loss.
*   **Return Values**: Return simple POJOs (which Graal maps to JS objects) or `ProxyObject` if dynamic behavior is needed.
*   **Errors**: Java Exceptions thrown in Bridge methods propagate to JS. We should catch internal errors and throw a custom `PolyglotException` or a user-friendly RuntimeException that JS can `try/catch`.

## 3. Metrics System (High Performance)

Aggregating stats from 10,000+ VUs requires a lock-free approach.

### A. Distributed Buffering
*   **`VuMetricsBuffer`**: Each VU has a local `ArrayList<MetricSample>`.
*   **Sampling**: Instead of locking on every `http.get`, we record the metric into the local buffer.
*   **Flushing**: Every 100ms (or 100 samples), the buffer is "published" to the central collector.

### B. Ingestion
*   **`MetricsIngester`**: A background thread (or Disruptor ring buffer).
*   **Storage**:
    *   **Counters**: `java.util.concurrent.atomic.LongAdder` (High write concurrency).
    *   **Gauges**: `AtomicReference<Double>`.
    *   **Trends**: `DoubleHistogram` (from HdrHistogram, if allowed, or a custom bucket implementation).

## 4. Implementation Steps (Refined)

1.  **Foundation**:
    *   Create `LyocellFileSystem` (The hardest part: getting imports to work).
    *   Setup `JsEngine` with `Shared Engine` + `HostAccess`.
2.  **The Bridge**:
    *   Define `HttpBridge` interface.
    *   Inject it into the context.
    *   Write the synthetic `k6/http` JS wrapper.
3.  **Runner**:
    *   Wire up `Main` -> `TestEngine` -> `VuWorker`.
