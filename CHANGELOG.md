# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2024-12-21

### Added
- Initial release of Lyocell HTTP Client CLI
- Virtual threads support with 100-thread pool for high concurrency
- Real-time CLI monitoring with color-coded status indicators
- HTTPie-style request syntax (query params, headers, JSON body)
- Load testing mode with configurable concurrency (`-n`, `-c` flags)
- YAML batch request support - execute multiple different requests from a file
- Native image compilation with GraalVM for instant startup (~10-50ms)
- Low memory footprint (~50-100MB RSS)
- Comprehensive test suite with 80% coverage
- JLine 3.30.6 for terminal UI
- Jackson 3.0.3 for YAML processing

### Features
- **Single Request Mode**: Execute individual HTTP requests with full customization
- **Load Testing Mode**: Send multiple identical requests with controlled concurrency
- **Batch Mode**: Execute different requests from YAML file
- **Demo Mode**: Run without arguments to see interactive demo
- **Real-time Statistics**: Live tracking of success/failure rates
- **Request Duration Metrics**: Accurate per-request timing and averages
- **Auto-completion Wait**: Exits immediately when all tasks complete

### Technical Details
- Java 25 with preview features (virtual threads, structured concurrency)
- GraalVM native image with `-O3` optimization
- Testcontainers for integration testing
- JaCoCo for code coverage reporting

[0.1.0]: https://github.com/wilhg/lyocell/releases/tag/v0.1.0
