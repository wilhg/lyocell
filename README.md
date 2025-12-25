# Lyocell

**Lyocell** is a fast, k6-compatible load-testing tool that ships as a single binary. Write familiar k6-style JavaScript, run it locally, and get shareable reports—no servers or heavy setup required.

## 🚀 Why Lyocell?

*   **Virtual Threads:** Uses Java 25's Project Loom to run every Virtual User (VU) on its own lightweight thread.
*   **k6 Compatible:** Runs standard k6 scripts (`import http from 'lyocell/http'`).
*   **Native Performance:** Compiles to a native executable (no JVM startup lag).
*   **Ecosystem:** Leverages the robust Java ecosystem for networking and metrics (Micrometer).

## 📋 Prerequisites

None. The install methods below include a ready-to-run binary.

## 📦 Installation

**macOS (Homebrew)**
```bash
brew tap wilhg/lyocell
brew install lyocell
```

**Linux (Homebrew)**
```bash
brew tap wilhg/lyocell
brew install lyocell
```

**Linux (Direct download)**
```bash
wget https://github.com/wilhg/lyocell/releases/latest/download/lyocell-linux-amd64
chmod +x lyocell-linux-amd64
sudo mv lyocell-linux-amd64 /usr/local/bin/lyocell   # optional, put on PATH
```

**Windows (Scoop)**
```powershell
scoop bucket add lyocell https://github.com/wilhg/lyocell-scoop
scoop install lyocell
```

## ⚡ Quick Start

### 1) Write a test (k6-style JS)
Save this as `test.js`:
```javascript
import http from 'lyocell/http';
import { check, sleep } from 'lyocell';

export const options = {
  vus: 20,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<500'],
    checks: ['rate>0.99'],
  },
  lyocell: {
    outputs: [{ type: 'html', target: 'report.html' }], // export HTML report
  },
};

export default function () {
  const res = http.get('https://httpbun.com/get');
  check(res, { 'status is 200': (r) => r.status === 200 });
  sleep(1);
}
```

### 2) Run it
```bash
lyocell test.js -o html=report.html
```

### 3) View results
- Live summary prints to the console.
- An HTML report is written to `report.html` (open in a browser, no server needed). Great for sharing complex load-test results.

## 📚 Documentation

* **User Guide & API Reference**: `prompts/K6_REFERENCE.md`
* **Advanced Usage** (scenarios, reporting, data seeding): `ADVANCED_USAGE.md`

## 🤝 Contributing

Contributions are welcome! See `AGENTS.md` for the workflow we follow.

## 📄 License

MIT