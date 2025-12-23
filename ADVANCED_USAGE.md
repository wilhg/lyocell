# Advanced Usage Guide

This guide covers advanced configuration, workload modeling, and observability features in Lyocell.

## 1. Advanced Workload Modeling (Scenarios)

Lyocell fully supports the `options.scenarios` configuration, allowing you to model complex traffic patterns.

### A. Ramping VUs (Stages)
Simulate a realistic load test with ramp-up, steady-state, and ramp-down periods.

```javascript
export const options = {
  scenarios: {
    contacts: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 }, // Ramp to 20 VUs
        { duration: '1m', target: 20 },  // Stay at 20 VUs
        { duration: '10s', target: 0 },  // Ramp down
      ],
      gracefulRampDown: '30s',
    },
  },
};
```

### B. Constant Arrival Rate (Open Model)
Maintain a fixed request rate (RPS) regardless of system response time.

```javascript
export const options = {
  scenarios: {
    constant_request_rate: {
      executor: 'constant-arrival-rate',
      rate: 1000, // 1000 iterations...
      timeUnit: '1s', // ...per second
      duration: '1m',
      preAllocatedVUs: 50,
      maxVUs: 100,
    },
  },
};
```

### C. Per-VU Iterations
Ensure each VU performs a specific number of iterations (useful for data seeding).

```javascript
export const options = {
  scenarios: {
    seeding: {
      executor: 'per-vu-iterations',
      vus: 10,
      iterations: 100, // Each VU does 100 iterations (Total: 1000)
    },
  },
};
```

## 2. Observability & Metrics

Lyocell integrates with **Micrometer** to support real-time monitoring.

### A. InfluxDB (Push)
Push metrics directly to InfluxDB 2.x.

**CLI:**
```bash
./lyocell script.js -o "influxdb=http://localhost:8086?org=myorg&bucket=mybucket&token=mytoken"
```

**Script Config:**
```javascript
export const options = {
  lyocell: {
    outputs: [
      { 
        type: 'influxdb', 
        url: 'http://localhost:8086',
        org: 'myorg',
        bucket: 'mybucket',
        token: 'mytoken'
      }
    ]
  }
};
```

### B. Prometheus (Pull & Push)

**Pull Mode (Scrape):**
Starts an HTTP server exposing `/metrics`.
```bash
./lyocell script.js -o prometheus=9090
```

**Push Mode (Pushgateway):**
Pushes metrics to a Pushgateway.
```bash
./lyocell script.js -o prometheus=http://pushgateway:9091
```

## 3. Advanced Standard Library

### A. Data Seeding with `SharedArray`
Efficiently load large datasets (e.g., CSV/JSON) once and share them across thousands of VUs.

```javascript
import { SharedArray } from 'lyocell/data';
import http from 'lyocell/http';

const data = new SharedArray('users', function () {
  return JSON.parse(open('./users.json'));
});

export default function () {
  const user = data[Math.floor(Math.random() * data.length)];
  http.post('https://api.example.com/login', JSON.stringify(user));
}
```

### B. Cryptography (`lyocell/crypto`)
Generate HMAC signatures for API authentication.

```javascript
import crypto from 'lyocell/crypto';

export default function () {
  const secret = 'my-secret-key';
  const message = 'timestamp=123456789';
  const signature = crypto.hmac('sha256', secret, message, 'hex');
  console.log(`Signature: ${signature}`);
}
```

### C. Execution Context (`lyocell/execution`)
Access unique identifiers for data partitioning.

```javascript
import execution from 'lyocell/execution';

export default function () {
  // Use VU ID to select unique data
  const id = execution.vu.idInTest; 
  console.log(`I am VU number ${id}`);
}
```

## 4. Building Native Image

For production, compile Lyocell into a standalone binary using GraalVM.

**Prerequisites:**
*   GraalVM for JDK 25
*   `native-image` tool installed (`gu install native-image`)

**Build Command:**
```bash
./gradlew nativeCompile
```

**Output:**
The binary will be at `build/native/nativeCompile/lyocell`.
