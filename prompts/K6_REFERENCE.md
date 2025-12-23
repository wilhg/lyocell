# Lyocell Reference Manual (k6 Clone)

This document serves as the user manual for Lyocell, a clean-room implementation of the k6 load testing tool on the Java platform. It adheres strictly to the [official k6 documentation](https://grafana.com/docs/lyocell/latest/using-lyocell/).

## 1. Test Lifecycle

A Lyocell test execution follows the standard four-stage k6 lifecycle:

### A. Init Stage
*   **Context**: Runs once per VU.
*   **Purpose**: Initialize global variables, import modules, and define functions.
*   **Constraints**: No network requests allowed (HTTP calls here will fail).
*   **Example**:
    ```javascript
    import http from 'lyocell/http';
    const BASE_URL = __ENV.BASE_URL || 'http://localhost:3000';
    ```

### B. Setup Stage (Optional)
*   **Function**: `export function setup() { ... }`
*   **Context**: Runs **once** for the entire test (by a special VU).
*   **Purpose**: Create test data, register users, or obtain tokens.
*   **Data Passing**: The return value (JSON-serializable) is passed to the VU and Teardown stages.
*   **Status**: ✅ Fully Implemented.

### C. VU Stage (The Test)
*   **Function**: `export default function(data) { ... }`
*   **Context**: Runs repeatedly in a loop for every Virtual User.
*   **Purpose**: The main load testing logic.
*   **Concurrency**: Each VU runs on its own **Java Virtual Thread**, allowing massive concurrency.
*   **Status**: ✅ Fully Implemented.

### D. Teardown Stage (Optional)
*   **Function**: `export function teardown(data) { ... }`
*   **Context**: Runs **once** after all VUs have finished.
*   **Purpose**: Cleanup data or environments.
*   **Status**: ✅ Fully Implemented.

## 2. Test Types (Methodology)

Lyocell supports standard performance testing methodologies defined by k6:

*   **Smoke Test**: Minimal load (1 VU) for short duration to verify script logic and system health.
*   **Load Test**: Simulates normal "day-in-the-life" traffic to verify SLOs.
*   **Stress Test**: Load beyond normal limits to find the breaking point or test stability.
*   **Soak Test**: Run for extended periods (hours) to detect memory leaks or resource exhaustion.
*   **Spike Test**: Sudden, massive surge in traffic to test autoscaling recovery.
*   **Breakpoint Test**: Ramp up load indefinitely until the system fails.

## 3. Configuration (`options`)

Lyocell supports configuration via the exported `options` object.

### Supported Options
| Option | Type | Description | Status |
| :--- | :--- | :--- | :--- |
| `vus` | `integer` | Number of concurrent Virtual Users. | ✅ |
| `duration` | `string` | Test duration (e.g., `'10s'`, `'1m'`). | ✅ |
| `iterations` | `integer` | Fixed number of total iterations. | ✅ |
| `thresholds` | `object` | Pass/fail criteria (e.g., `{'http_req_duration': ['p(95)<500']}`). | ✅ |
| `scenarios` | `object` | Advanced executors to model complex workloads. | ✅ |

### Supported Scenarios
*   `shared-iterations`: Fixed iterations shared across VUs. ✅
*   `per-vu-iterations`: Fixed iterations per VU. ✅
*   `constant-vus`: Fixed VUs for a duration. ✅
*   `ramping-vus`: Scale VUs up/down over time (stages). ✅
*   `constant-arrival-rate`: Open model (RPS targets). ✅
*   `ext`: Extension configuration.

## 4. JavaScript API Reference

### A. `lyocell/http` Module
**Imports**: `import http from 'lyocell/http';`

| Method | Signature | Status |
| :--- | :--- | :--- |
| `get` | `http.get(url, [params])` | ✅ |
| `post` | `http.post(url, body, [params])` | ✅ |
| `put` | `http.put(url, body, [params])` | ✅ |
| `patch` | `http.patch(url, body, [params])` | ✅ |
| `del` | `http.del(url, [body], [params])` | ✅ |
| `batch` | `http.batch(requests)` | ⏳ Planned |

**Request `params` Object**:
*   `headers`: `Map<String, String>` ✅
*   `tags`: `Map<String, String>` (Custom metric tags) ⏳ Planned
*   `timeout`: `string` ⏳ Planned
*   `cookies`: `object` ⏳ Planned
*   `auth`: `string` ⏳ Planned

**`Response` Object**:
*   `status` (number): HTTP status code. ✅
*   `url` (string): The URL extracted. ✅
*   `headers` (object): Response headers. ✅
*   `body` (string): Response body. ✅
*   `timings` (object): `{ duration: float, blocked: float, connecting: float, ... }`. ✅
*   `json([selector])` (function): Parse body as JSON. ✅
*   `clickLink()`: ⏳ Planned
*   `submitForm()`: ⏳ Planned

### B. `lyocell/metrics` Module
**Imports**: `import { Counter, Trend, Rate, Gauge } from 'lyocell/metrics';`

| Class | Description | Status |
| :--- | :--- | :--- |
| `Counter` | Cumulative sum (e.g., errors). | ✅ |
| `Trend` | Statistics: min, max, avg, p95 (e.g., latency). Supports `add(value, tags)`. | ✅ |
| `Rate` | Percentage of "true" values. | ✅ |
| `Gauge` | Stores the last value. | ✅ |

### C. `lyocell` Core Module
**Imports**: `import { check, group, sleep, fail, randomSeed } from 'lyocell';`

*   **`check(val, sets)`**: Assertions that don't stop the test.
    ```javascript
    check(res, { 'status is 200': (r) => r.status === 200 });
    ```
*   **`sleep(sec)`**: Suspends the VU for `sec` seconds (blocking the Virtual Thread, not the OS thread).
*   **`group(name, fn)`**: Groups metrics/checks under a label.
*   **`fail(err)`**: Aborts the current iteration and increments the `iterations_failed` metric. ✅
*   **`randomSeed(int)`**: Sets the seed for `Math.random` (for reproducible tests). ✅

### D. Other Standard Modules
These modules are standard in k6 and are fully supported by Lyocell.

#### `lyocell/execution` ✅
Exposes information about the current test execution state.
*   `execution.vu.idInTest`: Unique ID of the VU (1 to N).
*   `execution.vu.iterationInInstance`: Current iteration number.
*   `execution.test.abort()`: Stops the entire test.

#### `lyocell/encoding` ✅
*   `b64encode(input)`: Base64 encode.
*   `b64decode(input)`: Base64 decode.

#### `lyocell/crypto` ✅
*   `sha256(input)`: SHA-256 hashing.
*   `hmac(algo, secret, data)`: HMAC generation.

#### `lyocell/data` ✅
*   `SharedArray`: Memory-efficient way to share large data (e.g., JSON) between VUs.

### E. Environment Variables
*   **`__ENV`**: Global object containing environment variables.
    *   Usage: `__ENV.MY_VAR`
    *   System env vars are automatically injected.

## 4. Lyocell Extensions



Features specific to Lyocell, designed to be compatible with k6's philosophy.



### Observability

Lyocell supports real-time metrics export to external backends. You can configure outputs directly in the script `options` or via the CLI `-o` flag.



```javascript

export const options = {

  lyocell: {

    outputs: [

      { type: 'prometheus', port: 9090 }

    ]

  }

};

```
