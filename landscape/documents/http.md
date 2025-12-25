# HTTP Module (`lyocell/http`)

Import:
```javascript
import http from 'lyocell/http';
```

## Methods
| Method | Signature | Notes |
| --- | --- | --- |
| `get` | `http.get(url, params?)` | Simple GET. |
| `post` | `http.post(url, body, params?)` | Body is string/JSON. |
| `put` | `http.put(url, body, params?)` |  |
| `patch` | `http.patch(url, body, params?)` |  |
| `del` | `http.del(url, body?, params?)` |  |

## Params object
| Field | Type | Status |
| --- | --- | --- |
| `headers` | object | ✅ |
| `timeout` | string | ✅ (`"500ms"`, `"2s"`, `"1m"`) |
| `tags` | object | Planned |
| `cookies` | object | Planned |
| `auth` | string | Planned |

## Response
| Field | Type | Notes |
| --- | --- | --- |
| `status` | number | HTTP status (0 on error). |
| `body` | string | Response body. |
| `headers` | object | Lower-cased keys. |
| `timings` | object | `{ duration, blocked, connecting, tls_handshaking, sending, waiting, receiving }`. |
| `json()` | function | Parse body as JSON. |

## Example
```javascript
const res = http.post('https://httpbun.com/post', JSON.stringify({ hello: 'world' }), {
  headers: { 'Content-Type': 'application/json' },
  timeout: '2s',
});

console.log(res.status, res.timings.duration);
```

