# Refactoring & Implementation Guide

This document serves as the architectural roadmap for evolving Lyocell from its current MVP state to a feature-rich k6 clone. It outlines necessary refactoring to support upcoming phases and provides a step-by-step guide for implementation.

## 1. Critical Refactoring (The Foundation)

Before adding heavy features, we must strengthen the foundation.

### A. The Metrics Engine (Pre-req for Phase 6)
**Current State:**
*   `MetricsCollector` uses custom `LongAdder` and `ConcurrentHashMap` logic.
*   Reporting is hardcoded to a console summary.

**The Refactor:**
*   **Goal:** Replace the custom backend with **Micrometer**.
*   **Why?** Phase 6 requires output to InfluxDB, Prometheus, etc. Micrometer handles this strictly better than we can.
*   **Action Plan:**
    1.  Introduce `micrometer-core`.
    2.  Refactor `MetricsCollector` to act as a facade/registry wrapper.
    3.  Map k6 metric types to Micrometer types:
        *   `Counter` -> `io.micrometer.core.instrument.Counter`
        *   `Trend` -> `io.micrometer.core.instrument.DistributionSummary` (with percentiles)
        *   `Rate` -> `io.micrometer.core.instrument.Gauge` or specialized helper.
    4.  Update `SummaryReporter` to read from the Micrometer `MeterRegistry`.

### B. The Module System (Pre-req for Phase 7)
**Current State:**
*   Modules (`HttpModule`, `CoreModule`) are manually instantiated and bound in `JsEngine`.
*   Imports are handled by `LyocellFileSystem`.

**The Refactor:**
*   **Goal:** Create a `LyocellModule` interface and a `ModuleRegistry`.
*   **Why?** Phase 7 adds many modules (`k6/crypto`, `k6/execution`, etc.). Hardcoding them in `JsEngine` is unmaintainable.
*   **Action Plan:**
    1.  Define interface `LyocellModule` with method `install(Context.Builder ctx)`.
    2.  Update `JsEngine` to accept a list of modules.
    3.  Implement a standard loading mechanism (ServiceLoader or static registry).

### C. The Execution Engine (Pre-req for Phase 8)
**Current State:**
*   `TestEngine` contains the loop logic: "Spawn N threads, run `default` function M times."
*   This is effectively a hardcoded `shared-iterations` or `constant-vus` executor.

**The Refactor:**
*   **Goal:** Extract the "workload model" into an `Executor` strategy.
*   **Why?** Phase 8 (`scenarios`) requires support for `ramping-vus`, `constant-arrival-rate`, etc.
*   **Action Plan:**
    1.  Create interface `WorkloadExecutor`.
    2.  Move the current loop logic into `SimpleExecutor`.
    3.  `TestEngine` becomes a coordinator that selects and runs the correct `Executor`.

---

## 2. Implementation Guide: Phase 6 (Observability)

**Objective:** Add Micrometer and support external outputs.

**Steps:**
1.  **Dependencies:** Add `micrometer-core` and `micrometer-registry-prometheus` (or influx) to `build.gradle`.
2.  **Refactor Metrics (as above):** Delete custom accumulation logic; delegate to Micrometer.
3.  **JdkHttpSender:**
    *   Implement a `HttpSender` compatible with Micrometer's `PushRegistry` that uses Java 25 `HttpClient`.
    *   *Constraint:* Do NOT pull in OkHttp or Apache Client. Keep the binary small.
4.  **Configuration:**
    *   Parse `options.lyocell.outputs` (e.g., `["influxdb=http://localhost:8086"]`).
    *   Initialize the correct `MeterRegistry` implementation based on config.

---

## 3. Implementation Guide: Phase 7 (Standard Library)

**Objective:** Add missing k6 modules.

**Steps:**
1.  **`k6/execution`:**
    *   Requires injecting *context* into the module.
    *   Update `JsEngine` to allow passing the `VuWorker` ID/Context to modules.
    *   Implement `ExecutionModule` that exposes `vu.idInTest`, `vu.iterationInInstance`.
2.  **`k6/data` (SharedArray):**
    *   This requires a "Global" context that is shared across VUs.
    *   Implement `SharedArray` as a Java object that wraps a read-only data structure.
    *   Ensure `HostAccess` allows safe concurrent access from JS.
3.  **`k6/crypto` & `k6/encoding`:**
    *   Pure function implementations using Java's `java.security.MessageDigest` and `java.util.Base64`.
    *   Straightforward mapping.

---

## 4. Implementation Guide: Phase 8 (Scenarios)

**Objective:** Support complex scenarios and the `scenarios` config object.

**Steps:**
1.  **Config Parsing:**
    *   Map the complex `options.scenarios` JSON object to a Java POJO/Record hierarchy.
2.  **Executor Implementations:**
    *   **`RampingVusExecutor`:** Needs a scheduler that adjusts the active `StructuredTaskScope` concurrency over time.
    *   **`ConstantArrivalRate`:** Needs a rate-limiter based loop (Token Bucket algorithm) rather than a simple `for` loop.
3.  **Orchestrator:**
    *   Update `TestEngine` to run multiple `WorkloadExecutor` instances in parallel (if scenarios are concurrent) or sequence.

---

## 5. Suggested Order of Operations

1.  **Refactor Metrics** (Enables Phase 6).
2.  **Implement Phase 6** (Observability).
3.  **Refactor Modules** (Clean up).
4.  **Implement Phase 7** (Std Lib - `execution` is high value).
5.  **Refactor Engine** (Prepare for scenarios).
6.  **Implement Phase 8** (Scenarios).
