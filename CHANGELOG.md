# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - 2025-12-23

### ⚠️ Breaking Changes
- **Complete Rewrite:** Lyocell is now a Java-based **k6 clone**. The previous HTTP client CLI functionality has been replaced by a JavaScript execution engine compatible with k6 scripts.

### Added
- **k6 Compatibility:** Runs standard k6 JavaScript scripts (`import http from 'k6/http'`).
- **Virtual Threads Engine:** Spawns one Virtual Thread per Virtual User (VU) for massive concurrency.
- **GraalJS Integration:** Uses GraalVM JavaScript engine with strict context isolation per VU.
- **Modules Implemented:**
    - `k6/http`: `get`, `post`, `Response` object (headers, timings, JSON).
    - `k6/metrics`: `Counter`, `Trend`.
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