# Lyocell

**Lyocell** is a high-performance, open-source load testing tool rewritten as a **Java-based clone of k6**. It combines the developer experience of k6's JavaScript API with the massive concurrency of **Java 25 Virtual Threads**.

Running on **GraalVM**, Lyocell compiles to a standalone native binary that starts instantly and simulates thousands of concurrent users with minimal overhead.

## 🚀 Why Lyocell?

*   **Virtual Threads:** Uses Java 25's Project Loom to run every Virtual User (VU) on its own lightweight thread.
*   **k6 Compatible:** Runs standard k6 scripts (`import http from 'lyocell/http'`).
*   **Native Performance:** Compiles to a native executable (no JVM startup lag).
*   **Ecosystem:** Leverages the robust Java ecosystem for networking and metrics (Micrometer).

## 📋 Prerequisites

*   **Java 25** (with `--enable-preview`) - Required to build/run from source.
*   **GraalVM** - Optional, for building the native binary yourself.

## 📦 Installation

<details>
<summary><strong>macOS</strong> (Homebrew)</summary>

```bash
brew tap wilhg/lyocell
brew install lyocell
```
</details>

<details>
<summary><strong>Linux</strong> (Homebrew or Manual)</summary>

**Homebrew:**
```bash
brew tap wilhg/lyocell
brew install lyocell
```

**Manual:**
Download the standalone binary from the [Releases](https://github.com/wilhg/lyocell/releases) page:

```bash
# Download the latest version
wget https://github.com/wilhg/lyocell/releases/latest/download/lyocell-linux-amd64

# Make it executable
chmod +x lyocell-linux-amd64

# Move to a directory in your PATH (optional)
sudo mv lyocell-linux-amd64 /usr/local/bin/lyocell
```
</details>

<details>
<summary><strong>Windows</strong> (Scoop)</summary>

```powershell
scoop bucket add lyocell https://github.com/wilhg/lyocell-scoop
scoop install lyocell
```
</details>

## ⚡ Quick Start

### 1. Build from Source (Optional)

If you prefer to build it yourself:

```bash
git clone https://github.com/wilhg/lyocell.git
cd lyocell
./gradlew build
```

### 2. Write a Test Script

Create a file named `test.js`:

```javascript
import http from 'lyocell/http';
import { check, sleep } from 'lyocell';

export const options = {
    thresholds: {
        'http_req_duration': ['p(95)<500'], // 95% of requests must complete in < 500ms
    },
};

export default function() {
    const res = http.get('https://httpbun.com/get');
    check(res, { 'status is 200': (r) => r.status === 200 });
    sleep(1);
}
```

### 3. Run the Test

Run directly with Gradle (developer mode):

```bash
./gradlew run --args="test.js -u 10 -i 50"
```
*   `-u 10`: Simulate 10 concurrent Virtual Users.
*   `-i 50`: Run 50 total iterations.

## 📚 Documentation

*   **[User Guide & API Reference](prompts/K6_REFERENCE.md)**: Detailed API docs for `lyocell/http`, `lyocell/metrics`, and standard modules.
*   **[Advanced Usage](ADVANCED_USAGE.md)**: Complex scenarios (Ramping VUs), Observability (Prometheus), and Data Seeding.
*   **[Architecture](prompts/TECHNICAL_DESIGN.md)**: Deep dive into the internal design (Virtual Threads, GraalJS Contexts).

## 🛠️ Building Native Image

To create a standalone binary (Linux/macOS):

```bash
./gradlew nativeCompile
```
The binary will be generated at `build/native/nativeCompile/lyocell`.

## 🤝 Contributing

Contributions are welcome! Please follow our [Test-Driven Development](GEMINI.md) process and ensure all new features are covered by integration tests.

## 📄 License

MIT