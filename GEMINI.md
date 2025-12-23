# Project Context: Lyocell

## Project Overview

**Lyocell** is a high-performance, modern HTTP client CLI built with **Java 25**. It is designed for developers who need a powerful tool for making HTTP requests, load testing APIs, and executing batch requests with real-time feedback.

**Key Technologies:**
*   **Java 25:** Utilizes preview features, specifically **Virtual Threads** (Project Loom) for high-throughput concurrency and **Structured Concurrency**.
*   **GraalVM (Mandrel):** Compiles to a **Native Image** for instant startup (~10ms) and low memory footprint (~50MB), eliminating the JVM warmup penalty.
*   **JLine 3:** Powered the interactive **Terminal User Interface (TUI)** for real-time monitoring and color-coded status updates.
*   **Jackson:** Handles JSON and YAML parsing for request bodies and batch configurations.
*   **Testcontainers:** Ensures robust integration testing with real containerized HTTP services.

**Core Features:**
*   **Virtual Threads:** Uses a pool of virtual threads to handle high concurrency efficiently.
*   **Real-time Monitoring:** Live dashboard showing request status (Success, Failed, Pending) and statistics.
*   **Flexible Input:** Supports `httpie`-like syntax for CLI arguments and YAML files for batch processing.
*   **Load Testing:** Built-in support for sending multiple concurrent requests (`-n`, `-c`) to benchmark API performance.

## Building and Running

The project is managed with **Gradle**.

### Prerequisites
*   **Java 25** (e.g., Mandrel distribution for Native Image support).
*   **GraalVM/Mandrel** installed if building the native image.

### Key Commands

| Action | Command | Description |
| :--- | :--- | :--- |
| **Build Native Image** | `./gradlew nativeCompile` | Compiles the application to a standalone native binary in `build/native/nativeCompile/lyocell`. |
| **Run (JVM)** | `./gradlew run` | Runs the application on the JVM (useful for development cycles). |
| **Run (Native)** | `./build/native/nativeCompile/lyocell` | Executes the compiled native binary. |
| **Test** | `./gradlew test` | Runs the unit and integration tests. |
| **Coverage** | `./gradlew jacocoTestReport` | Generates a code coverage report (XML & HTML) in `build/reports/jacoco/test/`. |

## Development Conventions

*   **Java Version:** The project strictly uses **Java 25** with `--enable-preview` features. Ensure your IDE and JDK are configured accordingly.
*   **Code Style:** Follows standard Java conventions.
*   **Testing:**
    *   **JUnit 5** is used for the test framework.
    *   **Testcontainers** are used for integration tests to spin up real HTTP servers (e.g., `httpbun`).
    *   **Jacoco** enforces a minimum of **80% code coverage**.
*   **Architecture:**
    *   **Main:** `com.wilhg.lyocell.Main` is the entry point, handling argument parsing and mode selection.
    *   **CLI Logic:** `LyocellCli` manages the core event loop, task submission, and UI rendering.
    *   **Batch Processing:** `YamlBatchProcessor` handles parsing and execution of YAML-defined request batches.
    *   **Concurrency:** Utilizes `ConcurrentHttpClient` and `RequestTask` to manage async operations via Virtual Threads.

## Project Structure

*   `src/main/java`: Application source code.
*   `src/test/java`: Unit and integration tests.
*   `sample-requests.yaml`: Example configuration for batch requests.
*   `build.gradle`: Main build configuration script.
*   `settings.gradle`: Project settings and name.
