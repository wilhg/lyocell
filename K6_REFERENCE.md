# Lyocell Architecture & Reference (k6 clone)

This document is the definitive blueprint for rewriting Lyocell as a high-performance, Java 25-based clone of k6.

## 1. Internal Architecture

### A. Threading Model (Java 25)
Unlike k6 (Go), which uses goroutines, Lyocell will leverage **Java 25 Virtual Threads** (Project Loom).
*   **1 VU = 1 Virtual Thread**: Each Virtual User runs on its own dedicated Virtual Thread. This allows blocking I/O (e.g., HTTP requests, `sleep()`) to be written in a synchronous style without blocking OS threads.
*   **Structured Concurrency**: Use `StructuredTaskScope` to manage the lifecycle of VUs. If the test is aborted, the scope ensures all VUs are properly cancelled.
*   **Scoped Values**: Use `ScopedValue<VuContext>` to implicitly pass the current VU's state (ID, iteration, context) to static helper methods (like `k6/http` functions) without polluting method signatures or using expensive `ThreadLocal`.

### B. JavaScript Engine (GraalJS)
*   **Context Strategy**: GraalJS `Context` is **not thread-safe**.
    *   **Per-VU Context**: Each VU (Virtual Thread) *must* have its own isolated `Context` instance.
    *   **Shared Code**: To save memory, parsed `Source` objects (the test script) should be cached and shared across contexts.
*   **Module Loading**:
    *   We will strictly support **ES Modules** (`import`).
    *   **Internal Modules**: `k6/http`, `k6/metrics`, etc., will be implemented as Java classes and exposed via a custom `FileSystem` or by injecting them into the global scope during Context initialization, then aliased in a synthetic "loader" script.
    *   **Files**: Local script files will be loaded via the standard file system.

### C. Metrics Aggregation
High-concurrency aggregation is critical. We will avoid a single central lock.
*   **Distributed Aggregation**: Each VU maintains a local buffer of metrics (e.g., `VuMetricsBuffer`).
*   **Flush Mechanism**:
    *   **Periodic**: Every 1s (or batch size), the VU flushes its buffer to a central `MetricsIngester`.
    *   **Ingester**: Uses `LongAdder` for counters and `DoubleAccumulator` for simple trends to ensure lock-free ingestion.
    *   **RingBuffer (Disruptor style)**: For high-volume distinct data points (like detailed trend percentiles), use a ring buffer to pass events from VUs to a single "calculator" thread that updates the global histograms.

## 2. Configuration (`options` object)

The `options` object exported by the script controls execution. We must parse this JSON object from JS.

### A. Scenarios (`options.scenarios`)
A map of scenario names to configuration objects.
*   **`executor`**: String (e.g., `constant-vus`, `ramping-vus`).
*   **`vus`**: Integer (for constant/ramping VUs).
*   **`duration`**: String (e.g., `30s`).
*   **`stages`**: Array of `{ duration: string, target: int }` (for ramping).
*   **`startTime`**: String (offset).
*   **`exec`**: String (exported function name to run, defaults to `default`).

### B. Thresholds (`options.thresholds`)
A map of metric names to criteria strings.
*   **Keys**: Metric name (e.g., `http_req_duration`, `checks`).
*   **Values**: Array of strings (e.g., `['p(95)<200', 'rate<0.01']`).
*   **Abort**: `['p(95)<200', { threshold: 'rate<0.05', abortOnFail: true }]`.

## 3. JavaScript API Schema

### A. `k6/http`
**`request(method, url, [body], [params])`**
*   **`params` Object**:
    *   `headers`: `Map<String, String>` (Request headers).
    *   `cookies`: `Map<String, String | Object>` (Request cookies).
    *   `tags`: `Map<String, String>` (Custom tags for metrics).
    *   `auth`: `String` (e.g., `"user:pass"` for Basic Auth).
    *   `timeout`: `String` (e.g., `"5s"`).
    *   `redirects`: `Integer` (Max redirects).
    *   `compression`: `String` (`gzip`).
*   **`Response` Object**:
    *   `status`: `int` (200).
    *   `url`: `String`.
    *   `body`: `String`.
    *   `headers`: `Map<String, String>`.
    *   `cookies`: `Map<String, Object>`.
    *   `error`: `String`.
    *   `error_code`: `int`.
    *   `timings`: `{ duration: float, blocked: float, connecting: float, sending: float, waiting: float, receiving: float }`.
    *   `json(selector)`: Method to parse JSON.

### B. `k6` (Core)
*   **`check(val, sets, [tags])`**:
    *   `sets`: `{ "check name": (val) => boolean }`.
    *   Returns `boolean` (true if all pass).
*   **`group(name, fn)`**:
    *   Executes `fn`. Adds `group` tag to all metrics generated inside.
*   **`sleep(seconds)`**:
    *   Blocking sleep (via `Thread.sleep`).

### C. `k6/metrics`
*   **Classes**: `Counter`, `Gauge`, `Rate`, `Trend`.
*   **Constructor**: `new Counter('name', [isTime])`.
*   **Methods**: `add(value, [tags])`.

## 4. Java Implementation Strategy

### Directory Structure
```
src/main/java/com/wilhg/lyocell/
├── engine/
│   ├── TestEngine.java       # Main coordinator
│   ├── VuWorker.java         # Runnable for VirtualThread
│   └── ScenarioScheduler.java
├── js/
│   ├── JsRuntime.java        # Graal Context Wrapper
│   └── ModuleLoader.java     # Custom module resolver
├── modules/
│   ├── HttpModule.java       # k6/http implementation
│   ├── CoreModule.java       # k6 (check, group, sleep)
│   └── MetricsModule.java    # k6/metrics
├── metrics/
│   ├── MetricsCollector.java # Aggregator
│   └── listeners/            # Listeners for TUI/Console
└── model/
    ├── HttpRequest.java
    └── HttpResponse.java
```

### Key Dependencies
*   **GraalJS & Polyglot**: For JS execution.
*   **Java 25 (Preview)**: For Virtual Threads.
*   **Jackson**: For parsing options/results.
*   **JLine**: For TUI (already present).