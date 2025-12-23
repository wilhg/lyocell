# Refactoring & Implementation Guide

This document serves as the architectural roadmap for evolving Lyocell from its current MVP state to a feature-rich k6 clone. It outlines necessary refactoring to support upcoming phases and provides a step-by-step guide for implementation.

## 1. Critical Refactoring (The Foundation) ✅ COMPLETED

The foundation is now solid, supporting multi-executor scenarios and pluggable modules.

### A. The Metrics Engine ✅
*   **Status:** Replaced custom backend with **Micrometer**.
*   **Result:** Support for InfluxDB and Prometheus is built-in.

### B. The Module System ✅
*   **Status:** Implemented `LyocellModule` interface and `ModuleRegistry`.
*   **Result:** Clean separation of concerns for k6 standard library parity.

### C. The Execution Engine ✅
*   **Status:** Extracted workload models into `WorkloadExecutor` strategies.
*   **Result:** Supports complex scenarios like `ramping-vus` and `constant-arrival-rate`.

---

## 2. Implementation Guide: Phase 6 (Observability) ✅ COMPLETED

**Objective:** Add Micrometer and support external outputs.

**Steps:**
1.  **Dependencies:** Added `micrometer-core` and `micrometer-registry-prometheus` (or influx) to `build.gradle`.
2.  **Refactor Metrics (as above):** Deleted custom accumulation logic; delegated to Micrometer.
3.  **JdkHttpSender:**
    *   Implemented a `HttpSender` compatible with Micrometer's `PushRegistry` that uses Java 25 `HttpClient`.
4.  **Configuration:**
    *   Parse `options.lyocell.outputs` (e.g., `["influxdb=http://localhost:8086"]`).
    *   Initialize the correct `MeterRegistry` implementation based on config.

---

## 3. Implementation Guide: Phase 7 (Standard Library) ✅ COMPLETED

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

## 4. Implementation Guide: Phase 8 (Scenarios) ✅ COMPLETED

**Objective:** Support complex scenarios and the `scenarios` config object.

**Steps:**
1.  **Config Parsing:**
    *   Map the complex `options.scenarios` JSON object to a Java POJO/Record hierarchy.
2.  **Executor Implementations:**
    *   **`RampingVusExecutor`**: A scheduler that adjusts the active `StructuredTaskScope` concurrency over time.
    *   **`ConstantArrivalRate`**: A rate-limiter based loop (Token Bucket algorithm).
3.  **Orchestrator:**
    *   Update `TestEngine` to run multiple `WorkloadExecutor` instances in parallel.

---

## 5. Suggested Order of Operations

1.  **Refactor Metrics** (Enables Phase 6).
2.  **Implement Phase 6** (Observability).
3.  **Refactor Modules** (Clean up).
4.  **Implement Phase 7** (Std Lib - `execution` is high value).
5.  **Refactor Engine** (Prepare for scenarios).
6.  **Implement Phase 8** (Scenarios).
