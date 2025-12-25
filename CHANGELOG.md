# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.3] - 2025-12-25

### Fixed
- Stabilized flaky tests around time-series aggregation.

## [0.3.2] - 2025-12-24

### Fixed
- Improved Windows compatibility in native packaging and workflow steps.

## [0.3.1] - 2025-12-24

### Changed
- Enabled UPX compression for Linux/Windows release artifacts to reduce download size.

## [0.3.0] - 2025-12-23

### Added
- **Observability (Phase 6):**
    - **Micrometer Integration:** Decoupled metrics recording from reporting using a `CompositeMeterRegistry`.
    - **HTML Reports:** Zero-dependency, static HTML5 report generation with CSS charts.
    - **Configuration:** New `-o` / `--out` CLI flag and `options.lyocell.outputs` support in scripts.
- **Graceful Shutdown:** Ensures all metrics are flushed before the process exits.

## [0.2.0] - 2025-12-23

### ⚠️ Breaking Changes
- **Complete Rewrite:** Lyocell is now a Java-based **k6 clone**. The previous HTTP client CLI functionality has been replaced by a JavaScript execution engine compatible with k6 scripts.

### Added
- **k6 Compatibility:** Runs standard k6 JavaScript scripts (`import http from 'lyocell/http'`).
- **Virtual Threads Engine:** Spawns one Virtual Thread per Virtual User (VU) for massive concurrency.
- **GraalJS Integration:** Uses GraalVM JavaScript engine with strict context isolation per VU.
- **Modules Implemented:**
    - `lyocell/http`: `get`, `post`, `Response` object (headers, timings, JSON).
    - `lyocell/metrics`: `Counter`, `Trend`.
    - `k6`: `check`, `group`, `sleep`.
- **Environment Support:** Added `__ENV` global object for environment variable access.
- **Native Image:** Builds to a standalone binary (macOS/Linux) with instant startup.
- **Metrics Engine:** Lock-free `MetricsCollector` with ASCII summary report.
- **Examples:** New `examples/` directory with tested scripts running against `sharat87/httpbun`.

### Fixed
- Native Image build issues on macOS (removed incompatible G1 GC flag).
- Reflection configuration for GraalVM Native Image to ensure JS-Java interoperability.

## [0.1.0] - 2024-12-21
*(Legacy Java HTTP Client Release - Deprecated)*