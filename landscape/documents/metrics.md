# Metrics Module (`lyocell/metrics`)

Import:
```javascript
import { Counter, Trend, Rate, Gauge } from 'lyocell/metrics';
```

## Classes
| Class | Use |
| --- | --- |
| `Counter` | Cumulative count (errors, logins). |
| `Trend` | Record distributions (latency, payload sizes). |
| `Rate` | Track true/false ratio. |
| `Gauge` | Store last value (queue depth, temperature). |

## Usage
```javascript
const errors = new Counter('errors');
const latency = new Trend('custom_latency');
const successRate = new Rate('success_rate');
const queueDepth = new Gauge('queue_depth');

export default function () {
  const start = Date.now();
  const ok = Math.random() > 0.1;
  
  // You can add optional tags to any measurement
  latency.add(Date.now() - start, { service: 'api', method: 'GET' });
  
  successRate.add(ok);
  queueDepth.add(Math.random() * 10);
  if (!ok) errors.add(1, { type: 'network' });
}
```

## Thresholds (JS options)
```javascript
export const options = {
  thresholds: {
    http_req_duration: ['p(95)<500'],
    checks: ['rate>0.99'],
    'success_rate.true': ['rate>0.97'], // works because Rate uses <name>.true / <name>.total
  },
};
```

