# Lyocell

**Lyocell** is a high-performance, open-source load testing tool rewritten as a **Java-based clone of k6**. It allows you to run standard k6 JavaScript scripts using the power of **Java 25 Virtual Threads** for massive concurrency and **GraalVM (GraalJS)** for high-performance script execution.

Unlike the original Go-based k6, Lyocell leverages Project Loom (Virtual Threads) to provide a thread-per-virtual-user model that is both scalable and easy to reason about.

## 🚀 Features

*   **k6 Compatibility:** Runs standard k6 scripts (`import http from 'k6/http'`).
*   **Virtual Threads:** Uses Java 25 Virtual Threads to spawn thousands of concurrent users with minimal overhead.
*   **GraalVM Native Image:** Compiles to a standalone native binary for instant startup (~10ms).
*   **Metrics Engine:** High-performance, lock-free metrics aggregation (p95, p99, throughput).
*   **Dynamic Config:** Support for environment variables (`__ENV`).
*   **Thresholds:** Define pass/fail criteria directly in your script (`rate<0.01`).

## 📋 Prerequisites

To build and run from source, you need:
*   **Java 25** (with `--enable-preview`).
*   **GraalVM for JDK 25** (optional, for building the native binary).

## 🛠️ Building & Installation

### 1. Clone the Repository
```bash
git clone https://github.com/wilhg/lyocell.git
cd lyocell
```

### 2. Build the Native Binary (Recommended)
This produces a standalone executable that requires no JVM to run.
```bash
./gradlew nativeCompile
```
The binary will be generated at `build/native/nativeCompile/lyocell`.

### 3. Run with Gradle (Development)
If you don't want to compile a native image, you can run directly with Gradle:
```bash
./gradlew run --args="script.js -u 10 -i 100"
```

## 📖 Usage

Run a load test by supplying a JavaScript file and configuration flags.

```bash
./lyocell <script.js> [flags]
```

### Flags

| Flag | Description | Default |
| :--- | :--- | :--- |
| `-u`, `--vus <n>` | Number of Virtual Users (concurrency). | 1 |
| `-i`, `--iterations <n>` | Total iterations per VU. | 1 |

### Example Script (`test.js`)

Lyocell supports standard k6 syntax:

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

export const options = {
    thresholds: {
        'checks': ['rate>0.95'], // Fail if success rate is below 95%
    },
};

const myCounter = new Counter('my_custom_counter');

export default function() {
    const res = http.get(__ENV.BASE_URL || 'https://httpbin.org/get');
    
    check(res, {
        'status is 200': (r) => r.status === 200,
    });

    myCounter.add(1);
    sleep(1);
}
```

### Running the Example

```bash
# Run with 50 concurrent users, 10 iterations each
./build/native/nativeCompile/lyocell test.js -u 50 -i 10
```

**Output:**
```text
========================================
          LYOCELL TEST SUMMARY
========================================

[Execution]
  iterations................: 500

[Checks]
  checks....................: 100.00% (500 pass, 0 fail)

[Trends]
  http_req_duration:
    avg=124.50   min=89.00    p(95)=180.20   p(99)=210.50
========================================
```

## 🧩 Supported API

Lyocell implements a core subset of the k6 API:

### Modules
*   **`k6/http`**:
    *   `get(url, [params])`
    *   `post(url, body, [params])`
    *   `Response` object: `status`, `body`, `headers`, `timings`, `json()`
    *   *(Planned: `put`, `del`, `batch`)*
*   **`k6/metrics`**:
    *   `Counter`: `add(value)`
    *   `Trend`: `add(value)`
    *   *(Planned: `Gauge`, `Rate`)*
*   **`k6`**:
    *   `check(val, sets)`
    *   `group(name, fn)`
    *   `sleep(seconds)`
*   **Global**:
    *   `__ENV`: Environment variables.

### Lifecycle
*   `init` context (global scope)
*   `setup()`
*   `default()` (VU execution)
*   `teardown()`

## 🧪 Running Examples

We provide a set of example scripts in the `examples/` directory to help you get started. These scripts are designed to run against a local instance of `httpbun`.

### 1. Start the Test Server
Use Docker Compose to start a local `httpbun` service:

```bash
cd examples
docker-compose up -d
```
This will start a service at `http://localhost:80`.

### 2. Run Example Scripts

**Basic GET Request:**
```bash
./lyocell examples/basic-get.js -u 5 -i 10
```

**POST with JSON:**
```bash
./lyocell examples/post-json.js -u 1 -i 1
```

**Custom Metrics:**
```bash
./lyocell examples/custom-metrics.js -u 10 -i 50
```

### 3. Cleanup
When you are done, stop the test server:
```bash
cd examples
docker-compose down
```

## 🏗️ Architecture

*   **Engine:** `TestEngine` manages a `StructuredTaskScope` (Java 25) to supervise thousands of `VuWorker` threads.
*   **Isolation:** Each VU runs in its own isolated GraalJS `Context`, ensuring thread safety without shared mutable state.
*   **Bridge:** A custom `LyocellFileSystem` intercepts imports to inject high-performance Java implementations of k6 modules.

## 🤝 Contributing

We welcome contributions! Please ensure all PRs follow **Test-Driven Development (TDD)** and include integration tests.

1.  Fork the repo.
2.  Create a feature branch.
3.  Add tests (`src/test/java`).
4.  Implement feature.
5.  Submit PR.

## 📄 License

MIT
