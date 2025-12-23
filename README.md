# Lyocell

A modern HTTP client CLI built with Java 25, featuring virtual threads and real-time monitoring.

## Features

- 🧵 Virtual Threads - 100-thread pool for high concurrency
- 🔄 Structured Concurrency - Proper task lifecycle management
- 🎨 Real-time CLI - Live monitoring with color-coded status
- ⚡ Native Image - ~10ms startup, ~50MB memory
- 📊 Live Statistics - Real-time success/failure tracking

## Building the CLI

### Prerequisites

1. **Install Mandrel (GraalVM distribution):**

You can use SDKMAN to install Java 25 with Native Image support:
```bash
sdk install java 25.0.1.r25-mandrel
sdk use java 25.0.1.r25-mandrel
```

Or download Mandrel directly from [GitHub releases](https://github.com/graalvm/mandrel/releases)

2. **Verify installation:**
```bash
java -version        # Should show: java version "25"
native-image --version  # Should show Mandrel version
```

### Build Steps

```bash
# Clone the repository
git clone <repository-url>
cd lyocell

# Build the native binary
./gradlew nativeCompile
```

The CLI binary will be created at: **`build/native/nativeCompile/lyocell`**

Build time: ~2-5 minutes (first build)

## Using the CLI

### Demo Mode

Run without arguments to see the demo:

```bash
./build/native/nativeCompile/lyocell
```

This will:
1. Submit sample HTTP requests to httpbin.org
2. Display real-time monitoring dashboard
3. Show live statistics and task status updates

### HTTP Requests (httpie-style)

Make HTTP requests with simple, intuitive syntax:

```bash
# Simple GET request
lyocell GET https://httpbin.org/get

# GET with query parameters
lyocell https://httpbin.org/get name==John age==30

# POST with JSON data
lyocell POST https://httpbin.org/post name=John age:=30 active:=true

# With custom headers
lyocell https://httpbin.org/get User-Agent:Lyocell Authorization:"Bearer token"

# PUT with timeout
lyocell PUT https://httpbin.org/put id:=123 --timeout=3000
```

### Request Item Syntax

| Syntax | Description | Example |
|--------|-------------|---------|
| `key=value` | JSON string field | `name=John` → `{"name":"John"}` |
| `key:=value` | JSON raw/number | `age:=30` → `{"age":30}` |
| `key==value` | URL query parameter | `q==search` → `?q=search` |
| `Header:value` | Request header | `User-Agent:CLI` |

### Options

| Option | Description | Default |
|--------|-------------|---------|
| `-to, --timeout=ms` | Request timeout in milliseconds | `5000` |
| `-n, --requests=N` | Number of requests to send | `1` |
| `-c, --concurrency=N` | Number of concurrent requests | `1` |

### Batch Requests from YAML

Execute multiple different requests from a YAML file:

```bash
# Run batch requests
lyocell requests.yaml
lyocell ./config/api-tests.yml
```

**YAML Format:**

```yaml
# Optional defaults applied to all requests
defaults:
  timeout: 8000
  headers:
    Authorization: Bearer token123
    Content-Type: application/json

# List of requests to execute
requests:
  - name: Get Users
    method: GET
    url: https://api.example.com/users
    queryParams:
      page: "1"
      size: "50"

  - name: Create User
    method: POST
    url: https://api.example.com/users
    headers:
      X-Custom-Header: custom-value
    body:
      name: Alice
      email: alice@example.com
      age: 25

  - name: Update User
    method: PUT
    url: https://api.example.com/users/123
    timeout: 15000
    body:
      name: Bob Updated
```

**YAML Schema:**

| Field | Required | Description |
|-------|----------|-------------|
| `defaults.timeout` | No | Default timeout for all requests (ms) |
| `defaults.headers` | No | Default headers for all requests |
| `requests[].name` | No | Descriptive name for the request |
| `requests[].method` | No | HTTP method (default: GET) |
| `requests[].url` | **Yes** | Request URL |
| `requests[].headers` | No | Request headers (merged with defaults) |
| `requests[].queryParams` | No | URL query parameters |
| `requests[].body` | No | Request body (object or string) |
| `requests[].timeout` | No | Request timeout (overrides default) |

Batch mode will:
- Execute all requests sequentially
- Display real-time progress in the CLI
- Show summary statistics at the end

### Load Testing

Send multiple requests with controlled concurrency:

```bash
# Send 100 requests with 10 concurrent
lyocell https://httpbin.org/get -n=100 -c=10

# POST load test with JSON body
lyocell POST https://httpbin.org/post name=test id:=1 -n=50 -c=5

# Load test with custom headers
lyocell https://api.example.com/endpoint Authorization:"Bearer token" -n=200 -c=20
```

Results will show:
- Total time
- Successful/failed requests
- Requests per second
- Average time per request

### CLI Interface

```
╔═══════════════════════════════════════════════════════════╗
║           LYOCELL - HTTP Client Monitor                   ║
╚═══════════════════════════════════════════════════════════╝

📊 Statistics:
   Total: 10 | ✓ Success: 8 | ✗ Failed: 1 | ⏳ In Progress: 1

📝 Recent Tasks:
   ✓ Task #1  - SUCCESS    - GET https://httpbin.org/delay/0 - 245ms
   ✓ Task #2  - SUCCESS    - GET https://httpbin.org/delay/1 - 1234ms
   ⏳ Task #3  - PENDING    - GET https://httpbin.org/delay/2 - 456ms (ongoing)

Press Ctrl+C to exit
```

### Status Indicators

- ✓ **SUCCESS** (Green) - Request completed successfully (HTTP 2xx)
- ✗ **FAILED** (Red) - Request failed (network error, timeout, or HTTP 4xx/5xx)
- ⏳ **PENDING** (Yellow) - Request currently executing
- ⏸ **AWAITING** (Blue) - Request queued, waiting for worker thread

### Controls

- **Ctrl+C** - Gracefully shutdown the CLI


## Development

### Run Tests

```bash
./gradlew test
```

### Generate Coverage Report

```bash
./gradlew test jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

### Run with JVM (for development)

```bash
./gradlew run
```


## Performance

Native image with optimizations provides:
- ⚡ **Instant startup**: ~10-50ms (vs ~500-1000ms JVM)
- 💾 **Low memory**: ~50-100MB RSS (vs ~150-250MB JVM)
- 📦 **Single binary**: No JVM installation required
- 🚀 **CPU optimized**: Built with `-O3` and `-march=native`
- 🔋 **Memory efficient**: 128MB max heap, parallel GC

Compare performance:
```bash
./compare-performance.sh
```

## Architecture

### Concurrency Model
```
Submit Task → Queue → Worker Pool (100 Virtual Threads) → Listeners
```

### Task Lifecycle
```
AWAITING → PENDING → SUCCEED/FAILED
```


## Technical Details

### Built With
- **Java 25** - Virtual threads, structured concurrency, ScopedValue
- **JLine 3.25.1** - Terminal UI
- **Mandrel 25** - Native Image AOT compilation (Red Hat's GraalVM distribution)
- **Testcontainers** - Integration testing

### Test Coverage
- Total: 80%
- Tests: 12 integration tests
- All tests use Docker-based httpbun for real HTTP testing

### Architecture
- 100 virtual thread pool (configurable)
- Queue-based task distribution
- Event-driven status updates
- CLI refreshes every 500ms
