# Lyocell Overview & Options

Lyocell runs JavaScript load tests from a single binary. Write tests, run them with `lyocell`, and export shareable HTML reports.

## CLI
```bash
lyocell test.js -o html=report.html
```

## Options (JS `export const options`)
| Option | Type | Description |
| --- | --- | --- |
| `vus` | integer | Concurrent virtual users. |
| `duration` | string | Test duration (`"30s"`, `"5m"`). |
| `iterations` | integer | Total iterations (shared). |
| `thresholds` | object | Pass/fail rules (e.g., `{'http_req_duration': ['p(95)<500']}`). |
| `scenarios` | object | Advanced workload configs (see Scenarios doc). |
| `lyocell.outputs` | array | Outputs (e.g., `[{type: 'html', target: 'report.html'}]`). |

## Minimal Test
```javascript
import http from 'lyocell/http';
import { check, sleep } from 'lyocell';

export const options = {
  vus: 10,
  duration: '30s',
  thresholds: { http_req_duration: ['p(95)<500'], checks: ['rate>0.99'] },
  lyocell: { outputs: [{ type: 'html', target: 'report.html' }] },
};

export default function () {
  const res = http.get('https://httpbun.com/get');
  check(res, { 'status is 200': (r) => r.status === 200 });
  sleep(1);
}
```

