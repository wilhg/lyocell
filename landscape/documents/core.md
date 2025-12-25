# Core Module (`lyocell`)

Import:
```javascript
import { check, group, sleep, fail, randomSeed } from 'lyocell';
```

## Functions
| Fn | Purpose |
| --- | --- |
| `check(val, sets)` | Soft assertions; increments checks pass/fail. |
| `sleep(seconds)` | Pause the VU (virtual thread). |
| `group(name, fn)` | Group metrics/logging under a label. |
| `fail(message)` | Abort current iteration; marks as failed. |
| `randomSeed(int)` | Set seed for `Math.random` (deterministic tests). |

### check
```javascript
check(res, {
  'status is 200': (r) => r.status === 200,
  'body is json': (r) => !!r.headers['content-type']?.includes('json'),
});
```

### group
```javascript
group('auth', () => {
  const res = http.post(BASE + '/login', creds);
  check(res, { 'login ok': (r) => r.status === 200 });
});
```

### fail
```javascript
if (!token) fail('missing token');
```

### randomSeed
```javascript
randomSeed(1234);
```

