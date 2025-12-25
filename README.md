# Lyocell

**Lyocell** is a fast, single-binary load-testing tool. Write familiar JavaScript, run it locally, and get shareable reports—no servers or heavy setup required.

## 🚀 Why Lyocell?

*   **Single binary:** Install and run with no extra runtimes.
*   **Fast start:** Near-instant startup for local runs and CI.
*   **Familiar JS:** Write tests in straightforward JavaScript.
*   **HTML reports:** Generate shareable, offline summaries.

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

### 1) Write a test (familiar JS)
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